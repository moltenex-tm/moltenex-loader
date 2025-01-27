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
import net.fabricmc.loader.api.SemanticVersion
import net.fabricmc.loader.api.metadata.ModDependency
import com.moltenex.loader.api.util.version.VersionInterval
import net.fabricmc.loader.impl.discovery.ModSolver.InactiveReason
import net.fabricmc.loader.impl.metadata.AbstractModMetadata
import net.fabricmc.loader.impl.util.Localization.format
import net.fabricmc.loader.impl.util.StringUtil.capitalize
import net.fabricmc.loader.impl.util.version.VersionIntervalImpl
import java.io.PrintWriter
import java.io.StringWriter
import java.util.*


internal object ResultAnalyzer {
    private const val SHOW_PATH_INFO = false
    private const val SHOW_INACTIVE = false

    @Suppress("unused")
    fun gatherErrors(
        result: ModSolver.Result,
        selectedMods: MutableMap<String?, ModCandidateImpl?>,
        modsById: MutableMap<String?, MutableList<ModCandidateImpl>>,
        envDisabledMods: MutableMap<String, MutableSet<ModCandidateImpl>>,
        envType: EnvType
    ): String {
        val sw = StringWriter()

        PrintWriter(sw).use { pw ->
            var prefix = ""
            var suggestFix = true

            if (result.fix != null) {
                pw.printf("\n%s", format("resolution.solutionHeader"))

                formatFix(result.fix, result, selectedMods, modsById, envDisabledMods.toMap(), envType, pw)

                pw.printf("\n%s", format("resolution.depListHeader"))
                prefix = "\t"
                suggestFix = false
            }

            val matches: MutableList<ModCandidateImpl> = ArrayList()

            for (explanation in result.reason!!) {
                assert(explanation.error.isDependencyError)

                val dep = explanation.dep
                val selected = selectedMods[dep!!.modId]

                if (selected != null) {
                    matches.add(selected)
                } else {
                    val candidates = modsById[dep.modId]
                    if (candidates != null) matches.addAll(candidates)
                }

                addErrorToList(
                    explanation.mod!!,
                    explanation.dep, matches, envDisabledMods.containsKey(dep.modId), suggestFix, prefix, pw
                )
                matches.clear()
            }
            if (SHOW_INACTIVE && result.fix != null && !result.fix.inactiveMods.isEmpty()) {
                pw.printf("\n%s", format("resolution.inactiveMods"))

                val entries: MutableList<Map.Entry<ModCandidateImpl, InactiveReason>> =
                    ArrayList<Map.Entry<ModCandidateImpl, InactiveReason>>(result.fix.inactiveMods.entries)

                // sort by root, id, version
                entries.sortWith(Comparator<Map.Entry<ModCandidateImpl, *>> { o1, o2 ->
                    val a = o1.key
                    val b = o2.key

                    if (a.isRoot != b.isRoot) {
                        return@Comparator if (a.isRoot) -1 else 1
                    }
                    ModCandidateImpl.ID_VERSION_COMPARATOR.compare(a, b)
                })

                for ((mod, reason) in entries) {
                    val reasonKey = String.format("resolution.inactive.%s", reason.id)

                    pw.printf(
                        "\n\t - %s", format(
                            "resolution.inactive",
                            getName(mod),
                            getVersion(mod),
                            format(reasonKey)
                        )
                    )
                    //appendJijInfo(mod, "\t", false, pw); TODO: show this without spamming too much
                }
            }
        }
        return sw.toString()
    }

    private fun formatFix(
        fix: ModSolver.Fix,
        result: ModSolver.Result,
        selectedMods: MutableMap<String?, ModCandidateImpl?>,
        modsById: MutableMap<String?, MutableList<ModCandidateImpl>>,
        envDisabledMods: Map<String?, Set<ModCandidateImpl>>,
        envType: EnvType,
        pw: PrintWriter
    ) {
        for (mod in fix.modsToAdd) {
            val envDisabledAlternatives = envDisabledMods[mod.id]

            if (envDisabledAlternatives == null) {
                pw.printf(
                    "\n\t - %s", format(
                        "resolution.solution.addMod",
                        mod.id,
                        formatVersionRequirements(mod.versionIntervals!!)
                    )
                )
            } else {
                val envKey = String.format("environment.%s", envType.name.lowercase(Locale.ENGLISH))

                pw.printf(
                    "\n\t - %s", format(
                        "resolution.solution.replaceModEnvDisabled",
                        formatOldMods(envDisabledAlternatives),
                        mod.id,
                        formatVersionRequirements(mod.versionIntervals!!),
                        format(envKey)
                    )
                )
            }
        }

        for (mod in fix.modsToRemove) {
            pw.printf(
                "\n\t - %s",
                format("resolution.solution.removeMod", getName(mod), getVersion(mod), mod.localPath)
            )
        }

        for ((newMod, oldMods) in fix.modReplacements) {
            val oldModsFormatted = formatOldMods(oldMods)

            if (oldMods.size != 1 || oldMods[0].id != newMod.id) { // replace mods with another mod (different mod id)
                var newModName: String = newMod.id.toString()
                val alt = selectedMods[newMod.id]

                if (alt != null) {
                    newModName = getName(alt)
                } else {
                    val alts = modsById[newMod.id]
                    if (alts != null && !alts.isEmpty()) newModName = getName(alts[0])
                }

                pw.printf(
                    "\n\t - %s", format(
                        "resolution.solution.replaceMod",
                        oldModsFormatted,
                        newModName,
                        formatVersionRequirements(newMod.versionIntervals!!)
                    )
                )
            } else { // replace mod version only
                val oldMod = oldMods[0]
                val hasOverlap = VersionInterval.and(
                    newMod.versionIntervals,
                    listOf(VersionIntervalImpl(oldMod.version, true, oldMod.version, true))
                ).isNotEmpty()

                if (!hasOverlap) { // required version range doesn't overlap installed version, recommend range as-is
                    pw.printf(
                        "\n\t - %s", format(
                            "resolution.solution.replaceModVersion",
                            oldModsFormatted,
                            formatVersionRequirements(newMod.versionIntervals!!)
                        )
                    )
                } else { // required version range overlaps installed version, recommend range without
                    pw.printf(
                        "\n\t - %s", format(
                            "resolution.solution.replaceModVersionDifferent",
                            oldModsFormatted,
                            formatVersionRequirements(newMod.versionIntervals!!)
                        )
                    )

                    var foundAny = false

                    // check old deps against future mod set to highlight inconsistencies
                    for (dep in oldMod.dependencies) {
                        if (dep!!.kind!!.isSoft) continue

                        val mod = fix.activeMods[dep.modId]

                        if (mod != null) {
                            if (dep.matches(mod.version) != dep.kind!!.isPositive) {
                                pw.printf(
                                    "\n\t\t - %s", format(
                                        "resolution.solution.replaceModVersionDifferent.reqSupportedModVersion",
                                        mod.id,
                                        getVersion(mod)
                                    )
                                )
                                foundAny = true
                            }

                            continue
                        }

                        for (addMod in fix.modReplacements.keys) {
                            if (addMod.id.equals(dep.modId)) {
                                pw.printf(
                                    "\n\t\t - %s", format(
                                        "resolution.solution.replaceModVersionDifferent.reqSupportedModVersions",
                                        addMod.id,
                                        formatVersionRequirements(addMod.versionIntervals!!)
                                    )
                                )
                                foundAny = true
                                break
                            }
                        }
                    }

                    if (!foundAny) {
                        pw.printf("\n\t\t - %s", format("resolution.solution.replaceModVersionDifferent.unknown"))
                    }
                }
            }
        }
    }
    fun gatherWarnings(
        uniqueSelectedMods: List<ModCandidateImpl>, selectedMods: Map<String?, ModCandidateImpl?>,
        envDisabledMods: Map<String, Set<ModCandidateImpl>>, envType: EnvType?
    ): String? {
        val sw = StringWriter()

        PrintWriter(sw).use { pw ->
            for (mod in uniqueSelectedMods) {
                for (dep in mod.dependencies) {
                    val depMod: ModCandidateImpl?

                    if (dep != null) {
                        when (dep.kind) {
                            ModDependency.Kind.RECOMMENDS -> {
                                depMod = selectedMods[dep.modId]

                                if (depMod == null || !dep.matches(depMod.version)) {
                                    addErrorToList(
                                        mod,
                                        dep,
                                        toList(depMod),
                                        envDisabledMods.containsKey(dep.modId),
                                        true,
                                        "",
                                        pw
                                    )
                                }
                            }

                            ModDependency.Kind.CONFLICTS -> {
                                depMod = selectedMods[dep.modId]

                                if (depMod != null && dep.matches(depMod.version)) {
                                    addErrorToList(mod, dep, toList(depMod), false, true, "", pw)
                                }
                            }

                            else -> {}
                        }
                    }
                }
            }
        }
        return if (sw.buffer.length == 0) {
            null
        } else {
            sw.toString()
        }
    }

    private fun toList(mod: ModCandidateImpl?): List<ModCandidateImpl> {
        return if (mod != null) listOf(mod) else emptyList()
    }

    private fun addErrorToList(
        mod: ModCandidateImpl,
        dep: ModDependency,
        matches: List<ModCandidateImpl>,
        presentForOtherEnv: Boolean,
        suggestFix: Boolean,
        prefix: String,
        pw: PrintWriter
    ) {
        val args = arrayOf<Any?>(
            getName(mod),
            getVersion(mod),
            (if (matches.isEmpty()) dep.modId else getName(matches[0])),
            formatVersionRequirements(dep.getVersionIntervals()),
            getVersions(matches),
            matches.size
        )

        val reason: String

        if (matches.isNotEmpty()) {
            var present: Boolean

            if (dep.kind!!.isPositive) {
                present = false

                for (match in matches) {
                    if (dep.matches(match.version)) { // there is a satisfying mod version, but it can't be loaded for other reasons
                        present = true
                        break
                    }
                }
            } else {
                present = true
            }

            reason = if (present) "invalid" else "mismatch"
        } else if (presentForOtherEnv && dep.kind!!.isPositive) {
            reason = "envDisabled"
        } else {
            reason = "missing"
        }

        var key = java.lang.String.format("resolution.%s.%s", dep.kind!!.key, reason)
        pw.printf("\n%s - %s", prefix, capitalize(format(key, *args)))

        if (suggestFix) {
            key = java.lang.String.format("resolution.%s.suggestion", dep.kind!!.key)
            pw.printf("\n%s\t - %s", prefix, capitalize(format(key, *args)))
        }

        if (SHOW_PATH_INFO) {
            for (m in matches) {
                appendJijInfo(m, prefix, true, pw)
            }
        }
    }

    private fun appendJijInfo(mod: ModCandidateImpl, prefix: String, mentionMod: Boolean, pw: PrintWriter) {
        val loc: String
        val path: String?

        if (mod.metadata.type.equals(AbstractModMetadata.TYPE_BUILTIN)) {
            loc = "builtin"
            path = null
        } else if (mod.isRoot) {
            loc = "root"
            path = mod.localPath
        } else {
            loc = "normal"

            val paths: MutableList<ModCandidateImpl> = ArrayList()
            paths.add(mod)

            var cur = mod

            do {
                var best: ModCandidateImpl? = null
                var maxDiff = 0

                for (parent in cur.parentMods) {
                    val diff = cur.minNestLevel - parent.minNestLevel

                    if (diff > maxDiff) {
                        best = parent
                        maxDiff = diff
                    }
                }

                if (best == null) break

                paths.add(best)
                cur = best
            } while (!cur.isRoot)

            val pathSb = StringBuilder()

            for (i in paths.indices.reversed()) {
                val m = paths[i]

                if (pathSb.length > 0) pathSb.append(" -> ")
                pathSb.append(m.localPath)
            }

            path = pathSb.toString()
        }

        val key = String.format("resolution.jij.%s%s", loc, if (mentionMod) "" else "NoMention")

        val text = if (mentionMod) {
            if (path == null) {
                format(key, getName(mod), getVersion(mod))
            } else {
                format(key, getName(mod), getVersion(mod), path)
            }
        } else {
            if (path == null) {
                format(key)
            } else {
                format(key, path)
            }
        }

        pw.printf(
            "\n%s\t - %s",
            prefix,
            capitalize(text)
        )
    }

    private fun formatOldMods(mods: Collection<ModCandidateImpl>): String {
        val modsSorted: MutableList<ModCandidateImpl> = ArrayList(mods)
        modsSorted.sortWith(ModCandidateImpl.ID_VERSION_COMPARATOR)
        val ret: MutableList<String?> = ArrayList(modsSorted.size)

        for (m in modsSorted) {
            if (SHOW_PATH_INFO && m.hasPath() && !m.isBuiltin) {
                ret.add(format("resolution.solution.replaceMod.oldMod", getName(m), getVersion(m), m.localPath))
            } else {
                ret.add(format("resolution.solution.replaceMod.oldModNoPath", getName(m), getVersion(m)))
            }
        }

        return formatEnumeration(ret, true)
    }

    private fun getName(candidate: ModCandidateImpl): String {
        val typePrefix = when (candidate.metadata.type) {
            AbstractModMetadata.TYPE_FABRIC_MOD -> String.format(
                "%s ",
                format("resolution.type.mod")
            )

            AbstractModMetadata.TYPE_BUILTIN -> ""
            else -> ""
        }

        return java.lang.String.format("%s'%s' (%s)", typePrefix, candidate.metadata.name, candidate.id)
    }

    private fun getVersion(candidate: ModCandidateImpl): String? {
        return candidate.version!!.friendlyString
    }

    private fun getVersions(candidates: Collection<ModCandidateImpl>): String {
        return candidates.joinToString("/") { getVersion(it).toString() }
    }


    private fun formatVersionRequirements(intervals: Collection<VersionInterval?>): String {
        val ret: MutableList<String> = ArrayList()

        for (interval in intervals) {
            val str: String

            if (interval == null) {
                // empty interval, skip
                continue
            } else if (interval.min == null) {
                str = if (interval.max == null) {
                    return format("resolution.version.any")
                } else if (interval.isMaxInclusive) {
                    format("resolution.version.lessEqual", interval.max)
                } else {
                    format("resolution.version.less", interval.max)
                }
            } else if (interval.max == null) {
                str = if (interval.isMinInclusive) {
                    format("resolution.version.greaterEqual", interval.min)
                } else {
                    format("resolution.version.greater", interval.min)
                }
            } else if (interval.min!!.equals(interval.max)) {
                if (interval.isMinInclusive && interval.isMaxInclusive) {
                    str = format("resolution.version.equal", interval.min)
                } else {
                    // empty interval, skip
                    continue
                }
            } else if (isWildcard(interval, 0)) { // major.x wildcard
                val version = interval.min as SemanticVersion?
                str = format("resolution.version.major", version!!.getVersionComponent(0))
            } else if (isWildcard(interval, 1)) { // major.minor.x wildcard
                val version = interval.min as SemanticVersion?
                str = format(
                    "resolution.version.majorMinor",
                    version!!.getVersionComponent(0),
                    version.getVersionComponent(1)
                )
            } else {
                val key = String.format(
                    "resolution.version.rangeMin%sMax%s",
                    (if (interval.isMinInclusive) "Inc" else "Exc"),
                    (if (interval.isMaxInclusive) "Inc" else "Exc")
                )
                str = format(key, interval.min, interval.max)
            }

            ret.add(str)
        }

        return if (ret.isEmpty()) {
            format("resolution.version.none")
        } else {
            formatEnumeration(ret, false)
        }
    }

    /**
     * Determine whether an interval can be represented by a .x wildcard version string.
     *
     *
     * Example: [1.2.0-,1.3.0-) is the same as 1.2.x (incrementedComponent=1)
     */
    private fun isWildcard(interval: VersionInterval?, incrementedComponent: Int): Boolean {
        if (interval?.min == null || interval.max == null // not an interval with lower+upper bounds
            || !interval.isMinInclusive || interval.isMaxInclusive // not an [a,b) interval
            || !interval.isSemantic
        ) {
            return false
        }

        val min = interval.min as SemanticVersion?
        val max = interval.max as SemanticVersion?

        // min and max need to use the empty prerelease (a.b.c-)
        if ("" != min!!.prereleaseKey!!.orElse(null) || "" != max!!.prereleaseKey!!.orElse(null)) {
            return false
        }

        // max needs to be min + 1 for the covered component
        if (max.getVersionComponent(incrementedComponent) != min.getVersionComponent(incrementedComponent) + 1) {
            return false
        }

        var i = incrementedComponent + 1
        val m = Math.max(min.versionComponentCount, max.versionComponentCount)
        while (i < m) {
            // all following components need to be 0
            if (min.getVersionComponent(i) != 0 || max.getVersionComponent(i) != 0) {
                return false
            }
            i++
        }

        return true
    }

    private fun formatEnumeration(elements: Collection<*>, isAnd: Boolean): String {
        val keyPrefix = if (isAnd) "enumerationAnd." else "enumerationOr."
        val it = elements.iterator()

        when (elements.size) {
            0 -> return ""
            1 -> return Objects.toString(it.next())
            2 -> return format(keyPrefix + "2", it.next(), it.next())
            3 -> return format(keyPrefix + "3", it.next(), it.next(), it.next())
        }

        var ret = format(keyPrefix + "nPrefix", it.next())

        do {
            val next = it.next()!!
            ret = format(if (it.hasNext()) keyPrefix + "n" else keyPrefix + "nSuffix", ret, next)
        } while (it.hasNext())

        return ret
    }
}
