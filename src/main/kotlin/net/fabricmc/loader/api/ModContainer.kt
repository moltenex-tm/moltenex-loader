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
package net.fabricmc.loader.api

import net.fabricmc.loader.api.metadata.ModMetadata
import net.fabricmc.loader.api.metadata.ModOrigin
import java.nio.file.Files
import java.nio.file.Path
import java.util.*

/**
 * Represents a mod.
 */
interface ModContainer {
    /**
     * Returns the metadata of this mod.
     */
	val metadata: ModMetadata?

    /**
     * Returns the root directories of the mod (inside JAR/folder), exposing its contents.
     *
     *
     * The paths may point to regular folders or into mod JARs. Multiple root paths may occur in development
     * environments with `-Dfabric.classPathGroups` as used in multi-project mod setups.
     *
     *
     * A path returned by this method may be incompatible with [Path.toFile] as its FileSystem doesn't
     * necessarily represent the OS file system, but potentially a virtual view of jar contents or another abstraction.
     *
     * @return the root directories of the mod, may be empty for builtin or other synthetic mods
     */
    val rootPaths: List<Path>

    /**
     * Gets an NIO reference to a file inside the JAR/folder.
     *
     *
     * The path, if present, is guaranteed to exist!
     *
     *
     * A path returned by this method may be incompatible with [Path.toFile] as its FileSystem doesn't
     * necessarily represent the OS file system, but potentially a virtual view of jar contents or another abstraction.
     *
     * @param file The location from a root path, using `/` as a separator.
     * @return optional containing the path to a given file or empty if it can't be found
     */
    fun findPath(file: String): Optional<Path> {
        for (root in rootPaths) {
            val path = root.resolve(file.replace("/", root.fileSystem.separator))
            if (Files.exists(path)) return Optional.of(path)
        }

        return Optional.empty()
    }

    /**
     * Gets where the mod was loaded from originally, the mod jar/folder itself.
     *
     *
     * This location is not necessarily identical to the code source used at runtime, a mod may get copied or
     * otherwise transformed before being put on the class path. It thus mostly represents the installation and initial
     * loading, not what is being directly accessed at runtime.
     *
     *
     * The mod origin is provided for working with the installation like telling the user where a mod has been
     * installed at. Accessing the files inside a mod jar/folder should use [.findPath] and [.getRootPaths]
     * instead. Those also abstract jar accesses through the virtual `ZipFileSystem` away.
     *
     * @return mod origin
     */
    val origin: ModOrigin?

    /**
     * Get the mod containing this mod (nested jar parent).
     *
     * @return mod containing this mod or empty if not nested
     */
    val containingMod: Optional<ModContainer>

    /**
     * Get the active mods contained within this mod (nested jar children).
     *
     * @return active contained mods within this mod's jar
     */
    val containedMods: Collection<ModContainer?>?
}
