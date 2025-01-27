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
package net.fabricmc.loader.api.metadata

import com.moltenex.loader.api.util.version.Version
import java.util.*

/**
 * The metadata of a mod.
 */
interface ModMetadata {
    /**
     * Returns the type of the mod.
     *
     *
     * The types may be `fabric` or `builtin` by default.
     *
     * @return the type of the mod
     */
	val type: String?

    // When adding getters, follow the order as presented on the wiki.
    // No defaults.
    /**
     * Returns the mod's ID.
     *
     *
     * A mod's id must have only lowercase letters, digits, `-`, or `_`.
     *
     * @return the mod's ID.
     */
	val id: String?

    /**
     * Returns the mod's ID provides.
     *
     *
     * The aliases follow the same rules as ID
     *
     * @return the mod's ID provides
     */
	val provides: Collection<String?>?

    /**
     * Returns the mod's version.
     */
    var version: Version?

    /**
     * Returns the mod's environment.
     */
	val environment: ModEnvironment?

    /**
     * Returns all of the mod's dependencies.
     */
    var dependencies: Collection<ModDependency?>

    /**
     * Returns the mod's display name.
     */
	val name: String?

    /**
     * Returns the mod's description.
     */
	val description: String?

    /**
     * Returns the mod's authors.
     */
	val authors: Collection<Person?>?

    /**
     * Returns the mod's contributors.
     */
	val contributors: Collection<Person?>?

    /**
     * Returns the mod's contact information.
     */
	val contact: ContactInformation?

    /**
     * Returns the mod's licenses.
     */
	val license: Collection<String?>?

    /**
     * Gets the path to an icon.
     *
     *
     * The standard defines icons as square .PNG files, however their
     * dimensions are not defined - in particular, they are not
     * guaranteed to be a power of two.
     *
     *
     * The preferred size is used in the following manner:
     *  * the smallest image larger than or equal to the size
     * is returned, if one is present;
     *  * failing that, the largest image is returned.
     *
     * @param size the preferred size
     * @return the icon path, if any
     */
    fun getIconPath(size: Int): Optional<String>

    /**
     * Returns if the mod's `fabric.mod.json` declares a custom value under `key`.
     *
     * @param key the key
     * @return whether a custom value is present
     */
    fun containsCustomValue(key: String?): Boolean

    /**
     * Returns the mod's `fabric.mod.json` declared custom value under `key`.
     *
     * @param key the key
     * @return the custom value, or `null` if no such value is present
     */
    fun getCustomValue(key: String?): CustomValue?

    /**
     * Gets all custom values defined by this mod.
     *
     *
     * Note this map is unmodifiable.
     *
     * @return a map containing the custom values this mod defines.
     */
	val customValues: Map<String?, CustomValue?>?
}
