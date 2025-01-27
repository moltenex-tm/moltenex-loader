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
package net.fabricmc.loader.impl.metadata

import com.moltenex.loader.api.util.version.Version
import net.fabricmc.loader.api.metadata.ModDependency
import com.moltenex.loader.api.util.version.VersionInterval
import net.fabricmc.loader.api.metadata.version.VersionPredicate

class ModDependencyImpl(
    override var kind: ModDependency.Kind,
    override var modId: String,
    internal val matcherStringList: List<String>
) : ModDependency {

    private val ranges: Collection<VersionPredicate> = VersionPredicate.parse(matcherStringList)

    override fun matches(version: Version?): Boolean {
        return ranges.any { it.test(version) }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ModDependency) return false

        return kind == other.kind &&
                modId == other.modId &&
                ranges == other.getVersionRequirements()
    }

    override fun hashCode(): Int {
        return (kind.ordinal * 31 + modId.hashCode()) * 257 + ranges.hashCode()
    }

    override fun toString(): String {
        return buildString {
            append("{")
            append(kind.key)
            append(' ')
            append(modId)
            append(" @ [")
            matcherStringList.forEachIndexed { index, matcher ->
                if (index > 0) append(" || ")
                append(matcher)
            }
            append("]}")
        }
    }

    override fun getVersionRequirements(): Collection<VersionPredicate> = ranges

    override fun getVersionIntervals(): List<VersionInterval> {
        var ret: List<VersionInterval> = emptyList()
        for (predicate in ranges) {
            ret = VersionInterval.or(ret, predicate.interval!!)
        }
        return ret
    }
}
