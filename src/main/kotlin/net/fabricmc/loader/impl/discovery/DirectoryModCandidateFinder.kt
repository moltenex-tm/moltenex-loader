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

import net.fabricmc.loader.impl.discovery.ModCandidateFinder.ModCandidateConsumer
import net.fabricmc.loader.impl.util.LoaderUtil
import net.fabricmc.loader.impl.util.log.Log
import net.fabricmc.loader.impl.util.log.LogCategory
import java.io.IOException
import java.nio.file.*
import java.nio.file.attribute.BasicFileAttributes
import java.util.*

class DirectoryModCandidateFinder(path: Path?, private val requiresRemap: Boolean) : ModCandidateFinder {
    private val path: Path = LoaderUtil.normalizePath(path!!)

    override fun findCandidates(out: ModCandidateConsumer) {
        if (!Files.exists(path)) {
            try {
                Files.createDirectory(path)
                return
            } catch (e: IOException) {
                throw RuntimeException("Could not create directory $path", e)
            }
        }

        if (!Files.isDirectory(path)) {
            throw RuntimeException("$path is not a directory!")
        }

        try {
            Files.walkFileTree(
                this.path,
                EnumSet.of(FileVisitOption.FOLLOW_LINKS),
                1,
                object : SimpleFileVisitor<Path>() {
                    @Throws(IOException::class)
                    override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                        if (isValidFile(file)) {
                            out?.accept(file, requiresRemap)
                        }

                        return FileVisitResult.CONTINUE
                    }
                })
        } catch (e: IOException) {
            throw RuntimeException("Exception while searching for mods in '$path'!", e)
        }
    }

    companion object {
        @JvmStatic
		fun isValidFile(path: Path): Boolean {
            /*
		 * We only propose a file as a possible mod in the following scenarios:
		 * General: Must be a jar file
		 *
		 * Some OSes Generate metadata so consider the following because of OSes:
		 * UNIX: Exclude if file is hidden; this occurs when starting a file name with `.`
		 * MacOS: Exclude hidden + startsWith "." since Mac OS names their metadata files in the form of `.mod.jar`
		 */

            if (!Files.isRegularFile(path)) return false

            try {
                if (Files.isHidden(path)) return false
            } catch (e: IOException) {
                Log.warn(LogCategory.DISCOVERY, "Error checking if file %s is hidden", path, e)
                return false
            }

            val fileName = path.fileName.toString()

            return fileName.endsWith(".jar") && !fileName.startsWith(".")
        }
    }
}
