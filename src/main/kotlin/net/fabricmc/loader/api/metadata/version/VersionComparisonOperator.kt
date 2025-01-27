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
package net.fabricmc.loader.api.metadata.version

import net.fabricmc.loader.api.SemanticVersion
import com.moltenex.loader.api.util.version.Version
import net.fabricmc.loader.impl.util.version.SemanticVersionImpl

enum class VersionComparisonOperator(@JvmField val serialized: String, val isMinInclusive: Boolean, val isMaxInclusive: Boolean) {
    // order is important to match the longest substring (e.g. try >= before >)
    GREATER_EQUAL(">=", true, false) {
        override fun test(a: SemanticVersion, b: SemanticVersion): Boolean {
            return a.compareTo(b as Version) >= 0
        }

        override fun minVersion(version: SemanticVersion?): SemanticVersion? {
            return version
        }
    },
    LESS_EQUAL("<=", false, true) {
        override fun test(a: SemanticVersion, b: SemanticVersion): Boolean {
            return a.compareTo(b as Version) <= 0
        }

        override fun maxVersion(version: SemanticVersion): SemanticVersion? {
            return version
        }
    },
    GREATER(">", false, false) {
        override fun test(a: SemanticVersion, b: SemanticVersion): Boolean {
            return a.compareTo(b as Version) > 0
        }

        override fun minVersion(version: SemanticVersion?): SemanticVersion? {
            return version
        }
    },
    LESS("<", false, false) {
        override fun test(a: SemanticVersion, b: SemanticVersion): Boolean {
            return a.compareTo(b as Version) < 0
        }

        override fun maxVersion(version: SemanticVersion): SemanticVersion? {
            return version
        }
    },
    EQUAL("=", true, true) {
        override fun test(a: SemanticVersion, b: SemanticVersion): Boolean {
            return a.compareTo(b as Version) == 0
        }

        override fun minVersion(version: SemanticVersion?): SemanticVersion? {
            return version
        }

        override fun maxVersion(version: SemanticVersion): SemanticVersion? {
            return version
        }
    },
    SAME_TO_NEXT_MINOR("~", true, false) {
        override fun test(a: SemanticVersion, b: SemanticVersion): Boolean {
            return a.compareTo(b as Version) >= 0 && a.getVersionComponent(0) == b.getVersionComponent(0) && a.getVersionComponent(
                1
            ) == b.getVersionComponent(1)
        }

        override fun minVersion(version: SemanticVersion?): SemanticVersion? {
            return version
        }

        override fun maxVersion(version: SemanticVersion): SemanticVersion {
            return SemanticVersionImpl(
                intArrayOf(version.getVersionComponent(0), version.getVersionComponent(1) + 1),
                "",
                null
            )
        }
    },
    SAME_TO_NEXT_MAJOR("^", true, false) {
        override fun test(a: SemanticVersion, b: SemanticVersion): Boolean {
            return a.compareTo(b as Version) >= 0
                    && a.getVersionComponent(0) == b.getVersionComponent(0)
        }

        override fun minVersion(version: SemanticVersion?): SemanticVersion? {
            return version
        }

        override fun maxVersion(version: SemanticVersion): SemanticVersion {
            return SemanticVersionImpl(intArrayOf(version.getVersionComponent(0) + 1), "", null)
        }
    };

    fun test(a: Version, b: Version): Boolean {
        return if (a is SemanticVersion && b is SemanticVersion) {
            test(a, b)
        } else if (isMinInclusive || isMaxInclusive) {
            a.friendlyString == b.friendlyString
        } else {
            false
        }
    }

    abstract fun test(a: SemanticVersion, b: SemanticVersion): Boolean

    open fun minVersion(version: SemanticVersion?): SemanticVersion? {
        return null
    }

    open fun maxVersion(version: SemanticVersion): SemanticVersion? {
        return null
    }
}
