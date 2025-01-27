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
package net.fabricmc.loader.impl.util.version

import com.moltenex.loader.api.util.version.Version
import net.fabricmc.loader.api.SemanticVersion
import net.fabricmc.loader.api.VersionParsingException
import java.util.*
import java.util.regex.Pattern
import kotlin.math.max

/**
 * Parser for a superset of the semantic version format described at [semver.org](https://semver.org).
 *
 *
 * This superset allows additionally
 *  * Arbitrary number of `<version core>` components, but at least 1
 *  * `x`, `X` or `*` for the last `<version core>` component with `storeX` if not the first
 *  * Arbitrary `<build>` contents
 *
 */
@Suppress("deprecation")
open class SemanticVersionImpl: SemanticVersion {
    private val components: IntArray
    private val prerelease: String?
    private val build: String?
    final override var friendlyString: String? = null
        private set

    constructor(version: String, storeX: Boolean) {
        var version = version
        val buildDelimPos = version.indexOf('+')

        if (buildDelimPos >= 0) {
            build = version.substring(buildDelimPos + 1)
            version = version.substring(0, buildDelimPos)
        } else {
            build = null
        }

        val dashDelimPos = version.indexOf('-')

        if (dashDelimPos >= 0) {
            prerelease = version.substring(dashDelimPos + 1)
            version = version.substring(0, dashDelimPos)
        } else {
            prerelease = null
        }

        if (prerelease != null && !DOT_SEPARATED_ID.matcher(prerelease).matches()) {
            throw VersionParsingException("Invalid prerelease string '$prerelease'!")
        }

        if (version.endsWith(".")) {
            throw VersionParsingException("Negative version number component found!")
        } else if (version.startsWith(".")) {
            throw VersionParsingException("Missing version component!")
        }

        val componentStrings = version.split("\\.".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()

        if (componentStrings.size < 1) {
            throw VersionParsingException("Did not provide version numbers!")
        }

        var components = IntArray(componentStrings.size)
        var firstWildcardIdx = -1

        for (i in componentStrings.indices) {
            val compStr = componentStrings[i]

            if (storeX) {
                if (compStr == "x" || compStr == "X" || compStr == "*") {
                    if (prerelease != null) {
                        throw VersionParsingException("Pre-release versions are not allowed to use X-ranges!")
                    }

                    components[i] = SemanticVersion.COMPONENT_WILDCARD
                    if (firstWildcardIdx < 0) firstWildcardIdx = i
                    continue
                } else if (i > 0 && components[i - 1] == SemanticVersion.COMPONENT_WILDCARD) {
                    throw VersionParsingException("Interjacent wildcard (1.x.2) are disallowed!")
                }
            }

            if (compStr.trim { it <= ' ' }.isEmpty()) {
                throw VersionParsingException("Missing version number component!")
            }

            try {
                components[i] = compStr.toInt()

                if (components[i] < 0) {
                    throw VersionParsingException("Negative version number component '$compStr'!")
                }
            } catch (e: NumberFormatException) {
                throw VersionParsingException("Could not parse version number component '$compStr'!", e)
            }
        }

        if (storeX && components.size == 1 && components[0] == SemanticVersion.COMPONENT_WILDCARD) {
            throw VersionParsingException("Versions of form 'x' or 'X' not allowed!")
        }

        // strip extra wildcards (1.x.x -> 1.x)
        if (firstWildcardIdx > 0 && components.size > firstWildcardIdx + 1) {
            components = components.copyOf(firstWildcardIdx + 1)
        }

        this.components = components

        buildFriendlyName()
    }

    constructor(components: IntArray, prerelease: String?, build: String?) {
        require(!(components.size == 0 || components[0] == SemanticVersion.COMPONENT_WILDCARD)) { "Invalid components: " + components.contentToString() }

        this.components = components
        this.prerelease = prerelease
        this.build = build

        buildFriendlyName()
    }

    private fun buildFriendlyName() {
        val fnBuilder = StringBuilder()
        var first = true

        for (i in components) {
            if (first) {
                first = false
            } else {
                fnBuilder.append('.')
            }

            if (i == SemanticVersion.COMPONENT_WILDCARD) {
                fnBuilder.append('x')
            } else {
                fnBuilder.append(i)
            }
        }

        if (prerelease != null) {
            fnBuilder.append('-').append(prerelease)
        }

        if (build != null) {
            fnBuilder.append('+').append(build)
        }

        friendlyString = fnBuilder.toString()
    }

    override val versionComponentCount: Int
        get() = components.size

    override fun getVersionComponent(pos: Int): Int {
        return if (pos < 0) {
            throw RuntimeException("Tried to access negative version number component!")
        } else if (pos >= components.size) {
            // Repeat "x" if x-range, otherwise repeat "0".
            if (components[components.size - 1] == SemanticVersion.COMPONENT_WILDCARD) SemanticVersion.COMPONENT_WILDCARD else 0
        } else {
            components[pos]
        }
    }

    val versionComponents: IntArray
        get() = components.clone()

    override val prereleaseKey: Optional<String>
        get() = Optional.ofNullable(prerelease)

    override val buildKey: Optional<String>
        get() = Optional.ofNullable(build)

    override fun equals(o: Any?): Boolean {
        if (o !is SemanticVersionImpl) {
            return false
        } else {
            val other = o

            if (!equalsComponentsExactly(other)) {
                return false
            }

            return prerelease == other.prerelease && build == other.build
        }
    }

    override fun hashCode(): Int {
        return components.contentHashCode() * 73 + (if (prerelease != null) prerelease.hashCode() * 11 else 0) + (build?.hashCode()
            ?: 0)
    }

    override fun toString(): String {
        return friendlyString!!
    }

    override fun hasWildcard(): Boolean {
        for (i in components) {
            if (i < 0) {
                return true
            }
        }

        return false
    }

    fun equalsComponentsExactly(other: SemanticVersionImpl): Boolean {
        for (i in 0..<max(versionComponentCount.toDouble(), other.versionComponentCount.toDouble()).toInt()) {
            if (getVersionComponent(i) != other.getVersionComponent(i)) {
                return false
            }
        }

        return true
    }

    override fun compareTo(other: Version?): Int {
        if (other !is SemanticVersion) {
            return friendlyString!!.compareTo(other?.friendlyString!!)
        }

        val o = other as SemanticVersion

        for (i in 0..<max(versionComponentCount.toDouble(), o.versionComponentCount.toDouble()).toInt()) {
            val first = getVersionComponent(i)
            val second = o.getVersionComponent(i)

            if (first == SemanticVersion.COMPONENT_WILDCARD || second == SemanticVersion.COMPONENT_WILDCARD) {
                continue
            }

            val compare = Integer.compare(first, second)
            if (compare != 0) return compare
        }

        val prereleaseA = prereleaseKey
        val prereleaseB = o.prereleaseKey

        if (prereleaseA.isPresent || prereleaseB!!.isPresent) {
            if (prereleaseA.isPresent && prereleaseB!!.isPresent) {
                val prereleaseATokenizer = StringTokenizer(prereleaseA.get(), ".")
                val prereleaseBTokenizer = StringTokenizer(prereleaseB.get(), ".")

                while (prereleaseATokenizer.hasMoreElements()) {
                    if (prereleaseBTokenizer.hasMoreElements()) {
                        val partA = prereleaseATokenizer.nextToken()
                        val partB = prereleaseBTokenizer.nextToken()

                        if (UNSIGNED_INTEGER.matcher(partA).matches()) {
                            if (UNSIGNED_INTEGER.matcher(partB).matches()) {
                                val compare = Integer.compare(partA.length, partB.length)
                                if (compare != 0) return compare
                            } else {
                                return -1
                            }
                        } else {
                            if (UNSIGNED_INTEGER.matcher(partB).matches()) {
                                return 1
                            }
                        }

                        val compare = partA.compareTo(partB)
                        if (compare != 0) return compare
                    } else {
                        return 1
                    }
                }

                return if (prereleaseBTokenizer.hasMoreElements()) -1 else 0
            } else if (prereleaseA.isPresent) {
                return if (o.hasWildcard()) 0 else -1
            } else { // prereleaseB.isPresent()
                return if (hasWildcard()) 0 else 1
            }
        } else {
            return 0
        }
    }

    companion object {
        private val DOT_SEPARATED_ID: Pattern = Pattern.compile("|[-0-9A-Za-z]+(\\.[-0-9A-Za-z]+)*")
        private val UNSIGNED_INTEGER: Pattern = Pattern.compile("0|[1-9][0-9]*")
    }
}