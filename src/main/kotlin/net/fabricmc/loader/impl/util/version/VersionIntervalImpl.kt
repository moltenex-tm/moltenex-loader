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
import com.moltenex.loader.api.util.version.VersionInterval
import com.moltenex.loader.api.util.version.VersionInterval.Companion.INFINITE
import net.fabricmc.loader.api.SemanticVersion
import java.util.*
import kotlin.collections.ArrayList
import kotlin.properties.Delegates

class VersionIntervalImpl(
    override val min: Version?, minInclusive: Boolean,
    override val max: Version?, maxInclusive: Boolean
) : VersionInterval {
    override val isMinInclusive: Boolean = if (min != null) minInclusive else false
    override val isMaxInclusive: Boolean = if (max != null) maxInclusive else false

    init {
        assert(min != null || !minInclusive)
        assert(max != null || !maxInclusive)
        assert(min == null || min is SemanticVersion || minInclusive)
        assert(max == null || max is SemanticVersion || maxInclusive)
        assert(min == null || max == null || min is SemanticVersion && max is SemanticVersion || min == max)
    }

    override val isSemantic: Boolean
        get() = (min == null || min is SemanticVersion)
                && (max == null || max is SemanticVersion)

    override fun equals(other: Any?): Boolean {
        return if (other is VersionInterval) {

            Objects.equals(min, other.min) && isMinInclusive == other.isMinInclusive && Objects.equals(
                max,
                other.max
            ) && isMaxInclusive == other.isMaxInclusive
        } else {
            false
        }
    }

    override fun hashCode(): Int {
        return ((Objects.hashCode(min) + (if (isMinInclusive) 1 else 0)) * 31
                + (Objects.hashCode(max) + (if (isMaxInclusive) 1 else 0)) * 31)
    }

    override fun toString(): String {
        return if (min == null) {
            if (max == null) {
                "(-∞,∞)"
            } else {
                String.format("(-∞,%s%c", max, if (isMaxInclusive) ']' else ')')
            }
        } else if (max == null) {
            String.format("%c%s,∞)", if (isMinInclusive) '[' else '(', min)
        } else {
            String.format("%c%s,%s%c", if (isMinInclusive) '[' else '(', min, max, if (isMaxInclusive) ']' else ')')
        }
    }

    companion object {
        fun and(a: VersionInterval?, b: VersionInterval?): VersionInterval? {
            if (a == null || b == null) return null

            return if (!a.isSemantic || !b.isSemantic) {
                andPlain(a, b)
            } else {
                andSemantic(a, b)
            }
        }

        private fun andPlain(a: VersionInterval, b: VersionInterval): VersionInterval? {
            val aMin: Version? = a.min
            val aMax: Version? = a.max
            val bMin: Version? = b.min
            val bMax: Version? = b.max

            if (aMin != null) { // -> min must be aMin or invalid
                if (bMin != null && !aMin.equals(bMin) || bMax != null && !aMin.equals(bMax)) {
                    return null
                }

                if (aMax != null || bMax == null) {
                    assert(Objects.equals(aMax, bMax) || bMax == null)
                    return a
                } else {
                    return VersionIntervalImpl(aMin, true, bMax, b.isMaxInclusive)
                }
            } else if (aMax != null) { // -> min must be bMin, max must be aMax or invalid
                if (bMin != null && !aMax.equals(bMin) || bMax != null && !aMax.equals(bMax)) {
                    return null
                }

                return if (bMin == null) {
                    a
                } else if (bMax != null) {
                    b
                } else {
                    VersionIntervalImpl(bMin, true, aMax, true)
                }
            } else {
                return b
            }
        }

        private fun andSemantic(a: VersionInterval, b: VersionInterval): VersionInterval? {
            val minCmp = compareMin(a, b)
            val maxCmp = compareMax(a, b)

            if (minCmp == 0) { // aMin == bMin
                return if (maxCmp == 0) { // aMax == bMax -> a == b -> a/b
                    a
                } else { // aMax != bMax -> a/b..min(a,b)
                    if (maxCmp < 0) a else b
                }
            } else if (maxCmp == 0) { // aMax == bMax, aMin != bMin -> max(a,b)..a/b
                return if (minCmp < 0) b else a
            } else if (minCmp < 0) { // aMin < bMin, aMax != bMax -> b..min(a,b)
                if (maxCmp > 0) return b // a > b -> b


                val aMax = a.max as SemanticVersion?
                val bMin = b.min as SemanticVersion?
                val cmp = bMin!!.compareTo(aMax as Version?)

                return if (cmp < 0 || cmp == 0 && b.isMinInclusive && a.isMaxInclusive) {
                    VersionIntervalImpl(bMin, b.isMinInclusive, aMax, a.isMaxInclusive)
                } else {
                    null
                }
            } else { // aMin > bMin, aMax != bMax -> a..min(a,b)
                if (maxCmp < 0) return a // a < b -> a


                val aMin = a.min as SemanticVersion?
                val bMax = b.max as SemanticVersion?
                val cmp = aMin!!.compareTo(bMax as Version?)

                return if (cmp < 0 || cmp == 0 && a.isMinInclusive && b.isMaxInclusive) {
                    VersionIntervalImpl(aMin, a.isMinInclusive, bMax, b.isMaxInclusive)
                } else {
                    null
                }
            }
        }

        fun and(a: Collection<VersionInterval>, b: Collection<VersionInterval>): List<VersionInterval> {
            if (a.isEmpty() || b.isEmpty()) return emptyList()

            if (a.size == 1 && b.size == 1) {
                val merged = and(a.iterator().next(), b.iterator().next())
                return if (merged != null) listOf(merged) else emptyList()
            }

            // (a0 || a1 || a2) && (b0 || b1 || b2) == a0 && b0 && b1 && b2 || a1 && b0 && b1 && b2 || a2 && b0 && b1 && b2
            val allMerged: MutableList<VersionInterval> = ArrayList()

            for (intervalA in a) {
                for (intervalB in b) {
                    val merged = and(intervalA, intervalB)
                    if (merged != null) allMerged.add(merged)
                }
            }

            if (allMerged.isEmpty()) return emptyList()
            if (allMerged.size == 1) return allMerged

            val ret: MutableList<VersionInterval> = ArrayList(allMerged.size)

            for (v in allMerged) {
                merge(v, ret)
            }

            return ret
        }

        fun or(a: Collection<VersionInterval?>, b: VersionInterval?): List<VersionInterval> {
            if (a.isEmpty()) {
                return if (b == null) {
                    emptyList()
                } else {
                    listOf(b)
                }
            }

            val ret: MutableList<VersionInterval> = ArrayList(a.size + 1)

            for (v in a) {
                merge(v, ret)
            }

            merge(b, ret)

            return ret
        }

        private fun merge(a: VersionInterval?, out: MutableList<VersionInterval>) {
            if (a == null) return

            if (out.isEmpty()) {
                out.add(a)
                return
            }

            if (out.size == 1) {
                val e = out[0]

                if (e.min == null && e.max == null) {
                    return
                }
            }

            if (!a.isSemantic) {
                mergePlain(a, out)
            } else {
                mergeSemantic(a, out)
            }
        }

        private fun mergePlain(a: VersionInterval, out: MutableList<VersionInterval>) {
            val aMin: Version? = a.min
            val aMax: Version? = a.max
            val v: Version = checkNotNull(if (aMin != null) aMin else aMax)
            for (i in out.indices) {
                val c = out[i]

                if (v == c.min) {
                    if (aMin == null) {
                        assert(aMax!! == c.min)
                        out.clear()
                        out.add(INFINITE)
                    } else if (aMax == null && c.max != null) {
                        out[i] = a
                    }

                    return
                } else if (v == c.max) {
                    assert(c.min == null)

                    if (aMax == null) {
                        assert(aMin!! == c.max)
                        out.clear()
                        out.add(INFINITE)
                    }

                    return
                }
            }

            out.add(a)
        }

        private fun mergeSemantic(a: VersionInterval, out: MutableList<VersionInterval>) {
            var a = a
            val aMin = a.min as SemanticVersion?
            val aMax = a.max as SemanticVersion?

            if (aMin == null && aMax == null) {
                out.clear()
                out.add(INFINITE)
                return
            }

            var i = 0
            while (i < out.size) {
                val c = out[i]
                if (!c.isSemantic) {
                    i++
                    continue
                }

                val cMin = c.min as SemanticVersion?
                val cMax = c.max as SemanticVersion?
                var cmp: Int

                if (aMin == null) { // ..a..]
                    if (cMax == null) { // ..a..] [..c..
                        cmp = aMax!!.compareTo(cMin as Version?)

                        if (cmp < 0 || cmp == 0 && !a.isMaxInclusive && !c.isMinInclusive) { // ..a..]..[..c.. or ..a..)(..c..
                            out.add(i, a)
                        } else { // ..a..|..c.. or ..a.[..].c..
                            out.clear()
                            out.add(INFINITE)
                        }

                        return
                    } else { // ..a..] [..c..]
                        cmp = compareMax(a, c)

                        if (cmp >= 0) { // a encompasses c
                            out.removeAt(i)
                            i--
                        } else if (cMin == null) { // c encompasses a
                            return
                        } else { // aMax < cMax
                            cmp = aMax!!.compareTo(cMin as Version)

                            if (cmp < 0 || cmp == 0 && !a.isMaxInclusive && !c.isMinInclusive) { // ..a..]..[..c..] or ..a..)(..c..]
                                out.add(i, a)
                            } else { // c extends a to the right
                                out[i] = VersionIntervalImpl(null, false, cMax, c.isMaxInclusive)
                            }

                            return
                        }
                    }
                } else if (cMax == null) { // [..c..
                    cmp = compareMin(a, c)

                    if (cmp >= 0) { // c encompasses a
                        // no-op
                    } else if (aMax == null) { // a encompasses c
                        while (out.size > i) out.removeAt(i)
                        out.add(a)
                    } else { // aMin < cMin
                        cmp = aMax.compareTo(cMin as Version?)

                        if (cmp < 0 || cmp == 0 && !a.isMaxInclusive && !c.isMinInclusive) { // [..a..]..[..c.. or [..a..)(..c..
                            out.add(i, a)
                        } else { // a extends c to the left
                            out[i] = VersionIntervalImpl(aMin, a.isMinInclusive, null, false)
                        }
                    }

                    return
                } else if ((aMin.compareTo(cMax as Version)
                        .also { cmp = it }) < 0 || cmp == 0 && (a.isMinInclusive || c.isMaxInclusive)
                ) {
                    var cmp2: Int by Delegates.notNull()
                    if (aMax == null || cMin == null || (aMax.compareTo(cMin as Version)
                            .also { cmp2 = it }) > 0 || cmp2 == 0 && (a.isMaxInclusive || c.isMinInclusive)
                    ) {
                        val cmpMin = compareMin(a, c)
                        val cmpMax = compareMax(a, c)

                        if (cmpMax <= 0) { // aMax <= cMax
                            if (cmpMin < 0) { // aMin < cMin
                                out[i] = VersionIntervalImpl(aMin, a.isMinInclusive, cMax, c.isMaxInclusive)
                            }

                            return
                        } else if (cmpMin > 0) { // aMin > cMin, aMax > cMax
                            a = VersionIntervalImpl(cMin, c.isMinInclusive, aMax, a.isMaxInclusive)
                        }

                        out.removeAt(i)
                        i--
                    } else {
                        out.add(i, a)
                        return
                    }
                }
                i++
            }

            out.add(a)
        }

        private fun compareMin(a: VersionInterval, b: VersionInterval): Int {
            val aMin = a.min as SemanticVersion?
            val bMin = b.min as SemanticVersion?
            var cmp: Int by Delegates.notNull()

            return if (aMin == null) { // a <= b
                if (bMin == null) { // a == b == -inf
                    0
                } else { // bMin != null -> a < b
                    -1
                }
            } else if (bMin == null || (aMin.compareTo(bMin as Version)
                    .also { cmp = it }) > 0 || cmp == 0 && !a.isMinInclusive && b.isMinInclusive
            ) { // a > b
                1
            } else if (cmp < 0 || a.isMinInclusive && !b.isMinInclusive) { // a < b
                -1
            } else { // cmp == 0 && a.minInclusive() == b.minInclusive() -> a == b
                0
            }
        }

        private fun compareMax(a: VersionInterval, b: VersionInterval): Int {
            val aMax = a.max as SemanticVersion?
            val bMax = b.max as SemanticVersion?
            var cmp: Int by Delegates.notNull()

            return if (aMax == null) { // a >= b
                if (bMax == null) { // a == b == inf
                    0
                } else { // bMax != null -> a > b
                    1
                }
            } else if (bMax == null || (aMax.compareTo(bMax as Version)
                    .also { cmp = it }) < 0 || cmp == 0 && !a.isMaxInclusive && b.isMaxInclusive
            ) { // a < b
                -1
            } else if (cmp > 0 || a.isMaxInclusive && !b.isMaxInclusive) { // a > b
                1
            } else { // cmp == 0 && a.maxInclusive() == b.maxInclusive() -> a == b
                0
            }
        }

        fun not(interval: VersionInterval?): List<VersionInterval> {
            if (interval == null) { // () = empty interval -> infinite
                return listOf<VersionInterval>(INFINITE)
            } else if (interval.min == null) { // (-∞, = at least half-open towards min
                return if (interval.max == null) { // (-∞,∞) = infinite -> empty
                    emptyList()
                } else { // (-∞,x = left open towards min -> half open towards max
                    listOf<VersionInterval>(VersionIntervalImpl(interval.max, !interval.isMaxInclusive, null, false))
                }
            } else if (interval.max == null) { // x,∞) = half open towards max -> half open towards min
                return listOf<VersionInterval>(VersionIntervalImpl(null, false, interval.min, !interval.isMinInclusive))
            } else if (interval.min!! == interval.max && !interval.isMinInclusive && !interval.isMaxInclusive) { // (x,x) = effectively empty interval -> infinite
                return listOf<VersionInterval>(INFINITE)
            } else { // closed interval -> 2 half open intervals on each side
                val ret: MutableList<VersionInterval> = ArrayList(2)
                ret.add(VersionIntervalImpl(null, false, interval.min, !interval.isMinInclusive))
                ret.add(VersionIntervalImpl(interval.max, !interval.isMaxInclusive, null, false))

                return ret
            }
        }

        fun not(intervals: Collection<VersionInterval?>): List<VersionInterval>? {
            if (intervals.isEmpty()) return listOf<VersionInterval>(INFINITE)
            if (intervals.size == 1) return not(intervals.iterator().next())

            // !(i0 || i1 || i2) == !i0 && !i1 && !i2
            var ret: List<VersionInterval>? = null

            for (v in intervals) {
                val inverted = not(v)

                ret = if (ret == null) {
                    inverted
                } else {
                    and(ret, inverted)
                }

                if (ret.isEmpty()) break
            }

            return ret
        }
    }
}