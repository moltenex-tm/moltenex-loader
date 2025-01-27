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
import net.fabricmc.loader.impl.discovery.DirectoryModCandidateFinder.Companion.isValidFile
import net.fabricmc.loader.impl.discovery.ModCandidateFinder.ModCandidateConsumer
import net.fabricmc.loader.impl.util.Arguments
import net.fabricmc.loader.impl.util.LoaderUtil.normalizePath
import net.fabricmc.loader.impl.util.SystemProperties
import net.fabricmc.loader.impl.util.log.Log.warn
import net.fabricmc.loader.impl.util.log.LogCategory
import java.io.File
import java.io.IOException
import java.nio.file.*
import java.nio.file.attribute.BasicFileAttributes

class ArgumentModCandidateFinder(private val requiresRemap: Boolean) : ModCandidateFinder {
    override fun findCandidates(out: ModCandidateConsumer) {
        var list = System.getProperty(SystemProperties.ADD_MODS)
        if (list != null) addMods(list, "system property", out)

        list = MoltenexLoaderImpl.INSTANCE?.gameProvider?.getArguments()?.remove(Arguments.ADD_MODS)
        if (list != null) addMods(list, "argument", out)
    }

    private fun addMods(list: String, source: String, out: ModCandidateConsumer) {
        for (pathStr in list.split(File.pathSeparator.toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()) {
            if (pathStr.isEmpty()) continue

            if (pathStr.startsWith("@")) {
                val path = Paths.get(pathStr.substring(1))

                if (!Files.isRegularFile(path)) {
                    warn(LogCategory.DISCOVERY, "Skipping missing/invalid %s provided mod list file %s", source, path)
                    continue
                }

                try {
                    Files.newBufferedReader(path).use { reader ->
                        val fileSource = String.format("%s file %s", source, path)
                        var line: String
                        while ((reader.readLine().also { line = it }) != null) {
                            line = line.trim { it <= ' ' }
                            if (line.isEmpty()) continue

                            addMod(line, fileSource, out)
                        }
                    }
                } catch (e: IOException) {
                    throw RuntimeException(String.format("Error reading %s provided mod list file %s", source, path), e)
                }
            } else {
                addMod(pathStr, source, out)
            }
        }
    }

    private fun addMod(pathStr: String, source: String, out: ModCandidateConsumer) {
        val path = normalizePath(Paths.get(pathStr))

        if (!Files.exists(path)) { // missing
            warn(LogCategory.DISCOVERY, "Skipping missing %s provided mod path %s", source, path)
        } else if (Files.isDirectory(path)) { // directory for extracted mod (in-dev usually) or jars (like mods, but recursive)
            if (isHidden(path)) {
                warn(LogCategory.DISCOVERY, "Ignoring hidden %s provided mod path %s", source, path)
                return
            }

            if (Files.exists(path.resolve("fabric.mod.json"))) { // extracted mod
                out.accept(path, requiresRemap)
            } else { // dir containing jars
                try {
                    val skipped: MutableList<String> = ArrayList()

                    Files.walkFileTree(path, object : SimpleFileVisitor<Path>() {
                        @Throws(IOException::class)
                        override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                            if (isValidFile(file)) {
                                out.accept(file, requiresRemap)
                            } else {
                                skipped.add(path.relativize(file).toString())
                            }

                            return FileVisitResult.CONTINUE
                        }

                        @Throws(IOException::class)
                        override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
                            return if (isHidden(dir)) {
                                FileVisitResult.SKIP_SUBTREE
                            } else {
                                FileVisitResult.CONTINUE
                            }
                        }
                    })

                    if (skipped.isNotEmpty()) {
                        warn(
                            LogCategory.DISCOVERY,
                            "Incompatible files in %s provided mod directory %s (non-jar or hidden): %s",
                            source,
                            path,
                            java.lang.String.join(", ", skipped)
                        )
                    }
                } catch (e: IOException) {
                    warn(LogCategory.DISCOVERY, "Error processing %s provided mod path %s: %s", source, path, e)
                }
            }
        } else { // single file
            if (!isValidFile(path)) {
                warn(
                    LogCategory.DISCOVERY,
                    "Incompatible file in %s provided mod path %s (non-jar or hidden)",
                    source,
                    path
                )
            } else {
                out.accept(path, requiresRemap)
            }
        }
    }

    companion object {
        private fun isHidden(path: Path): Boolean {
            try {
                return path.fileName.toString().startsWith(".") || Files.isHidden(path)
            } catch (e: IOException) {
                warn(LogCategory.DISCOVERY, "Error determining whether %s is hidden: %s", path, e)
                return true
            }
        }
    }
}
