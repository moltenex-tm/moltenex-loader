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

import net.fabricmc.api.EnvType
import net.fabricmc.loader.impl.game.LibClassifier.LibraryType
import net.fabricmc.loader.impl.util.LoaderUtil.normalizeExistingPath
import net.fabricmc.loader.impl.util.ManifestUtil.getClassPath
import net.fabricmc.loader.impl.util.SystemProperties
import net.fabricmc.loader.impl.util.UrlUtil.asPath
import net.fabricmc.loader.impl.util.UrlUtil.getCodeSource
import net.fabricmc.loader.impl.util.log.Log.info
import net.fabricmc.loader.impl.util.log.LogCategory
import java.io.File
import java.io.IOException
import java.net.URL
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*
import java.util.jar.JarFile
import java.util.jar.Manifest
import java.util.zip.ZipError
import java.util.zip.ZipFile

class LibClassifier<L>(
    cls: Class<L>,
    env: EnvType,
    gameProvider: GameProvider
) where L : Enum<L>, L : LibraryType {
    private val libs: MutableList<L>
    private val origins: MutableMap<L, Path>
    private val localPaths: MutableMap<L, String>
    val systemLibraries: MutableSet<Path> = HashSet()
    val unmatchedOrigins: MutableList<Path> = ArrayList()

    init {
        val libs = cls.enumConstants

        this.libs = ArrayList(libs.size)
        this.origins = EnumMap(cls)
        this.localPaths = EnumMap(cls)

        // game provider libs
        for (lib in libs) {
            if (lib!!.isApplicable(env)) {
                this.libs.add(lib)
            }
        }

        // system libs configured through system property
        val sb = if (DEBUG) StringBuilder() else null
        val systemLibProp = System.getProperty(SystemProperties.SYSTEM_LIBRARIES)

        if (systemLibProp != null) {
            for (lib in systemLibProp.split(File.pathSeparator.toRegex()).dropLastWhile { it.isEmpty() }
                .toTypedArray()) {
                var path = Paths.get(lib)

                if (!Files.exists(path)) {
                    info(LogCategory.LIB_CLASSIFICATION, "Skipping missing system library entry %s", path)
                    continue
                }

                path = normalizeExistingPath(path)

                if (systemLibraries.add(path)) {
                    if (DEBUG) sb!!.append(String.format("🇸 %s%n", path))
                }
            }
        }

        // loader libs
        val junitRun = System.getProperty(SystemProperties.UNIT_TEST) != null

        for (lib in LoaderLibrary.entries) {
            if (!lib.isApplicable(env!!, junitRun)) continue

            if (lib.path != null) {
                val path = normalizeExistingPath(lib.path)
                systemLibraries.add(path)

                if (DEBUG) sb!!.append(String.format("✅ %s %s%n", lib.name, path))
            } else {
                if (DEBUG) sb!!.append(String.format("❎ %s%n", lib.name))
            }
        }

        // game provider itself
        var gameProviderPath = getCodeSource(gameProvider.javaClass)

        if (gameProviderPath != null) {
            gameProviderPath = normalizeExistingPath(gameProviderPath)

            if (systemLibraries.add(gameProviderPath)) {
                if (DEBUG) sb!!.append(String.format("✅ gameprovider %s%n", gameProviderPath))
            }
        } else {
            if (DEBUG) sb!!.append("❎ gameprovider")
        }

        if (DEBUG) info(LogCategory.LIB_CLASSIFICATION, "Loader/system libraries:%n%s", sb)

        // process indirectly referenced libs
        processManifestClassPath(
            LoaderLibrary.SERVER_LAUNCH,
            env,
            junitRun
        ) // not used by fabric itself, but others add Log4J this way
    }

    @Throws(IOException::class)
    private fun processManifestClassPath(lib: LoaderLibrary, env: EnvType?, junitRun: Boolean) {
        if (lib.path == null || !lib.isApplicable(env!!, junitRun) || !Files.isRegularFile(lib.path)) return

        val manifest: Manifest

        ZipFile(lib.path.toFile()).use { zf ->
            val entry = zf.getEntry(JarFile.MANIFEST_NAME) ?: return
            manifest = Manifest(zf.getInputStream(entry))
        }
        val cp = getClassPath(manifest, lib.path) ?: return

        for (url in cp) {
            process(url)
        }
    }

    @Throws(IOException::class)
    fun process(url: URL?) {
        process(asPath(url!!))
    }

    @SafeVarargs
    @Throws(IOException::class)
    fun process(paths: Iterable<Path>, vararg excludedLibs: L) {
        val excluded = makeSet<L>(excludedLibs)

        for (path in paths) {
            process(path, excluded)
        }
    }

    @SafeVarargs
    @Throws(IOException::class)
    fun process(path: Path, vararg excludedLibs: L) {
        process(path, makeSet(excludedLibs))
    }

    @Throws(IOException::class)
    private fun process(path: Path, excludedLibs: Set<L>) {
        var path = path
        path = normalizeExistingPath(path)
        if (systemLibraries.contains(path)) return

        var matched = false

        if (Files.isDirectory(path)) {
            for (lib in libs) {
                if (excludedLibs.contains(lib) || origins.containsKey(lib)) continue

                for (p in lib!!.paths) {
                    if (Files.exists(path.resolve(p))) {
                        matched = true
                        addLibrary(lib, path, p)
                        break
                    }
                }
            }
        } else {
            try {
                ZipFile(path.toFile()).use { zf ->
                    for (lib in libs) {
                        if (excludedLibs.contains(lib) || origins.containsKey(lib)) continue

                        for (p in lib!!.paths) {
                            if (zf.getEntry(p) != null) {
                                matched = true
                                addLibrary(lib, path, p)
                                break
                            }
                        }
                    }
                }
            } catch (e: ZipError) {
                throw IOException("error reading $path", e)
            } catch (e: IOException) {
                throw IOException("error reading $path", e)
            }
        }

        if (!matched) {
            unmatchedOrigins.add(path)

            if (DEBUG) info(LogCategory.LIB_CLASSIFICATION, "unmatched %s", path)
        }
    }

    private fun addLibrary(lib: L, originPath: Path, localPath: String) {
        val prev = origins.put(lib, originPath)
        check(prev == null) { "lib $lib was already added" }
        localPaths[lib] = localPath

        if (DEBUG) info(LogCategory.LIB_CLASSIFICATION, "%s %s (%s)", lib!!.name, originPath, localPath)
    }

    @SafeVarargs
    fun `is`(path: Path, vararg libs: L): Boolean {
        for (lib in libs) {
            if (path == origins[lib]) return true
        }

        return false
    }

    fun has(lib: L): Boolean {
        return origins.containsKey(lib)
    }

    fun getOrigin(lib: L): Path? {
        return origins[lib]
    }

    fun getLocalPath(lib: L): String? {
        return localPaths[lib]
    }

    fun getClassName(lib: L): String? {
        val localPath = localPaths[lib]
        if (localPath == null || !localPath.endsWith(".class")) return null

        return localPath.substring(0, localPath.length - 6).replace('/', '.')
    }

    /**
     * Returns system level libraries, typically Loader and its dependencies.
     */
    fun getSystemLibraries(): Collection<Path> {
        return systemLibraries
    }

    fun remove(path: Path): Boolean {
        if (unmatchedOrigins.remove(path)) return true

        var ret = false

        val it: MutableIterator<Map.Entry<L, Path>> = origins.entries.iterator()
        while (it.hasNext()) {
            val entry = it.next()

            if (entry.value == path) {
                localPaths.remove(entry.key)
                it.remove()

                ret = true
            }
        }

        return ret
    }

    interface LibraryType {
        fun isApplicable(env: EnvType?): Boolean
        val paths: Array<String>
    }

    companion object {
        private val DEBUG = System.getProperty(SystemProperties.DEBUG_LOG_LIB_CLASSIFICATION) != null

        private fun <L : Enum<L>?> makeSet(libs: Array<out L>): Set<L> {
            if (libs.size == 0) return emptySet()

            val ret: MutableSet<L> = EnumSet.of(libs[0])

            for (i in 1..<libs.size) {
                ret.add(libs[i])
            }

            return ret
        }
    }
}