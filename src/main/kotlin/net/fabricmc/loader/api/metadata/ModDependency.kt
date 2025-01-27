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
import com.moltenex.loader.api.util.version.VersionInterval
import net.fabricmc.loader.api.metadata.version.VersionPredicate

/**
 * Represents a dependency.
 */
interface ModDependency {
    /**
     * Get the kind of dependency.
     */
	val kind: Kind?

    /**
     * Returns the ID of the mod to check.
     */
	val modId: String?

    /**
     * Returns if the version fulfills this dependency's version requirement.
     *
     * @param version the version to check
     */
    fun matches(version: Version?): Boolean

    /**
     * Returns a representation of the dependency's version requirements.
     *
     * @return representation of the dependency's version requirements
     */
    fun getVersionRequirements(): Collection<VersionPredicate?>?

    /**
     * Returns the version intervals covered by the dependency's version requirements.
     *
     *
     * There may be multiple because the allowed range may not be consecutive.
     */
    fun getVersionIntervals(): List<VersionInterval>

    enum class Kind(
		/**
         * Get the key for the dependency as used by fabric.mod.json (v1+) and dependency overrides.
         */
		@JvmField val key: String,
		/**
         * Get whether the dependency is positive, encouraging the inclusion of a mod instead of negative/discouraging.
         */
        val isPositive: Boolean,
		/**
         * Get whether it is a soft dependency, allowing the mod to still load if the dependency is unmet.
         */
        val isSoft: Boolean
    ) {
        DEPENDS("depends", true, false),
        RECOMMENDS("recommends", true, true),
        SUGGESTS("suggests", true, true),
        CONFLICTS("conflicts", false, true),
        BREAKS("breaks", false, false);

        companion object {
            private val map = createMap()

            /**
             * Parse a dependency kind from its key as provided by [.getKey].
             */
			fun parse(key: String): Kind? {
                return map[key]
            }

            private fun createMap(): Map<String, Kind> {
                val values = entries.toTypedArray()
                val ret: MutableMap<String, Kind> = HashMap(values.size)

                for (kind in values) {
                    ret[kind.key] = kind
                }

                return ret
            }
        }
    }
}
