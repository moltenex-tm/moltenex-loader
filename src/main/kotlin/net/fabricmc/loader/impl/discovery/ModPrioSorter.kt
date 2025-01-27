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

import com.moltenex.loader.api.util.version.Version
import java.util.*


internal object ModPrioSorter {
    /**
     * Sort the mod candidate list by priority.
     *
     *
     * This is implemented with two sorting passes, first sorting by isRoot/id/version/nesting/parent, then a best
     * effort pass to prioritize mods that have overall newer id:version pairs.
     *
     *
     * The second pass won't prioritize non-root mods over root mods or above a mod with the same main id but a newer
     * version as these cases are deemed deliberately influenced by the end user or mod author. Since there may be
     * multiple id:version pairs the choice can only be best effort, but the SAT solver will ensure all hard constraints
     * are still met later on.
     *
     * @param mods mods to sort
     * @param modsById grouped mods output
     */
    fun sort(mods: MutableList<ModCandidateImpl>, modsById: MutableMap<String?, MutableList<ModCandidateImpl>>) {
        // sort all mods by priority

        mods.sortWith(comparator)

        // group/index all mods by id, gather provided mod ids
        val providedMods: MutableSet<String?> = HashSet()

        for (mod in mods) {
            modsById.computeIfAbsent(mod.id) { ignore: String? -> ArrayList() }.add(mod)

            for (provided in mod.provides!!) {
                modsById.computeIfAbsent(provided) { ignore: String? -> ArrayList() }.add(mod)
                providedMods.add(provided)
            }
        }

        // strip any provided mod ids that don't have any effect (only 1 candidate for the id)
        val it = providedMods.iterator()
        while (it.hasNext()) {
            if (modsById[it.next()]!!.size <= 1) {
                it.remove()
            }
        }

        // handle overlapping mod ids that need higher priority than the standard comparator handles
        // this is implemented through insertion sort which allows for skipping over unrelated mods that aren't properly comparable
        if (providedMods.isEmpty()) return  // no overlapping id mods


        // float overlapping ids up as needed
        var movedPastRoots = false
        var startIdx = 0
        val potentiallyOverlappingIds: MutableSet<String?> = HashSet()

        var i = 0
        val size = mods.size
        while (i < size) {
            val mod = mods[i]
            val id = mod.id

            //System.out.printf("%d: %s%n", i, mod);
            if (!movedPastRoots && !mod.isRoot) { // update start index to avoid mixing root and non-root mods (root always has higher prio)
                movedPastRoots = true
                startIdx = i
            }

            // gather ids for mod that might overlap other mods
            if (providedMods.contains(id)) {
                potentiallyOverlappingIds.add(id)
            }

            if (!mod.provides!!.isEmpty()) {
                for (provId in mod.provides!!) {
                    if (providedMods.contains(provId)) {
                        potentiallyOverlappingIds.add(provId)
                    }
                }
            }

            if (potentiallyOverlappingIds.isEmpty()) {
                i++
                continue
            }

            // search for a suitable mod that overlaps mod but has a lower version
            var earliestIdx = -1

            for (j in i - 1 downTo startIdx) {
                val cmpMod = mods[j]
                val cmpId = cmpMod.id
                if (cmpId == id) break // can't move mod past another mod with the same id since that mod since that mod would have a higher version due to the previous sorting step and thus always has higher prio


                // quick check if it might match
                if (!potentiallyOverlappingIds.contains(cmpId)
                    && (cmpMod.provides!!.isEmpty() || Collections.disjoint(potentiallyOverlappingIds, cmpMod.provides))
                ) {
                    continue
                }

                val cmp = compareOverlappingIds(mod, cmpMod, Int.MAX_VALUE)

                if (cmp < 0) { // mod needs to be after cmpMod, move mod forward
                    //System.out.printf("found candidate for %d at %d (before %s)%n", i, j, cmpMod);
                    earliestIdx = j
                } else if (cmp != Int.MAX_VALUE) { // cmpMod has at least the same prio, don't search past it
                    break
                }
            }

            if (earliestIdx >= 0) {
                //System.out.printf("move %d to %d (before %s)%n", i, earliestIdx, mods.get(earliestIdx));
                mods.removeAt(i)
                mods.add(earliestIdx, mod)
            }

            potentiallyOverlappingIds.clear()
            i++
        }
    }

    private val comparator =
        Comparator<ModCandidateImpl> { a, b -> compare(a, b) }

    private fun compare(a: ModCandidateImpl, b: ModCandidateImpl): Int {
        // descending sort prio (less/earlier is higher prio):
        // root mods first, lower id first, higher version first, less nesting first, parent cmp

        if (a.isRoot) {
            if (!b.isRoot) {
                return -1 // only a is root
            }
        } else if (b.isRoot) {
            return 1 // only b is root
        }

        // sort id asc
        val idCmp = a.id!!.compareTo(b.id!!)
        if (idCmp != 0) return idCmp

        // sort version desc (lower version later)
        val versionCmp = b.version!!.compareTo(a.version)
        if (versionCmp != 0) return versionCmp

        // sort nestLevel asc
        val nestCmp = a.minNestLevel - b.minNestLevel // >0 if nest(a) > nest(b)
        if (nestCmp != 0) return nestCmp

        if (a.isRoot) return 0 // both root


        // find highest priority parent, if it is not shared by both a+b the one that has it is deemed higher prio
        return compareParents(a, b)
    }

    private fun compareParents(a: ModCandidateImpl, b: ModCandidateImpl): Int {
        assert(!a.getParentMods().isEmpty() && !b.getParentMods().isEmpty())

        var minParent: ModCandidateImpl? = null

        for (mod in a.getParentMods()) {
            if (minParent == null || mod != minParent && compare(minParent, mod) > 0) {
                minParent = mod
            }
        }

        checkNotNull(minParent)
        var found = false

        for (mod in b.getParentMods()) {
            if (mod == minParent) { // both a and b have minParent
                found = true
            } else if (compare(minParent, mod) > 0) { // b has a higher prio parent than a
                return 1
            }
        }

        return if (found) 0 else -1 // only a has minParent if !found, so only a has the highest prio parent
    }

    private fun compareOverlappingIds(a: ModCandidateImpl, b: ModCandidateImpl, noMatchResult: Int): Int {
        assert(
            a.id != b.id // should have been handled before
        )

        var ret = 0 // sum of individual normalized pair comparisons, may cancel each other out
        var matched =
            false // whether any ids overlap, for falling back to main id comparison as if there were no provides

        for (provIdA in a.provides!!) { // a-provides vs b
            if (provIdA == b.id) {
                val providedVersionA: Version? = a.version
                ret += Integer.signum(b.version!!.compareTo(providedVersionA))
                matched = true
            }
        }

        for (provIdB in b.provides!!) {
            if (provIdB == a.id) { // a vs b-provides
                val providedVersionB: Version? = b.version
                ret += Integer.signum(providedVersionB!!.compareTo(a.version))
                matched = true

                continue
            }

            for (provIdA in a.provides!!) { // a-provides vs b-provides
                if (provIdB == provIdA) {
                    val providedVersionA: Version? = a.version
                    val providedVersionB: Version? = b.version

                    ret += Integer.signum(providedVersionB!!.compareTo(providedVersionA))
                    matched = true

                    break
                }
            }
        }

        return if (matched) ret else noMatchResult
    }
}
