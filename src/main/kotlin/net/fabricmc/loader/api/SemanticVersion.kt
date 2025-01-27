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

import com.moltenex.loader.api.util.version.Version
import net.fabricmc.loader.impl.util.version.VersionParser
import java.util.*

/**
 * Represents a [Sematic Version](https://semver.org/).
 *
 *
 * Compared to a regular [Version], this type of version receives better support
 * for version comparisons in dependency notations, and is preferred.
 *
 * @see Version
 */
interface SemanticVersion : Version {
    /**
     * Returns the number of components in this version.
     *
     *
     * For example, `1.3.x` has 3 components.
     *
     * @return the number of components
     */
	val versionComponentCount: Int

    /**
     * Returns the version component at `pos`.
     *
     *
     * May return [.COMPONENT_WILDCARD] to indicate a wildcard component.
     *
     *
     * If the pos exceeds the number of components, returns [.COMPONENT_WILDCARD]
     * if the version [has wildcard][.hasWildcard]; otherwise returns `0`.
     *
     * @param pos the position to check
     * @return the version component
     */
    fun getVersionComponent(pos: Int): Int

    /**
     * Returns the prerelease key in the version notation.
     *
     *
     * The prerelease key is indicated by a `-` before a `+` in
     * the version notation.
     *
     * @return the optional prerelease key
     */
	val prereleaseKey: Optional<String>?

    /**
     * Returns the build key in the version notation.
     *
     *
     * The build key is indicated by a `+` in the version notation.
     *
     * @return the optional build key
     */
	val buildKey: Optional<String>?

    /**
     * Returns if a wildcard notation is present in this version.
     *
     *
     * A wildcard notation is a `x`, `X`, or `*` in the version string,
     * such as `2.5.*`.
     *
     * @return whether this version has a wildcard notation
     */
    fun hasWildcard(): Boolean

    @Deprecated("Use {@link #compareTo(Version)} instead")
    fun compareTo(o: SemanticVersion?): Int {
        return compareTo(o as Version?)
    }

    companion object {
        /**
         * Parses a semantic version from a string notation.
         *
         * @param s the string notation of the version
         * @return the parsed version
         * @throws VersionParsingException if a problem arises during version parsing
         */
        @JvmStatic
		@Throws(VersionParsingException::class)
        fun parse(s: String): SemanticVersion {
            return VersionParser.parseSemantic(s)
        }

        /**
         * The value of [version component][.getVersionComponent] that indicates
         * a [wildcard][.hasWildcard].
         */
        const val COMPONENT_WILDCARD: Int = Int.MIN_VALUE
    }
}
