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
package net.fabricmc.loader.impl

import com.moltenex.loader.impl.MoltenexLoaderImpl
import net.fabricmc.loader.api.ModContainer
import net.fabricmc.loader.api.metadata.ModOrigin
import net.fabricmc.loader.impl.discovery.ModCandidateImpl
import net.fabricmc.loader.impl.metadata.LoaderModMetadata
import net.fabricmc.loader.impl.metadata.ModOriginImpl
import net.fabricmc.loader.impl.util.FileSystemUtil.getJarFileSystem
import net.fabricmc.loader.impl.util.log.Log
import net.fabricmc.loader.impl.util.log.LogCategory
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.*
import kotlin.concurrent.Volatile


class ModContainerImpl(candidate: ModCandidateImpl) : ModContainer {
    private val info: LoaderModMetadata? = null
    override val metadata: LoaderModMetadata = candidate.metadata
    override val origin: ModOrigin
    val codeSourcePaths: List<Path>? = candidate.paths
    private val parentModId =
        if (candidate.parentMods.isEmpty()) null else candidate.parentMods.iterator().next().id
    private lateinit var childModIds: MutableCollection<String>

    @Volatile
    private var roots: List<Path>? = null

    override val rootPaths: List<Path>
        get() {
            var ret = roots

            if (ret == null || !checkFsOpen(ret)) {
                ret = obtainRootPaths()
                roots = ret // obtainRootPaths is thread safe, but we need to avoid plain or repeated reads to root
            }

            return ret
        }

    private fun checkFsOpen(paths: List<Path>): Boolean {
        for (path in paths) {
            if (path.fileSystem.isOpen) continue

            if (!warnedClose) {
                if (!MoltenexLoaderImpl.INSTANCE?.isDevelopmentEnvironment()!!) warnedClose = true
                Log.warn(
                    LogCategory.GENERAL,
                    "FileSystem for %s has been closed unexpectedly, existing root path references may break!",
                    this
                )
            }

            return false
        }

        return true
    }

    private var warnedClose = false

    init {
        for (c in candidate.nestedMods) {
            if (c.parentMods.size <= 1 || c.parentMods.iterator().next() == candidate) {
                childModIds.add(c.id!!)
            }
        }

        val paths = candidate.originPaths
        this.origin = if (paths != null) ModOriginImpl(paths) else ModOriginImpl(parentModId, candidate.localPath)
    }

    private fun obtainRootPaths(): List<Path> {
        var allDirs = true

        for (path in codeSourcePaths!!) {
            if (!Files.isDirectory(path)) {
                allDirs = false
                break
            }
        }

        if (allDirs) return codeSourcePaths

        try {
            if (codeSourcePaths.size == 1) {
                return listOf(
                    obtainRootPath(
                        codeSourcePaths[0]
                    )
                )
            } else {
                val ret: MutableList<Path> = ArrayList(codeSourcePaths.size)

                for (path in codeSourcePaths) {
                    ret.add(obtainRootPath(path))
                }

                return Collections.unmodifiableList(ret)
            }
        } catch (e: IOException) {
            throw RuntimeException("Failed to obtain root directory for mod '" + metadata.id + "'!", e)
        }
    }


    override val containingMod: Optional<ModContainer>
        get() = if (parentModId != null) {
            MoltenexLoaderImpl.INSTANCE?.getModContainer(parentModId)?.orElse(null)?.let { Optional.of(it) } ?: Optional.empty()
        } else {
            Optional.empty()
        }

    override val containedMods: Collection<ModContainer>
        get() {
            if (childModIds.isEmpty()) return emptyList()

            val ret: MutableList<ModContainer> = ArrayList(childModIds.size)

            for (id in childModIds) {
                val mod = MoltenexLoaderImpl.INSTANCE?.getModContainer(id)?.orElse(null)
                if (mod != null) ret.add(mod)
            }

            return ret
        }

    fun getInfo(): LoaderModMetadata {
        return info!!
    }

    override fun toString(): String {
        return java.lang.String.format("%s %s", metadata.id, metadata.version)
    }

    companion object {

        @Throws(IOException::class)
        private fun obtainRootPath(path: Path): Path {
            if (Files.isDirectory(path)) {
                return path
            } else  /* JAR */ {
                val delegate = getJarFileSystem(path, false)
                val fs =
                    delegate.get() ?: throw RuntimeException("Could not open JAR file $path for NIO reading!")

                return fs.rootDirectories.iterator().next()

                // We never close here. It's fine. getJarFileSystem() will handle it gracefully, and so should mods
            }
        }
    }
}
