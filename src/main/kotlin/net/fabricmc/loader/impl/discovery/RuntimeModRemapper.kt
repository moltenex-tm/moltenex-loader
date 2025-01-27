/*                               Copyright 2025 Moltenex
 *
 * Licensed under the MOSL (Moltenex Open Source License), hereinafter referred to as
 * the "License." You may not use this file except in compliance with the License.
 *
 * The License can be obtained at:
 *      -http://www.moltenex.com/licenses/MOSL
 *      -LICENSE.md file found in the root
 *
 * Unless required by applicable law or agreed to in writing, the Software distributed
 * under the License is provided on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS
 * OF ANY KIND, either express or implied.
 *
 * For the specific language governing permissions and limitations under the License,
 * please refer to the full License document.
 *
*/
package net.fabricmc.loader.impl.discovery

import com.moltenex.loader.impl.MoltenexLoaderImpl
import com.moltenex.loader.impl.launch.MoltenexLauncherBase.Companion.launcher
import net.fabricmc.accesswidener.*
import net.fabricmc.loader.impl.FormattedException
import net.fabricmc.loader.impl.util.FileSystemUtil.getJarFileSystem
import net.fabricmc.loader.impl.util.ManifestUtil.readManifest
import net.fabricmc.loader.impl.util.SystemProperties
import net.fabricmc.loader.impl.util.log.Log.warn
import net.fabricmc.loader.impl.util.log.LogCategory
import net.fabricmc.tinyremapper.*
import net.fabricmc.tinyremapper.extension.mixin.MixinExtension
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.commons.Remapper
import java.io.File
import java.io.IOException
import java.net.URISyntaxException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*
import java.util.stream.Collectors

object RuntimeModRemapper {
    private const val REMAP_TYPE_MANIFEST_KEY = "Fabric-Loom-Mixin-Remap-Type"
    private const val REMAP_TYPE_STATIC = "static"
    private const val SOURCE_NAMESPACE = "intermediary"

    fun remap(modCandidates: Collection<ModCandidateImpl>, tmpDir: Path?, outputDir: Path) {
        val modsToRemap: MutableList<ModCandidateImpl> = ArrayList()
        val remapMixins: MutableSet<InputTag> = HashSet()

        for (mod in modCandidates) {
            if (mod.requiresRemap) {
                modsToRemap.add(mod)
            }
        }

        if (modsToRemap.isEmpty()) return

        val infoMap: MutableMap<ModCandidateImpl, RemapInfo> = HashMap()

        var remapper: TinyRemapper? = null

        try {
            val launcher = launcher

            val mergedAccessWidener = AccessWidener()
            mergedAccessWidener.visitHeader(SOURCE_NAMESPACE)

            for (mod in modsToRemap) {
                val info = RemapInfo()
                infoMap[mod] = info

                if (mod.hasPath()) {
                    val paths = mod.paths
                    if (paths != null) {
                        if (paths.size != 1) throw UnsupportedOperationException("multiple path for $mod")
                    }

                    info.inputPath = paths?.get(0)
                } else {
                    info.inputPath = mod.copyToDir(tmpDir!!, true)
                    info.inputIsTemp = true
                }

                info.outputPath = outputDir.resolve(mod.defaultFileName)
                Files.deleteIfExists(info.outputPath!!)

                val accessWidener = mod.metadata.accessWidener

                if (accessWidener != null) {
                    info.accessWidenerPath = accessWidener

                    try {
                        getJarFileSystem(info.inputPath!!, false).use { jarFs ->
                            val fs = jarFs.get()
                            info.accessWidener = Files.readAllBytes(fs!!.getPath(accessWidener))
                        }
                    } catch (t: Throwable) {
                        throw RuntimeException("Error reading access widener for mod '" + mod.id + "'!", t)
                    }

                    AccessWidenerReader(mergedAccessWidener).read(info.accessWidener)
                }
            }

            remapper = TinyRemapper.newRemapper()
                .withMappings(
                    TinyUtils.createMappingProvider(
                        launcher!!.mappingConfiguration!!.getMappings(),
                        SOURCE_NAMESPACE,
                        launcher.targetNamespace
                    )
                )
                .renameInvalidLocals(false)
                .extension(MixinExtension { o: InputTag -> remapMixins.contains(o) })
                .extraAnalyzeVisitor { mrjVersion: Int, className: String?, next: ClassVisitor? ->
                    AccessWidenerClassVisitor.createClassVisitor(
                        MoltenexLoaderImpl.ASM_VERSION,
                        next,
                        mergedAccessWidener
                    )
                }
                .build()

            try {
                remapper.readClassPathAsync(*remapClasspath.toTypedArray<Path>())
            } catch (e: IOException) {
                throw RuntimeException("Failed to populate remap classpath", e)
            }

            for (mod in modsToRemap) {
                val info = infoMap[mod]

                val tag = remapper.createInputTag()
                info!!.tag = tag

                if (requiresMixinRemap(info.inputPath!!)) {
                    remapMixins.add(tag)
                }

                remapper.readInputsAsync(tag, info.inputPath)
            }

            //Done in a 2nd loop as we need to make sure all the inputs are present before remapping
            for (mod in modsToRemap) {
                val info = infoMap[mod]
                val outputConsumer = OutputConsumerPath.Builder(info!!.outputPath).build()

                val delegate = getJarFileSystem(info.inputPath!!, false)

                if (delegate.get() == null) {
                    throw RuntimeException("Could not open JAR file " + info.inputPath!!.fileName + " for NIO reading!")
                }

                val inputJar = delegate.get()!!.rootDirectories.iterator().next()
                outputConsumer.addNonClassFiles(inputJar, NonClassCopyMode.FIX_META_INF, remapper)

                info.outputConsumerPath = outputConsumer

                remapper.apply(outputConsumer, info.tag)
            }

            //Done in a 3rd loop as this can happen when the remapper is doing its thing.
            for (mod in modsToRemap) {
                val info = infoMap[mod]

                if (info!!.accessWidener != null) {
                    info.accessWidener =
                        remapAccessWidener(requireNotNull(info.accessWidener), remapper.remapper, launcher.targetNamespace)
                }
            }

            remapper.finish()

            for (mod in modsToRemap) {
                val info = infoMap[mod]

                info!!.outputConsumerPath!!.close()

                if (info.accessWidenerPath != null) {
                    getJarFileSystem(info.outputPath!!, false).use { jarFs ->
                        val fs = jarFs.get()
                        Files.delete(fs!!.getPath(info.accessWidenerPath!!))
                        Files.write(fs.getPath(info.accessWidenerPath!!), info.accessWidener!!)
                    }
                }

                mod.paths = listOf(info.outputPath) as List<Path>
            }
        } catch (t: Throwable) {
            remapper?.finish()

            for (info in infoMap.values) {
                if (info.outputPath == null) {
                    continue
                }

                try {
                    Files.deleteIfExists(info.outputPath!!)
                } catch (e: IOException) {
                    warn(LogCategory.MOD_REMAP, "Error deleting failed output jar %s", info.outputPath, e)
                }
            }

            throw FormattedException("Failed to remap mods!", t)
        } finally {
            for (info in infoMap.values) {
                try {
                    if (info.inputIsTemp) Files.deleteIfExists(info.inputPath!!)
                } catch (e: IOException) {
                    warn(LogCategory.MOD_REMAP, "Error deleting temporary input jar %s", info.inputIsTemp, e)
                }
            }
        }
    }

    private fun remapAccessWidener(input: ByteArray, remapper: Remapper, targetNamespace: String?): ByteArray {
        val writer = AccessWidenerWriter()
        val remappingDecorator = AccessWidenerRemapper(writer, remapper, SOURCE_NAMESPACE, targetNamespace)
        val accessWidenerReader = AccessWidenerReader(remappingDecorator)
        accessWidenerReader.read(input, SOURCE_NAMESPACE)
        return writer.write()
    }

    @get:Throws(IOException::class)
    private val remapClasspath: List<Path>
        get() {
            val remapClasspathFile = System.getProperty(SystemProperties.REMAP_CLASSPATH_FILE)
                ?: throw RuntimeException("No remapClasspathFile provided")

            val content = String(Files.readAllBytes(Paths.get(remapClasspathFile)), StandardCharsets.UTF_8)

            return Arrays.stream(content.split(File.pathSeparator.toRegex()).dropLastWhile { it.isEmpty() }
                .toTypedArray())
                .map { first: String? -> Paths.get(first!!) }
                .collect(Collectors.toList())
        }

    /**
     * Determine whether a jar dependencyHandler Mixin remapping with tiny remapper.
     *
     *
     * This is typically the case when a mod was built without the Mixin annotation processor generating refmaps.
     */
    @Throws(IOException::class, URISyntaxException::class)
    private fun requiresMixinRemap(inputPath: Path): Boolean {
        val manifest = readManifest(inputPath) ?: return false

        val mainAttributes = manifest.mainAttributes

        return REMAP_TYPE_STATIC.equals(mainAttributes.getValue(REMAP_TYPE_MANIFEST_KEY), ignoreCase = true)
    }

    private class RemapInfo {
        var tag: InputTag? = null
        var inputPath: Path? = null
        var outputPath: Path? = null
        var inputIsTemp: Boolean = false
        var outputConsumerPath: OutputConsumerPath? = null
        var accessWidenerPath: String? = null
        var accessWidener: ByteArray? = null
    }
}
