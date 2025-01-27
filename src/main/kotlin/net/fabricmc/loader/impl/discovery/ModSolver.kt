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

import net.fabricmc.loader.api.SemanticVersion
import com.moltenex.loader.api.util.version.Version
import net.fabricmc.loader.api.metadata.ModDependency
import com.moltenex.loader.api.util.version.VersionInterval
import net.fabricmc.loader.api.metadata.version.VersionPredicate
import net.fabricmc.loader.impl.discovery.Explanation.ErrorKind
import net.fabricmc.loader.impl.util.SystemProperties
import net.fabricmc.loader.impl.util.log.Log.warn
import net.fabricmc.loader.impl.util.log.LogCategory
import net.fabricmc.loader.impl.util.version.SemanticVersionImpl
import net.fabricmc.loader.impl.util.version.VersionPredicateParser.any
import org.sat4j.pb.IPBSolver
import org.sat4j.pb.SolverFactory
import org.sat4j.pb.tools.DependencyHelper
import org.sat4j.pb.tools.INegator
import org.sat4j.pb.tools.WeightedObject
import org.sat4j.specs.ContradictionException
import org.sat4j.specs.TimeoutException
import java.math.BigInteger
import java.util.*
import java.util.function.Function


internal object ModSolver {
    @Throws(ContradictionException::class, TimeoutException::class, ModResolutionException::class)
    fun solve(
        allModsSorted: List<ModCandidateImpl>,
        modsById: Map<String?, List<ModCandidateImpl>>,
        selectedMods: MutableMap<String?, ModCandidateImpl?>,
        uniqueSelectedMods: MutableList<ModCandidateImpl>
    ): Result {
        // build priority index

        val priorities: MutableMap<ModCandidateImpl, Int> = IdentityHashMap(allModsSorted.size)

        for (i in allModsSorted.indices) {
            priorities[allModsSorted[i]] = i
        }

        // create and configure solver
        solverPrepTime = System.nanoTime()

        val solver = SolverFactory.newDefaultOptimizer()

        val timeout = Integer.getInteger(SystemProperties.DEBUG_RESOLUTION_TIMEOUT, 60)
        if (timeout > 0) solver.timeout = timeout // in seconds


        val dependencyHelper = createDepHelper(solver)

        setupSolver(
            allModsSorted, modsById,
            priorities, selectedMods, uniqueSelectedMods,
            false, null, false,
            dependencyHelper
        )

        // solve
        solveTime = System.nanoTime()

        val hasSolution = dependencyHelper.hasASolution()

        // check solution
        solutionFetchTime = System.nanoTime()

        if (hasSolution) {
            val solution = dependencyHelper.aSolution

            solutionAnalyzeTime = System.nanoTime()

            for (obj in solution) {
                if (obj is ModCandidateImpl) {
                    ModResolver.selectMod(obj, selectedMods, uniqueSelectedMods)
                } else {
                    assert(obj is OptionalDepVar)
                }
            }

            dependencyHelper.reset()

            return Result.createSuccess()
        } else { // no solution
            val reason = dependencyHelper.why()

            // gather all failed deps
            val failedDeps = Collections.newSetFromMap(IdentityHashMap<ModDependency, Boolean>())
            val failedExplanations: MutableList<Explanation> = ArrayList()

            computeFailureCausesOptional(
                allModsSorted, modsById,
                priorities, selectedMods, uniqueSelectedMods,
                reason, dependencyHelper,
                failedDeps, failedExplanations
            )

            // find best solution with mod addition/removal
            fixSetupTime = System.nanoTime()

            val fix = computeFix(
                uniqueSelectedMods, allModsSorted, modsById,
                priorities, selectedMods,
                failedDeps, dependencyHelper
            )

            dependencyHelper.reset()

            return Result.createFailure(reason, failedExplanations, fix)
        }
    }

    var solverPrepTime: Long = 0
    var solveTime: Long = 0
    var solutionFetchTime: Long = 0
    var solutionAnalyzeTime: Long = 0
    var fixSetupTime: Long = 0

    @Throws(ContradictionException::class, TimeoutException::class)
    private fun computeFailureCausesOptional(
        allModsSorted: List<ModCandidateImpl>,
        modsById: Map<String?, List<ModCandidateImpl>>,
        priorities: Map<ModCandidateImpl, Int>,
        selectedMods: MutableMap<String?, ModCandidateImpl?>,
        uniqueSelectedMods: List<ModCandidateImpl>,
        reason: Set<Explanation>,
        dependencyHelper: DependencyHelper<DomainObject?, Explanation>,
        failedDeps: MutableSet<ModDependency>,
        failedExplanations: MutableList<Explanation>
    ) {
        var dependencyHelper = dependencyHelper
        dependencyHelper.reset()
        dependencyHelper =
            createDepHelper(dependencyHelper.solver) // dependencyHelper.reset doesn't fully reset the dep helper

        setupSolver(
            allModsSorted, modsById,
            priorities, selectedMods, uniqueSelectedMods,
            true, null, false,
            dependencyHelper
        )

        if (dependencyHelper.hasASolution()) {
            val solution = dependencyHelper.aSolution
            val disabledDeps: MutableSet<ModDependency> =
                HashSet() // DisableDepVar uses equality semantics, not identity

            for (obj in solution) {
                if (obj is DisableDepVar) {
                    disabledDeps.add(obj.dep)
                } else {
                    assert(obj is ModCandidateImpl)
                }
            }

            // populate failedDeps with disabledDeps entries that are actually in use (referenced through non-optional mods)
            // record explanation for failed deps that capture the depending mod
            for (obj in solution) {
                if (obj !is ModCandidateImpl) continue

                val mod = obj

                for (dep in mod.dependencies) {
                    if (disabledDeps.contains(dep)) {
                        assert(dep?.kind === ModDependency.Kind.DEPENDS || dep?.kind === ModDependency.Kind.BREAKS)

                        failedDeps.add(dep!!)
                        failedExplanations.add(
                            Explanation(
                                if (dep.kind === ModDependency.Kind.DEPENDS) ErrorKind.HARD_DEP else ErrorKind.NEG_HARD_DEP,
                                mod,
                                dep
                            )
                        )
                    }
                }
            }
        }
    }

    @Throws(ContradictionException::class, TimeoutException::class)
    private fun computeFix(
        uniqueSelectedMods: List<ModCandidateImpl>,
        allModsSorted: List<ModCandidateImpl>,
        modsById: Map<String?, List<ModCandidateImpl>>,
        priorities: Map<ModCandidateImpl, Int>,
        selectedMods: MutableMap<String?, ModCandidateImpl?>,
        failedDeps: Set<ModDependency>,
        dependencyHelper: DependencyHelper<DomainObject?, Explanation>
    ): Fix? {
        // group positive deps by mod id
        var dependencyHelper = dependencyHelper
        val depsById: MutableMap<String?, MutableSet<Collection<VersionPredicate>>> = HashMap()

        for (dep in failedDeps) {
            if (dep.kind === ModDependency.Kind.DEPENDS) {
                depsById.computeIfAbsent(dep.modId) { ignore: String? -> HashSet<Collection<VersionPredicate>>() }
                    .add(dep.getVersionRequirements() as Collection<VersionPredicate>)
            }
        }

        // add mods with unsatisfied deps as well so they can be replaced (remove+add)
        val modsWithOnlyOutboundDepFailures: MutableSet<String?> = HashSet()

        for (mod in allModsSorted) {
            if (!mod.dependencies.isEmpty() && !depsById.containsKey(mod.id) && !Collections.disjoint(
                    mod.dependencies,
                    failedDeps
                )
            ) { // mod has unsatisfied deps
                depsById.computeIfAbsent(mod.id) { ignore: String? -> HashSet() }.add(setOf(any))
                modsWithOnlyOutboundDepFailures.add(mod.id)
            }
        }

        // add deps that didn't fail to find all relevant boundary versions to test
        for (mod in allModsSorted) {
            for (dep in mod.dependencies) {
                if (dep!!.kind !== ModDependency.Kind.DEPENDS) continue

                val predicates = depsById[dep?.modId]

                if (dep != null) {
                    predicates?.add(dep.getVersionRequirements() as Collection<VersionPredicate>)
                }
            }
        }

        // determine mod versions to try to add
        val installableMods: MutableMap<String?, MutableList<AddModVar?>> = HashMap()

        for ((id, value) in depsById) {
            val hadOnlyOutboundDepFailures = modsWithOnlyOutboundDepFailures.contains(id)

            // extract all version bounds (resulting version needs to be part of one of them)
            val allIntervals: MutableSet<VersionInterval> = HashSet()

            for (versionPredicates in value) {
                var intervals = emptyList<VersionInterval>()

                for (v in versionPredicates) {
                    intervals = VersionInterval.or(intervals, v.interval!!)
                }

                allIntervals.addAll(intervals)
            }

            if (allIntervals.isEmpty()) continue

            // try to determine common version bounds, alternatively approximate (imprecise due to not knowing the real versions or which deps are really essential)
            var commonInterval: VersionInterval? = null
            var commonVersionInitialized = false
            val versions: MutableSet<Version> = HashSet()

            for (interval in allIntervals) {
                if (commonInterval == null) {
                    if (!commonVersionInitialized) { // initialize to first range, otherwise leave as empty range
                        commonInterval = interval
                        commonVersionInitialized = true
                    }
                } else {
                    commonInterval = interval.and(commonInterval)
                }

                versions.add(deriveVersion(interval))
            }

            val out = installableMods.computeIfAbsent(id) { ignore: String? -> ArrayList() }

            if (commonInterval != null) {
                out.add(AddModVar(id, deriveVersion(commonInterval), hadOnlyOutboundDepFailures))
            } else {
                for (version in versions) {
                    out.add(AddModVar(id, version, hadOnlyOutboundDepFailures))
                }
            }

            out.sortWith(Comparator.comparing { obj: AddModVar -> obj.version }
                .reversed())
        }

        // check the determined solution
        fixSolveTime = System.nanoTime()

        dependencyHelper.reset()
        dependencyHelper =
            createDepHelper(dependencyHelper.solver) // dependencyHelper.reset doesn't fully reset the dep helper

        setupSolver(
            allModsSorted, modsById,
            priorities, selectedMods, uniqueSelectedMods,
            false, installableMods, true,
            dependencyHelper
        )

        if (!dependencyHelper.hasASolution()) {
            warn(
                LogCategory.RESOLUTION,
                "Unable to find a solution to fix the mod set, reason: %s",
                dependencyHelper.why()
            )
            return null
        }

        val activeMods: MutableMap<String?, ModCandidateImpl> = HashMap()
        val inactiveMods: MutableMap<ModCandidateImpl, InactiveReason> = IdentityHashMap(allModsSorted.size)
        val modsToAdd: MutableList<AddModVar> = ArrayList()
        val modsToRemove: MutableList<ModCandidateImpl> = ArrayList()
        val modReplacements: MutableMap<AddModVar, List<ModCandidateImpl>> = HashMap()

        for (mod in allModsSorted) {
            inactiveMods[mod] = InactiveReason.UNKNOWN
        }

        for (obj in dependencyHelper.aSolution) {
            if (obj is ModCandidateImpl) {
                val mod = obj

                activeMods[mod.id] = mod
                inactiveMods.remove(mod)
            } else if (obj is AddModVar) {
                val mod = obj
                val replaced: MutableList<ModCandidateImpl> = ArrayList()

                val selectedMod = selectedMods[obj.id]
                if (selectedMod != null) replaced.add(selectedMod)

                val mods = modsById[obj.id]
                if (mods != null) replaced.addAll(mods)

                if (replaced.isEmpty()) {
                    modsToAdd.add(mod)
                } else { // same id as mods picked previously -> replacement
                    modReplacements[mod] = replaced

                    for (m in replaced) {
                        inactiveMods[m] = InactiveReason.TO_REPLACE
                    }
                }
            } else if (obj is RemoveModVar) {
                var found = false

                val mod = selectedMods[obj.id]

                if (mod != null) {
                    modsToRemove.add(mod)
                    inactiveMods[mod] = InactiveReason.TO_REMOVE
                    found = true
                }

                val mods = modsById[obj.id]

                if (mods != null) {
                    for (m in mods) {
                        if (m.isRoot) {
                            modsToRemove.add(m)
                            inactiveMods[m] = InactiveReason.TO_REMOVE
                            found = true
                        }
                    }
                }

                assert(found)
            } else { // unexpected domainobj kind
                assert(false) { obj!! }
            }
        }

        // compute version intervals compatible with the active mod set for all mods to add
        for (mods in listOf<Collection<AddModVar>>(modsToAdd, modReplacements.keys)) {
            for (mod in mods) {
                var intervals: List<VersionInterval>? = listOf(VersionInterval.INFINITE)

                for (m in activeMods.values) {
                    for (dep in m.dependencies) {
                        if (dep != null) {
                            if (!dep.modId.equals(mod.id) || dep.kind!!.isSoft) continue
                        }

                        if (dep != null) {
                            if (dep.kind!!.isPositive) {
                                intervals = VersionInterval.and(intervals, dep.getVersionIntervals())
                            } else {
                                intervals = VersionInterval.and(intervals, VersionInterval.not(dep.getVersionIntervals()))
                            }
                        }
                    }
                }

                mod.versionIntervals = intervals
            }
        }

        // compute reasons for mods to be inactive
        inactiveModLoop@ for (entry in inactiveMods.entries) {
            if (entry.value != InactiveReason.UNKNOWN) continue

            val mod = entry.key
            val active = activeMods[mod.id]

            if (active != null) {
                if (allModsSorted.indexOf(mod) > allModsSorted.indexOf(active)) { // entry has lower prio (=higher index) than active
                    if (mod.version == active.version) {
                        entry.setValue(InactiveReason.SAME_ACTIVE)
                    } else {
                        assert(mod.version!!.compareTo(active.version) < 0)
                        entry.setValue(InactiveReason.NEWER_ACTIVE)
                    }
                } else {
                    entry.setValue(InactiveReason.INCOMPATIBLE)
                }

                continue@inactiveModLoop
            }

            if (!mod.getParentMods().isEmpty()) {
                var found = false

                for (m in mod.getParentMods()) {
                    if (activeMods[m.id] == m) {
                        found = true
                        break
                    }
                }

                if (!found) {
                    entry.setValue(InactiveReason.INACTIVE_PARENT)
                    continue@inactiveModLoop
                }
            }
        }

        // TODO: test if the solution is actually valid?
        return Fix(modsToAdd, modsToRemove, modReplacements, activeMods, inactiveMods)
    }

    var fixSolveTime: Long = 0

    private fun deriveVersion(interval: VersionInterval): Version {
        if (!interval.isSemantic) {
            return if (interval.min != null) interval.min!! else interval.max!!
        }

        var v = interval.min as SemanticVersion

        if (!interval.isMinInclusive) { // not inclusive, increment slightly
            var pr = v.prereleaseKey!!.orElse(null)
            var comps = (v as SemanticVersionImpl).versionComponents

            if (pr != null) { // has prerelease, add to increase
                pr = if (pr.isEmpty()) "0" else ("$pr.0")
            } else { // regular version only, increment patch and make least prerelease
                if (comps.size < 3) {
                    comps = comps.copyOf(comps.size + 1)
                }

                comps[comps.size - 1]++
                pr = ""
            }

            v = SemanticVersionImpl(comps, pr, null)
        }

        return v
    }

    @Throws(ContradictionException::class)
    private fun setupSolver(
        allModsSorted: List<ModCandidateImpl>,
        modsById: Map<String?, List<ModCandidateImpl?>>,
        priorities: Map<ModCandidateImpl, Int>,
        selectedMods: MutableMap<String?, ModCandidateImpl?>,
        uniqueSelectedMods: List<ModCandidateImpl>,
        depDisableSim: Boolean,
        installableMods: Map<String?, MutableList<AddModVar?>>?,
        removalSim: Boolean,
        dependencyHelper: DependencyHelper<DomainObject?, Explanation>
    ) {
        val dummies: Map<String?, DomainObject> = HashMap()
        val disabledDeps: HashMap<ModDependency, Map.Entry<DomainObject, Int>> = HashMap()
        val weightedObjects: MutableList<WeightedObject<DomainObject?>?> = ArrayList()

        generatePreselectConstraints(
            uniqueSelectedMods, modsById,
            priorities, selectedMods,
            depDisableSim, installableMods, removalSim,
            dummies, disabledDeps,
            dependencyHelper, weightedObjects
        )

        generateMainConstraints(
            allModsSorted, modsById,
            priorities, selectedMods,
            depDisableSim, installableMods, removalSim,
            dummies, disabledDeps,
            dependencyHelper, weightedObjects
        )

        if (depDisableSim) {
            applyDisableDepVarWeights(disabledDeps, priorities.size, weightedObjects)
        }

        val weights = weightedObjects
            .map { it as WeightedObject<DomainObject?> } // Handle potential nullability
            .toTypedArray()

        dependencyHelper.setObjectiveFunction(*weights)
    }

    @Throws(ContradictionException::class)
    private fun generatePreselectConstraints(
        uniqueSelectedMods: List<ModCandidateImpl>,
        modsById: Map<String?, List<ModCandidateImpl?>>,
        priorities: Map<ModCandidateImpl, Int>,
        selectedMods: MutableMap<String?, ModCandidateImpl?>,
        depDisableSim: Boolean,
        installableMods: Map<String?, MutableList<AddModVar?>>?,
        removalSim: Boolean,
        dummyMods: Map<String?, DomainObject>,
        disabledDeps: HashMap<ModDependency, Map.Entry<DomainObject, Int>>,
        dependencyHelper: DependencyHelper<DomainObject?, Explanation>,
        weightedObjects: MutableList<WeightedObject<DomainObject?>?>
    ) {
        val enableOptional =
            !depDisableSim && installableMods == null && !removalSim // whether to enable optional mods (regular solve only, not for failure handling)
        val suitableMods: MutableList<DomainObject?> = ArrayList()

        for (mod in uniqueSelectedMods) {
            // add constraints for dependencies (skips deps that are already preselected outside depDisableSim)

            for (dep in mod.dependencies) {
                if (dep != null) {
                    if (!enableOptional && dep.kind!!.isSoft) continue
                }
                if (dep != null) {
                    if (selectedMods.containsKey(dep.modId)) continue
                }

                var availableMods: List<DomainObject.Mod?>? =
                    modsById[dep?.modId]

                if (availableMods != null) {
                    for (m in availableMods) {
                        if (m != null) {
                            if (dep != null) {
                                if (dep.matches(m.version)) suitableMods.add(m)
                            }
                        }
                    }
                }

                if (installableMods != null) {
                    if (dep != null) {
                        availableMods = installableMods[dep.modId]
                    }

                    if (availableMods != null) {
                        for (m in availableMods) {
                            if (dep != null) {
                                if (m != null) {
                                    if (dep.matches(m.version)) suitableMods.add(m)
                                }
                            }
                        }
                    }
                }

                if (suitableMods.isEmpty() && !depDisableSim) continue

                if (dep != null) {
                    when (dep.kind) {
                        net.fabricmc.loader.api.metadata.ModDependency.Kind.DEPENDS -> {
                            if (depDisableSim) {
                                suitableMods.add(getCreateDisableDepVar(dep, disabledDeps))
                            }

                            dependencyHelper.clause(
                                Explanation(ErrorKind.PRESELECT_HARD_DEP, mod, dep),
                                *suitableMods.toTypedArray<DomainObject?>()
                            )
                        }

                        net.fabricmc.loader.api.metadata.ModDependency.Kind.RECOMMENDS -> {
                            // this will prioritize greedy over non-greedy loaded mods, regardless of modPrioComparator due to the objective weights

                            // only pull IF_RECOMMENDED or encompassing in
                            suitableMods.removeIf { m: DomainObject? -> (m as ModCandidateImpl).loadCondition.ordinal > ModLoadCondition.IF_RECOMMENDED.ordinal } as (DomainObject?) -> Boolean as (DomainObject?) -> Boolean

                            if (!suitableMods.isEmpty()) {
                                suitableMods.add(
                                    getCreateDummy(
                                        dep.modId,
                                        { id: String? -> OptionalDepVar(id) }, dummyMods, priorities.size, weightedObjects
                                    )
                                )
                                dependencyHelper.clause(
                                    Explanation(ErrorKind.PRESELECT_SOFT_DEP, mod, dep),
                                    *suitableMods.toTypedArray<DomainObject?>()
                                )
                            }
                        }

                        net.fabricmc.loader.api.metadata.ModDependency.Kind.BREAKS -> if (depDisableSim) {
                            dependencyHelper.setTrue(
                                getCreateDisableDepVar(dep, disabledDeps),
                                Explanation(ErrorKind.PRESELECT_NEG_HARD_DEP, mod, dep)
                            )
                        } else {
                            for (match in suitableMods) {
                                dependencyHelper.setFalse(match, Explanation(ErrorKind.PRESELECT_NEG_HARD_DEP, mod, dep))
                            }
                        }

                        net.fabricmc.loader.api.metadata.ModDependency.Kind.CONFLICTS -> {}
                        else -> {}
                    }
                }

                suitableMods.clear()
            }

            if (removalSim) {
                var prio = priorities.size + 10

                if (installableMods != null) {
                    prio += installableMods.getOrDefault(mod.id, emptyList<AddModVar>()).size

                    val installable: List<AddModVar?>? = installableMods[mod.id]
                    if (installable != null) suitableMods.addAll(installable)
                }

                suitableMods.add(
                    getCreateDummy(
                        mod.id,
                        { id: String? -> RemoveModVar(id) }, dummyMods, prio, weightedObjects
                    )
                )
                suitableMods.add(mod)

                dependencyHelper.clause(
                    Explanation(ErrorKind.PRESELECT_FORCELOAD, mod.id),
                    *suitableMods.toTypedArray<DomainObject?>()
                )
                suitableMods.clear()
            }
        }
    }

    @Throws(ContradictionException::class)
    private fun generateMainConstraints(
        allModsSorted: List<ModCandidateImpl>,
        modsById: Map<String?, List<ModCandidateImpl?>>,
        priorities: Map<ModCandidateImpl, Int>,
        selectedMods: MutableMap<String?, ModCandidateImpl?>,
        depDisableSim: Boolean,
        installableMods: Map<String?, MutableList<AddModVar?>>?,
        removalSim: Boolean,
        dummyMods: Map<String?, DomainObject>,
        disabledDeps: HashMap<ModDependency, Map.Entry<DomainObject, Int>>?,
        dependencyHelper: DependencyHelper<DomainObject?, Explanation>,
        weightedObjects: MutableList<WeightedObject<DomainObject?>?>
    ) {
        val enableOptional =
            !depDisableSim && installableMods == null && !removalSim // whether to enable optional mods (regular solve only, not for failure handling)
        val suitableMods: MutableList<DomainObject?> = ArrayList()

        for (mod in allModsSorted) {
            // add constraints for dependencies

            for (dep in mod.dependencies) {
                if (!enableOptional && dep!!.kind!!.isSoft) continue

                val selectedMod = selectedMods[dep!!.modId]

                if (selectedMod != null) { // dep is already selected = present
                    if (!removalSim) {
                        if (!dep.kind!!.isSoft // .. and is a hard dep
                                && dep.matches(selectedMod.version) != dep.kind!!.isPositive
                            ) { // ..but isn't suitable (DEPENDS without match or BREAKS with match)
                                if (depDisableSim) {
                                    dependencyHelper.setTrue(
                                        getCreateDisableDepVar(dep, disabledDeps!!),
                                        Explanation(ErrorKind.HARD_DEP, mod, dep)
                                    )
                                } else {
                                    dependencyHelper.setFalse(
                                        mod,
                                        Explanation(ErrorKind.HARD_DEP_INCOMPATIBLE_PRESELECTED, mod, dep)
                                    )
                                }
                            }

                        continue
                    } else if (dep.matches(selectedMod.version)) {
                        suitableMods.add(selectedMod)
                    }
                }

                var availableMods: List<DomainObject.Mod?>? =
                    modsById[dep.modId]

                if (availableMods != null) {
                    for (m in availableMods) {
                        if (dep.matches(m!!.version)) suitableMods.add(m)
                    }
                }

                if (installableMods != null) {
                    availableMods = installableMods[dep.modId]

                    if (availableMods != null) {
                        for (m in availableMods) {
                            if (dep.matches(m!!.version)) suitableMods.add(m)
                        }
                    }
                }

                when (dep.kind) {
                    net.fabricmc.loader.api.metadata.ModDependency.Kind.DEPENDS -> {
                        if (depDisableSim) {
                            suitableMods.add(getCreateDisableDepVar(dep, disabledDeps!!))
                        }

                        if (suitableMods.isEmpty()) {
                            dependencyHelper.setFalse(mod, Explanation(ErrorKind.HARD_DEP_NO_CANDIDATE, mod, dep))
                        } else {
                            dependencyHelper.implication(mod).implies(*suitableMods.toTypedArray<DomainObject?>())
                                .named(Explanation(ErrorKind.HARD_DEP, mod, dep))
                        }
                    }

                    net.fabricmc.loader.api.metadata.ModDependency.Kind.RECOMMENDS -> {
                        // this will prioritize greedy over non-greedy loaded mods, regardless of modPrioComparator due to the objective weights

                        // only pull IF_RECOMMENDED or encompassing in
                        suitableMods.removeIf { m: DomainObject? -> (m as ModCandidateImpl).loadCondition.ordinal> ModLoadCondition.IF_RECOMMENDED.ordinal }

                        if (!suitableMods.isEmpty()) {
                            suitableMods.add(
                                getCreateDummy(
                                    dep.modId,
                                    { id: String? -> OptionalDepVar(id) }, dummyMods, priorities.size, weightedObjects
                                )
                            )
                            dependencyHelper.implication(mod).implies(*suitableMods.toTypedArray<DomainObject?>())
                                .named(Explanation(ErrorKind.SOFT_DEP, mod, dep))
                        }
                    }

                    net.fabricmc.loader.api.metadata.ModDependency.Kind.BREAKS -> if (!suitableMods.isEmpty()) {
                        if (depDisableSim) {
                            val `var` = getCreateDisableDepVar(dep, disabledDeps!!)

                            for (match in suitableMods) {
                                dependencyHelper.implication(mod).implies(NegatedDomainObject(match!!), `var`)
                                    .named(Explanation(ErrorKind.NEG_HARD_DEP, mod, dep))
                            }
                        } else {
                            for (match in suitableMods) {
                                dependencyHelper.implication(mod).impliesNot(match)
                                    .named(Explanation(ErrorKind.NEG_HARD_DEP, mod, dep))
                            }
                        }
                    }

                    net.fabricmc.loader.api.metadata.ModDependency.Kind.CONFLICTS -> {}
                    else -> {}
                }

                suitableMods.clear()
            }

            // add constraints to select greedy nested mods (ALWAYS or IF_POSSIBLE)
            // add constraints to restrict nested mods to selected parents
            if (!mod.isRoot) { // nested mod
                val loadCondition = mod.loadCondition

                if (loadCondition == ModLoadCondition.ALWAYS) { // required with parent
                    val explanation = Explanation(
                        ErrorKind.NESTED_FORCELOAD,
                        mod.getParentMods().iterator().next(),
                        mod.id
                    ) // FIXME: this applies to all parents
                    val siblings = modsById[mod.id]!!.toTypedArray<DomainObject?>()

                    if (isAnyParentSelected(mod, selectedMods)) {
                        dependencyHelper.clause(explanation, *siblings)
                    } else {
                        for (parent in mod.getParentMods()) {
                            dependencyHelper.implication(parent).implies(*siblings).named(explanation)
                        }
                    }
                }

                // require parent to be selected with the nested mod
                if (!isAnyParentSelected(mod, selectedMods)) {
                    dependencyHelper.implication(mod).implies(*mod.getParentMods().toTypedArray<DomainObject>())
                        .named(Explanation(ErrorKind.NESTED_REQ_PARENT, mod))
                }
            }

            // add weights if potentially needed (choice between multiple mods or dummies)
            if (!mod.isRoot || mod.loadCondition != ModLoadCondition.ALWAYS || modsById[mod.id]!!.size > 1) {
                val prio = priorities[mod]!!

                val weight =
                    if (mod.loadCondition.ordinal >= ModLoadCondition.IF_RECOMMENDED.ordinal) { // non-greedy (optional)
                        TWO.pow(prio + 1)
                    } else { // greedy
                        TWO.pow(allModsSorted.size - prio).negate()
                    }

                weightedObjects.add(WeightedObject.newWO(mod, weight))
            }
        }

        // add constraints to force-load root mods (ALWAYS only, IF_POSSIBLE is being handled through negative weight later)
        // add single mod per id constraints
        for (variants in modsById.values) {
            val firstMod = variants[0]
            val id = firstMod!!.id

            // force-load root mod
            if (variants.size == 1 && !removalSim) { // trivial case, others are handled by multi-variant impl
                if (firstMod.isRoot && firstMod.loadCondition == ModLoadCondition.ALWAYS) {
                    dependencyHelper.setTrue(firstMod, Explanation(ErrorKind.ROOT_FORCELOAD_SINGLE, firstMod))
                }
            } else { // complex case, potentially multiple variants
                var isRequired = false

                for (mod in variants) {
                    if (mod!!.isRoot && mod.loadCondition == ModLoadCondition.ALWAYS) {
                        isRequired = true
                        break
                    }
                }

                if (isRequired) {
                    if (removalSim) {
                        var prio = priorities.size + 10
                        if (installableMods != null) prio += installableMods.getOrDefault(
                            id,
                            emptyList<AddModVar>()
                        ).size

                        suitableMods.add(
                            getCreateDummy(
                                id,
                                { id: String? -> RemoveModVar(id) }, dummyMods, prio, weightedObjects
                            )
                        )
                    }

                    if (installableMods != null) {
                        val installable: List<AddModVar?>? = installableMods[id]
                        if (installable != null) suitableMods.addAll(installable)
                    }

                    suitableMods.addAll(variants)

                    dependencyHelper.clause(
                        Explanation(ErrorKind.ROOT_FORCELOAD, id),
                        *suitableMods.toTypedArray<DomainObject?>()
                    )
                    suitableMods.clear()
                }
            }

            // single mod per id constraint
            suitableMods.addAll(variants)

            if (installableMods != null) {
                val installable: List<AddModVar?>? = installableMods[id]

                if (installable != null && !installable.isEmpty()) {
                    suitableMods.addAll(installable)

                    val mod = selectedMods[id]
                    if (mod != null) suitableMods.add(mod)
                }
            }

            if (suitableMods.size > 1 // multiple options
                || enableOptional && firstMod.loadCondition == ModLoadCondition.IF_POSSIBLE
            ) { // optional greedy loading
                dependencyHelper.atMost(1, *suitableMods.toTypedArray<DomainObject?>())
                    .named(Explanation(ErrorKind.UNIQUE_ID, id))
            }

            suitableMods.clear()
        }

        // add weights and missing unique id constraints for installable mods
        if (installableMods != null) {
            for (variants in installableMods.values) {
                val id = variants[0]!!.id
                val isReplacement = modsById.containsKey(id)

                if (!isReplacement) { // no single mod per id constraint created yet
                    suitableMods.addAll(variants)

                    val selectedMod = selectedMods[id]
                    if (selectedMod != null) suitableMods.add(selectedMod)

                    if (suitableMods.size > 1) {
                        dependencyHelper.atMost(1, *suitableMods.toTypedArray<DomainObject?>())
                            .named(Explanation(ErrorKind.UNIQUE_ID, id))
                    }

                    suitableMods.clear()
                }

                for (i in variants.indices) {
                    val mod = variants[i]
                    var weight = priorities.size + 4 + i
                    if (isReplacement) weight += 3
                    if (mod!!.hadOnlyOutboundDepFailures) weight++

                    weightedObjects.add(WeightedObject.newWO(mod, TWO.pow(weight)))
                }
            }
        }
    }

    private val TWO: BigInteger = BigInteger.valueOf(2)

    private fun createDepHelper(solver: IPBSolver): DependencyHelper<DomainObject?, Explanation> {
        val ret = DependencyHelper<DomainObject?, Explanation>(solver) // new LexicoHelper<>(solver)
        ret.setNegator(negator)

        return ret
    }

    private fun getCreateDummy(
        id: String?,
        supplier: Function<String?, DomainObject>,
        duplicateMap: Map<String?, DomainObject>,
        modCount: Int,
        weightedObjects: MutableList<WeightedObject<DomainObject?>?>
    ): DomainObject? {
        var ret = duplicateMap[id]
        if (ret != null) return ret

        ret = supplier.apply(id)
        val weight = modCount + 2
        weightedObjects.add(WeightedObject.newWO(ret, TWO.pow(weight)))

        return ret
    }

    private fun getCreateDisableDepVar(
        dep: ModDependency,
        duplicateMap: HashMap<ModDependency, Map.Entry<DomainObject, Int>>
    ): DomainObject {
        val entry = duplicateMap.computeIfAbsent(dep) { d: ModDependency -> AbstractMap.SimpleEntry(DisableDepVar(d), 0) }

        if (entry is AbstractMap.SimpleEntry) {
            entry.setValue(entry.value + 1)
        }

        return entry.key
    }

    private fun applyDisableDepVarWeights(
        map: HashMap<ModDependency, Map.Entry<DomainObject, Int>>?,
        modCount: Int,
        weightedObjects: MutableList<WeightedObject<DomainObject?>?>
    ) {
        val baseWeight = TWO.pow(modCount + 3)

        for ((key, count) in map!!.values) {
            weightedObjects.add(
                WeightedObject.newWO(
                    key,
                    if (count > 1) baseWeight.multiply(BigInteger.valueOf(count.toLong())) else baseWeight
                )
            )
        }
    }

    private val negator: INegator = object : INegator {
        override fun unNegate(thing: Any): Any {
            return (thing as NegatedDomainObject).obj
        }

        override fun isNegated(thing: Any): Boolean {
            return thing is NegatedDomainObject
        }
    }

    fun isAnyParentSelected(mod: ModCandidateImpl, selectedMods: MutableMap<String?, ModCandidateImpl?>): Boolean {
        for (parentMod in mod.getParentMods()) {
            if (selectedMods[parentMod.id] == parentMod) return true
        }

        return false
    }

    fun hasAllDepsSatisfied(mod: ModCandidateImpl, mods: Map<String?, ModCandidateImpl?>): Boolean {
        for (dep in mod.dependencies) {
            if (dep!!.kind === ModDependency.Kind.DEPENDS) {
                val m = mods[dep!!.modId]
                if (m == null || !dep.matches(m.version)) return false
            } else if (dep!!.kind === ModDependency.Kind.BREAKS) {
                val m = mods[dep!!.modId]
                if (m != null && dep.matches(m.version)) return false
            }
        }

        return true
    }

    internal class Result private constructor(
		@JvmField val success: Boolean,
		@JvmField val immediateReason: Collection<Explanation>?,
		@JvmField val reason: Collection<Explanation>?, // may be null
		@JvmField val fix: Fix?
    ) {
        companion object {
            fun createSuccess(): Result {
                return Result(true, null, null, null)
            }

            fun createFailure(
                immediateReason: Collection<Explanation>?,
                reason: Collection<Explanation>?,
                fix: Fix?
            ): Result {
                return Result(false, immediateReason, reason, fix)
            }
        }
    }

    internal class Fix(
		val modsToAdd: Collection<AddModVar>,
        val modsToRemove: Collection<ModCandidateImpl>,
		val modReplacements: Map<AddModVar, List<ModCandidateImpl>>,
		val activeMods: MutableMap<String?, ModCandidateImpl>,
        val inactiveMods: MutableMap<ModCandidateImpl, InactiveReason>
    )

    internal enum class InactiveReason(val id: String) {
        INACTIVE_PARENT("inactive_parent"),
        INCOMPATIBLE("incompatible"),
        NEWER_ACTIVE("newer_active"),
        SAME_ACTIVE("same_active"),
        TO_REMOVE("to_remove"),
        TO_REPLACE("to_replace"),
        UNKNOWN("unknown"),
        WRONG_ENVIRONMENT("wrong_environment")
    }

    private class OptionalDepVar(override val id: String?) : DomainObject {
        override fun toString(): String {
            return "optionalDep:$id"
        }
    }

    private class DisableDepVar(val dep: ModDependency) : DomainObject {
        override val id: String?
            get() = dep.modId

        override fun toString(): String {
            return "disableDep:$dep"
        }
    }

    internal class AddModVar(
        override val id: String?,
        override val version: Version,
        val hadOnlyOutboundDepFailures: Boolean
    ) :
        DomainObject.Mod {
        var versionIntervals: List<VersionInterval>? = null

        override fun toString(): String {
            return String.format("add:%s %s (%s)", id, version, versionIntervals)
        }
    }

    private class RemoveModVar(override val id: String?) : DomainObject {
        override fun toString(): String {
            return "remove:$id"
        }
    }

    private class NegatedDomainObject(val obj: DomainObject) : DomainObject {
        override val id: String?
            get() = obj.id

        override fun toString(): String {
            return "!$obj"
        }
    }
}
