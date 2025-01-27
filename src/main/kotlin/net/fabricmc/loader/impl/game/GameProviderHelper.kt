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
package net.fabricmc.loader.impl.game

import com.moltenex.loader.impl.launch.MoltenexLauncher
import net.fabricmc.api.EnvType
import net.fabricmc.loader.impl.FormattedException
import com.moltenex.loader.impl.MoltenexLoaderImpl
import net.fabricmc.loader.impl.util.LoaderUtil.getClassFileName
import net.fabricmc.loader.impl.util.LoaderUtil.normalizeExistingPath
import net.fabricmc.loader.impl.util.LoaderUtil.normalizePath
import net.fabricmc.loader.impl.util.SystemProperties
import net.fabricmc.loader.impl.util.UrlConversionException
import net.fabricmc.loader.impl.util.UrlUtil.getCodeSource
import net.fabricmc.loader.impl.util.log.Log.debug
import net.fabricmc.loader.impl.util.log.Log.info
import net.fabricmc.loader.impl.util.log.Log.warn
import net.fabricmc.loader.impl.util.log.LogCategory
import net.fabricmc.mappingio.tree.MappingTree
import net.fabricmc.tinyremapper.*
import java.io.IOException
import java.net.URI
import java.net.URL
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*
import java.util.jar.JarEntry
import java.util.jar.JarFile
import java.util.zip.ZipFile

object GameProviderHelper {
    val commonGameJar: Path?
        get() = getGameJar(SystemProperties.GAME_JAR_PATH)

    fun getEnvGameJar(env: EnvType): Path? {
        return getGameJar(if (env == EnvType.CLIENT) SystemProperties.GAME_JAR_PATH_CLIENT else SystemProperties.GAME_JAR_PATH_SERVER)
    }

    private fun getGameJar(property: String): Path? {
        val `val` = System.getProperty(property) ?: return null

        val path = Paths.get(`val`)
        if (!Files.exists(path)) throw RuntimeException("Game jar " + path + " (" + normalizePath(path) + ") configured through " + property + " system property doesn't exist")

        return normalizeExistingPath(path)
    }

    fun getSource(loader: ClassLoader, filename: String): Optional<Path> {
        val url: URL?

        if ((loader.getResource(filename).also { url = it }) != null) {
            try {
                return Optional.of(getCodeSource(url!!, filename))
            } catch (e: UrlConversionException) {
                // TODO: Point to a logger
                e.printStackTrace()
            }
        }

        return Optional.empty()
    }

    fun getSources(loader: ClassLoader, filename: String): List<Path> {
        try {
            val urls = loader.getResources(filename)
            val paths: MutableList<Path> = ArrayList()

            while (urls.hasMoreElements()) {
                val url = urls.nextElement()

                try {
                    paths.add(getCodeSource(url, filename))
                } catch (e: UrlConversionException) {
                    // TODO: Point to a logger
                    e.printStackTrace()
                }
            }

            return paths
        } catch (e: IOException) {
            e.printStackTrace()
            return emptyList()
        }
    }

    fun findFirst(
        paths: List<Path>,
        zipFiles: MutableMap<Path?, ZipFile?>,
        isClassName: Boolean,
        vararg names: String
    ): FindResult? {
        for (name in names) {
            val file = if (isClassName) getClassFileName(name) else name

            for (path in paths) {
                if (Files.isDirectory(path)) {
                    if (Files.exists(path.resolve(file))) {
                        return FindResult(name, path)
                    }
                } else {
                    var zipFile = zipFiles[path]

                    if (zipFile == null) {
                        try {
                            zipFile = ZipFile(path.toFile())
                            zipFiles[path] = zipFile
                        } catch (e: IOException) {
                            throw RuntimeException("Error reading $path", e)
                        }
                    }

                    if (zipFile.getEntry(file) != null) {
                        return FindResult(name, path)
                    }
                }
            }
        }

        return null
    }

    private var emittedInfo = false

    @JvmOverloads
    fun deobfuscate(
        inputFileMap: MutableMap<String?, Path?>,
        gameId: String,
        gameVersion: String,
        gameDir: Path,
        launcher: MoltenexLauncher,
        sourceNamespace: String = "official"
    ): MutableMap<String?, Path?> {
        debug(LogCategory.GAME_REMAP, "Requesting deobfuscation of %s", inputFileMap)

        if (launcher.isDevelopment) { // in-dev is already deobfuscated
            return inputFileMap
        }

        val mappingConfig = launcher.mappingConfiguration

        if (!mappingConfig!!.matches(gameId, gameVersion)) {
            val mappingsGameId = mappingConfig.getGameId()
            val mappingsGameVersion = mappingConfig.getGameVersion()

            throw FormattedException(
                "Incompatible mappings",
                String.format(
                    "Supplied mappings for %s %s are incompatible with %s %s, this is likely caused by launcher misbehavior",
                    (mappingsGameId ?: "(unknown)"),
                    (mappingsGameVersion ?: "(unknown)"),
                    gameId,
                    gameVersion
                )
            )
        }

        val targetNamespace = mappingConfig.targetNamespace
        val namespaces = mappingConfig.getNamespaces()

        if (namespaces == null) {
            debug(LogCategory.GAME_REMAP, "No mappings, using input files")
            return inputFileMap
        }

        if (!namespaces.contains(targetNamespace) || !namespaces.contains(sourceNamespace)) {
            debug(LogCategory.GAME_REMAP, "Missing namespace in mappings, using input files")
            return inputFileMap
        }

        val deobfJarDir = getDeobfJarDir(gameDir, gameId, gameVersion)
        val inputFiles: MutableList<Path?> = ArrayList(inputFileMap.size)
        val outputFiles: MutableList<Path> = ArrayList(inputFileMap.size)
        val tmpFiles: MutableList<Path> = ArrayList(inputFileMap.size)
        val ret: MutableMap<String?, Path?> = HashMap(inputFileMap.size)
        var anyMissing = false

        for ((name, inputFile) in inputFileMap) {
            // TODO: allow versioning mappings?
            val deobfJarFilename = String.format("%s-%s.jar", name, targetNamespace)
            val outputFile = deobfJarDir.resolve(deobfJarFilename)
            val tmpFile = deobfJarDir.resolve("$deobfJarFilename.tmp")

            if (Files.exists(tmpFile)) { // previous unfinished remap attempt
                warn(
                    LogCategory.GAME_REMAP,
                    "Incomplete remapped file found! This means that the remapping process failed on the previous launch. If this persists, make sure to let us at Moltenex know!"
                )

                try {
                    Files.deleteIfExists(outputFile)
                    Files.deleteIfExists(tmpFile)
                } catch (e: IOException) {
                    throw RuntimeException("can't delete incompletely remapped files", e)
                }
            }

            inputFiles.add(inputFile)
            outputFiles.add(outputFile)
            tmpFiles.add(tmpFile)
            ret[name] = outputFile

            if (!anyMissing && !Files.exists(outputFile)) {
                anyMissing = true
            }
        }

        if (!anyMissing) {
            debug(LogCategory.GAME_REMAP, "Remapped files exist already, reusing them")
            return ret
        }

        debug(LogCategory.GAME_REMAP, "Moltenex mapping file detected, applying...")

        if (!emittedInfo) {
            info(LogCategory.GAME_REMAP, "Moltenex is preparing JARs on first launch, this may take a few seconds...")
            emittedInfo = true
        }

        try {
            Files.createDirectories(deobfJarDir)
            deobfuscate0(
                inputFiles, outputFiles, tmpFiles,
                mappingConfig.getMappings()!!, sourceNamespace, targetNamespace, launcher
            )
        } catch (e: IOException) {
            throw RuntimeException("error remapping game jars $inputFiles", e)
        }

        return ret
    }

    private fun getDeobfJarDir(gameDir: Path, gameId: String, gameVersion: String): Path {
        val ret = gameDir.resolve(MoltenexLoaderImpl.CACHE_DIR_NAME).resolve(MoltenexLoaderImpl.REMAPPED_JARS_DIR_NAME)
        val versionDirName = StringBuilder()

        if (!gameId.isEmpty()) {
            versionDirName.append(gameId)
        }

        if (!gameVersion.isEmpty()) {
            if (versionDirName.length > 0) versionDirName.append('-')
            versionDirName.append(gameVersion)
        }

        if (versionDirName.length > 0) versionDirName.append('-')
        versionDirName.append(MoltenexLoaderImpl.VERSION)

        return ret.resolve(versionDirName.toString().replace("[^\\w\\-\\. ]+".toRegex(), "_"))
    }

    @Throws(IOException::class)
    private fun deobfuscate0(
        inputFiles: List<Path?>,
        outputFiles: List<Path>,
        tmpFiles: List<Path>,
        mappings: MappingTree,
        sourceNamespace: String,
        targetNamespace: String,
        launcher: MoltenexLauncher
    ) {
        val remapper = TinyRemapper.newRemapper()
            .withMappings(TinyUtils.createMappingProvider(mappings, sourceNamespace, targetNamespace))
            .rebuildSourceFilenames(true)
            .build()

        val depPaths: MutableSet<Path> = HashSet()

        if (System.getProperty(SystemProperties.DEBUG_DEOBFUSCATE_WITH_CLASSPATH) != null) {
            for (path in launcher.classPath!!) {
                if (!inputFiles.contains(path)) {
                    depPaths.add(path!!)

                    debug(LogCategory.GAME_REMAP, "Appending '%s' to remapper classpath", path)
                    remapper.readClassPathAsync(path)
                }
            }
        }

        val outputConsumers: MutableList<OutputConsumerPath> = ArrayList(inputFiles.size)
        val inputTags: MutableList<InputTag> = ArrayList(inputFiles.size)

        try {
            for (i in inputFiles.indices) {
                val inputFile = inputFiles[i]
                val tmpFile = tmpFiles[i]

                val inputTag = remapper.createInputTag()
                val outputConsumer = OutputConsumerPath.Builder(tmpFile) // force jar despite the .tmp extension
                    .assumeArchive(true)
                    .build()

                outputConsumers.add(outputConsumer)
                inputTags.add(inputTag)

                outputConsumer.addNonClassFiles(inputFile, NonClassCopyMode.FIX_META_INF, remapper)
                remapper.readInputsAsync(inputTag, inputFile)
            }

            for (i in inputFiles.indices) {
                remapper.apply(outputConsumers[i], inputTags[i])
            }
        } finally {
            for (outputConsumer in outputConsumers) {
                outputConsumer.close()
            }

            remapper.finish()
        }

        // Minecraft doesn't tend to check if a ZipFileSystem is already present,
        // so we clean up here.
        depPaths.addAll(tmpFiles)

        for (p in depPaths) {
            try {
                p.fileSystem.close()
            } catch (e: Exception) {
                // pass
            }

            try {
                FileSystems.getFileSystem(URI("jar:" + p.toUri())).close()
            } catch (e: Exception) {
                // pass
            }
        }

        val missing: MutableList<Path?> = ArrayList()

        for (i in inputFiles.indices) {
            val inputFile = inputFiles[i]
            val tmpFile = tmpFiles[i]
            val outputFile = outputFiles[i]

            val found: Boolean

            JarFile(tmpFile.toFile()).use { jar ->
                found = jar.stream().anyMatch { e: JarEntry -> e.name.endsWith(".class") }
            }
            if (!found) {
                missing.add(inputFile)
                Files.delete(tmpFile)
            } else {
                Files.move(tmpFile, outputFile)
            }
        }

        if (!missing.isEmpty()) {
            throw RuntimeException("Generated deobfuscated JARs contain no classes: $missing")
        }
    }

    class FindResult internal constructor(val name: String, val path: Path)
}
