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

import net.fabricmc.loader.api.metadata.ModDependency

internal class Explanation private constructor(
	@JvmField val error: ErrorKind,
	@JvmField val mod: ModCandidateImpl?,
	@JvmField val dep: ModDependency?,
	val data: String?
) :
    Comparable<Explanation> {
    private val cmpId: Int

    constructor(error: ErrorKind, mod: ModCandidateImpl?) : this(error, mod, null, null)

    constructor(error: ErrorKind, mod: ModCandidateImpl?, dep: ModDependency?) : this(error, mod, dep, null)

    constructor(error: ErrorKind, data: String?) : this(error, null, data)

    constructor(error: ErrorKind, mod: ModCandidateImpl?, data: String?) : this(error, mod, null, data)

    init {
        this.cmpId = nextCmpId++
    }

    override fun compareTo(other: Explanation): Int {
        return Integer.compare(cmpId, other.cmpId)
    }

    override fun toString(): String {
        return if (mod == null) {
            String.format("%s %s", error, data)
        } else if (dep == null) {
            String.format("%s %s", error, mod)
        } else {
            String.format("%s %s %s", error, mod, dep)
        }
    }

    internal enum class ErrorKind(@JvmField val isDependencyError: Boolean) {
        /**
         * Positive hard dependency (depends) from a preselected mod.
         */
        PRESELECT_HARD_DEP(true),

        /**
         * Positive soft dependency (recommends) from a preselected mod.
         */
        PRESELECT_SOFT_DEP(true),

        /**
         * Negative hard dependency (breaks) from a preselected mod.
         */
        PRESELECT_NEG_HARD_DEP(true),

        /**
         * Force loaded due to being preselected.
         */
        PRESELECT_FORCELOAD(false),

        /**
         * Positive hard dependency (depends) from a mod with incompatible preselected candidate.
         */
        HARD_DEP_INCOMPATIBLE_PRESELECTED(true),

        /**
         * Positive hard dependency (depends) from a mod with no matching candidate.
         */
        HARD_DEP_NO_CANDIDATE(true),

        /**
         * Positive hard dependency (depends) from a mod.
         */
        HARD_DEP(true),

        /**
         * Positive soft dependency (recommends) from a mod.
         */
        SOFT_DEP(true),

        /**
         * Negative hard dependency (breaks) from a mod.
         */
        NEG_HARD_DEP(true),

        /**
         * Force loaded if the parent is loaded due to LoadType ALWAYS.
         */
        NESTED_FORCELOAD(false),

        /**
         * Dependency of a nested mod on its parent mods.
         */
        NESTED_REQ_PARENT(false),

        /**
         * Force loaded due to LoadType ALWAYS as a singular root mod.
         */
        ROOT_FORCELOAD_SINGLE(false),

        /**
         * Force loaded due to LoadType ALWAYS and containing root mods.
         */
        ROOT_FORCELOAD(false),

        /**
         * Requirement to load at most one mod per id (including provides).
         */
        UNIQUE_ID(false)
    }

    companion object {
        private var nextCmpId = 0
    }
}
