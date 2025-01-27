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

import net.fabricmc.api.EnvType
import net.fabricmc.loader.api.metadata.ModDependency
import net.fabricmc.loader.impl.discovery.ModPrioSorter.sort
import net.fabricmc.loader.impl.discovery.ModSolver.AddModVar
import net.fabricmc.loader.impl.discovery.ModSolver.InactiveReason
import net.fabricmc.loader.impl.discovery.ModSolver.solve
import net.fabricmc.loader.impl.discovery.ResultAnalyzer.gatherErrors
import net.fabricmc.loader.impl.discovery.ResultAnalyzer.gatherWarnings
import net.fabricmc.loader.impl.metadata.ModDependencyImpl
import net.fabricmc.loader.impl.util.log.Log
import net.fabricmc.loader.impl.util.log.Log.debug
import net.fabricmc.loader.impl.util.log.Log.warn
import net.fabricmc.loader.impl.util.log.LogCategory
import org.sat4j.specs.ContradictionException
import org.sat4j.specs.TimeoutException
import java.util.*
import java.util.stream.Collectors


object ModResolver {
    @Throws(ModResolutionException::class)
    fun resolve(
        candidates: Collection<ModCandidateImpl>,
        envType: EnvType,
        envDisabledMods: MutableMap<String, MutableSet<ModCandidateImpl>>
    ): List<ModCandidateImpl> {
        val startTime = System.nanoTime()
        val result = findCompatibleSet(candidates, envType, envDisabledMods)

        val endTime = System.nanoTime()
        debug(LogCategory.RESOLUTION, "Mod resolution time: %.1f ms", (endTime - startTime) * 1e-6)

        return result
    }

    @Throws(ModResolutionException::class)
    private fun findCompatibleSet(
        candidates: Collection<ModCandidateImpl>,
        envType: EnvType,
        envDisabledMods: MutableMap<String, MutableSet<ModCandidateImpl>>
    ): List<ModCandidateImpl> {
        // sort all mods by priority and group by id

        val allModsSorted: MutableList<ModCandidateImpl> = ArrayList(candidates)
        val modsById: MutableMap<String?, MutableList<ModCandidateImpl>> = LinkedHashMap() // linked to ensure consistent execution

        sort(allModsSorted, modsById)

        // soften positive deps from schema 0 or 1 mods on mods that are present but disabled for the current env
        // this is a workaround necessary due to many mods declaring deps that are unsatisfiable in some envs and loader before 0.12x not verifying them properly
        for (mod in allModsSorted) {
            if (mod.metadata.schemaVersion >= 2) continue

            for (dep in mod.metadata.dependencies) {
                if (!dep!!.kind!!.isPositive || dep.kind == ModDependency.Kind.SUGGESTS) continue  // no positive dep or already suggests

                if (dep !is ModDependencyImpl) continue  // can't modify dep kind

                if (modsById.containsKey(dep.modId)) continue  // non-disabled match available


                val disabledMatches = envDisabledMods[dep.modId] ?: continue
                // no disabled id matches


                for (m in disabledMatches) {
                    if (dep.matches(m.version)) { // disabled version match -> remove dep
                        dep.kind = ModDependency.Kind.SUGGESTS
                        break
                    }
                }
            }
        }

        // preselect mods, check for builtin mod collisions
        val preselectedMods: MutableList<ModCandidateImpl> = ArrayList()

        for (mods in modsById.values) {
            val mods = mods.toMutableList()
            var builtinMod: ModCandidateImpl? = null

            for (mod in mods) {
                if (mod.isBuiltin) {
                    builtinMod = mod
                    break
                }
            }

            if (builtinMod == null) continue

            if (mods.size > 1) {
                mods.remove(builtinMod)
                throw ModResolutionException("Mods share ID with builtin mod $builtinMod: $mods")
            }

            preselectedMods.add(builtinMod)
        }

        val selectedMods: MutableMap<String?, ModCandidateImpl?> = HashMap(allModsSorted.size)
        val uniqueSelectedMods: MutableList<ModCandidateImpl> = ArrayList(allModsSorted.size)

        for (mod in preselectedMods) {
            preselectMod(mod, allModsSorted, modsById, selectedMods, uniqueSelectedMods)
        }

        // solve
        val result: ModSolver.Result

        try {
            result = solve(
                allModsSorted, modsById,
                selectedMods, uniqueSelectedMods
            )
        } catch (e: ContradictionException) {
            throw ModResolutionException("Solving failed", e)
        } catch (e: TimeoutException) {
            throw ModResolutionException("Solving failed", e)
        }

        if (!result.success) {
            Log.warn(LogCategory.RESOLUTION, "Mod resolution failed")
            Log.info(LogCategory.RESOLUTION, "Immediate reason: %s%n", result.immediateReason)
            Log.info(LogCategory.RESOLUTION, "Reason: %s%n", result.reason)
            if (envDisabledMods.isNotEmpty()) Log.info(
                LogCategory.RESOLUTION,
                "%s environment disabled: %s%n",
                envType.name,
                envDisabledMods.keys
            )

            if (result.fix == null) {
                Log.info(LogCategory.RESOLUTION, "No fix?")
            } else {
                Log.info(
                    LogCategory.RESOLUTION, "Fix: add %s, remove %s, replace [%s]%n",
                    result.fix.modsToAdd,
                    result.fix.modsToRemove,
                    result.fix.modReplacements.entries.stream()
                        .map<String> { e: Map.Entry<AddModVar?, List<ModCandidateImpl?>?> ->
                            String.format(
                                "%s -> %s",
                                e.value,
                                e.key
                            )
                        }.collect(
                            Collectors.joining(", ")
                        )
                )

                for (mods in envDisabledMods.values) {
                    for (m in mods) {
                        result.fix.inactiveMods.put(m, InactiveReason.WRONG_ENVIRONMENT)
                    }
                }
            }

            throw ModResolutionException(
                "Some of your mods are incompatible with the game or each other! ${gatherErrors(result, selectedMods, modsById, envDisabledMods, envType)}",
            )
        }

        uniqueSelectedMods.sortWith(compareBy { it.id ?: "" })

        // clear cached data and inbound refs for unused mods, set minNestLevel for used non-root mods to max, queue root mods
        val queue: Queue<ModCandidateImpl> = ArrayDeque()

        for (mod in allModsSorted) {
            if (selectedMods[mod.id] == mod) { // mod is selected
                if (!mod.resetMinNestLevel()) { // -> is root
                    queue.add(mod)
                }
            } else {
                mod.clearCachedData()

                for (m in mod.nestedMods) {
                    m.getParentMods().remove(mod)
                }

                for (m in mod.getParentMods()) {
                    m.nestedMods.remove(mod)
                }
            }
        }

        // recompute minNestLevel (may have changed due to parent associations having been dropped by the above step)
        run {
            var mod: ModCandidateImpl
            while ((queue.poll().also { mod = it }) != null) {
                for (child in mod.nestedMods) {
                    if (child.updateMinNestLevel(mod)) {
                        queue.add(child)
                    }
                }
            }
        }

        val warnings = gatherWarnings(
            uniqueSelectedMods, selectedMods,
            envDisabledMods, envType
        )

        if (warnings != null) {
            warn(LogCategory.RESOLUTION, "Warnings were found!%s", warnings)
        }

        return uniqueSelectedMods
    }

    @Throws(ModResolutionException::class)
    fun preselectMod(
        mod: ModCandidateImpl,
        allModsSorted: MutableList<ModCandidateImpl>,
        modsById: MutableMap<String?, MutableList<ModCandidateImpl>>,
        selectedMods: MutableMap<String?, ModCandidateImpl?>,
        uniqueSelectedMods: MutableList<ModCandidateImpl>
    ) {
        selectMod(mod, selectedMods, uniqueSelectedMods)

        allModsSorted.removeAll(modsById.remove(mod.id)!!)

        for (provided in mod.provides!!) {
            allModsSorted.removeAll(modsById.remove(provided)!!)
        }
    }

    @Throws(ModResolutionException::class)
    fun selectMod(
        mod: ModCandidateImpl,
        selectedMods: MutableMap<String?, ModCandidateImpl?>,
        uniqueSelectedMods: MutableList<ModCandidateImpl>
    ) {
        var prev = selectedMods.put(mod.id, mod)
        if (prev != null) throw ModResolutionException("duplicate mod $mod.id")

        for (provided in mod.provides!!) {
            prev = selectedMods.put(provided, mod)
            if (prev != null) throw ModResolutionException("duplicate mod $provided")
        }

        uniqueSelectedMods.add(mod)
    }
}
