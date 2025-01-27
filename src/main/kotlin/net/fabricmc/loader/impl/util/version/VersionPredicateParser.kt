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
import net.fabricmc.loader.api.VersionParsingException
import net.fabricmc.loader.api.metadata.version.VersionComparisonOperator
import net.fabricmc.loader.api.metadata.version.VersionPredicate
import net.fabricmc.loader.api.metadata.version.VersionPredicate.PredicateTerm
import net.fabricmc.loader.impl.util.version.VersionParser.parse
import java.util.*
import java.util.function.Predicate
import kotlin.collections.ArrayList
import kotlin.collections.HashSet


object VersionPredicateParser {
    private val OPERATORS = VersionComparisonOperator.entries.toTypedArray()

    @Throws(VersionParsingException::class)
    fun parse(predicate: String): VersionPredicate {
        val predicateList: MutableList<SingleVersionPredicate> = ArrayList()

        for (s in predicate.split(" ".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()) {
            var s = s
            s = s.trim { it <= ' ' }

            if (s.isEmpty() || s == "*") {
                continue
            }

            var operator = VersionComparisonOperator.EQUAL

            for (op in OPERATORS) {
                if (s.startsWith(op.serialized)) {
                    operator = op
                    s = s.substring(op.serialized.length)
                    break
                }
            }

            var version: Version = parse(s, true)

            if (version is SemanticVersion) {
                val semVer = version as SemanticVersion

                if (semVer.hasWildcard()) { // .x version -> replace with conventional version by replacing the operator
                    if (operator !== VersionComparisonOperator.EQUAL) {
                        throw VersionParsingException("Invalid predicate: $predicate, version ranges with wildcards (.X) require using the equality operator or no operator at all!")
                    }

                    assert(!semVer.prereleaseKey!!.isPresent)

                    val compCount = semVer.versionComponentCount
                    assert(compCount == 2 || compCount == 3)

                    operator =
                        if (compCount == 2) VersionComparisonOperator.SAME_TO_NEXT_MAJOR else VersionComparisonOperator.SAME_TO_NEXT_MINOR

                    val newComponents = IntArray(semVer.versionComponentCount - 1)

                    for (i in 0..<semVer.versionComponentCount - 1) {
                        newComponents[i] = semVer.getVersionComponent(i)
                    }

                    version = SemanticVersionImpl(newComponents, "", semVer.buildKey!!.orElse(null))
                }
            } else if (!operator.isMinInclusive && !operator.isMaxInclusive) { // non-semver without inclusive bound
                throw VersionParsingException("Invalid predicate: $predicate, version ranges need to be semantic version compatible to use operators that exclude the bound!")
            } else { // non-semver with inclusive bound
                operator = VersionComparisonOperator.EQUAL
            }

            predicateList.add(SingleVersionPredicate(operator, version))
        }

        return if (predicateList.isEmpty()) {
            AnyVersionPredicate.INSTANCE
        } else if (predicateList.size == 1) {
            predicateList[0]
        } else {
            MultiVersionPredicate(predicateList)
        }
    }

    @Throws(VersionParsingException::class)
    fun parse(predicates: Collection<String>): Set<VersionPredicate> {
        val ret: MutableSet<VersionPredicate> = HashSet(predicates.size)

        for (version in predicates) {
            ret.add(parse(version))
        }

        return ret
    }

    val any: VersionPredicate
        get() = AnyVersionPredicate.INSTANCE

    internal class AnyVersionPredicate private constructor() : VersionPredicate {
        override fun test(t: Version?): Boolean {
            return true
        }

        override val terms: List<PredicateTerm>
            get() = emptyList()

        override val interval: VersionInterval
            get() = INFINITE

        override fun toString(): String {
            return "*"
        }

        companion object {
            val INSTANCE: VersionPredicate = AnyVersionPredicate()
        }
    }

    internal class SingleVersionPredicate(override val operator: VersionComparisonOperator, refVersion: Version) :
        VersionPredicate,
        PredicateTerm {
        private val refVersion: Version = refVersion

        override fun test(version: Version?): Boolean {
            Objects.requireNonNull(version, "null version")

            return operator.test(version!!, refVersion)
        }

        override val terms: List<PredicateTerm>
            get() = listOf<PredicateTerm>(this)

        override val interval: VersionInterval
            get() {
                if (refVersion is SemanticVersion) {
                    val version = refVersion as SemanticVersion

                    return VersionIntervalImpl(
                        operator.minVersion(version), operator.isMinInclusive,
                        operator.maxVersion(version), operator.isMaxInclusive
                    )
                } else {
                    return VersionIntervalImpl(refVersion, true, refVersion, true)
                }
            }

        override val referenceVersion: Version
            get() = refVersion

        override fun equals(obj: Any?): Boolean {
            if (obj is SingleVersionPredicate) {
                val o = obj

                return operator === o.operator && refVersion.equals(o.refVersion)
            } else {
                return false
            }
        }

        override fun hashCode(): Int {
            return operator.ordinal * 31 + refVersion.hashCode()
        }

        override fun toString(): String {
            return operator.serialized + refVersion.toString()
        }
    }

    internal class MultiVersionPredicate(private val predicates: List<SingleVersionPredicate>) :
        VersionPredicate {
        override fun test(version: Version?): Boolean {
            Objects.requireNonNull(version, "null version")

            for (predicate in predicates) {
                if (!predicate.test(version)) return false
            }

            return true
        }

        override val terms: List<PredicateTerm>
            get() = predicates

        override val interval: VersionInterval?
            get() {
                if (predicates.isEmpty()) return AnyVersionPredicate.INSTANCE.interval

                var ret: VersionInterval? = predicates[0].interval

                for (i in 1..<predicates.size) {
                    ret = VersionIntervalImpl.and(ret, predicates[i].interval)
                }

                return ret
            }

        override fun equals(obj: Any?): Boolean {
            if (obj is MultiVersionPredicate) {
                return predicates == obj.predicates
            } else {
                return false
            }
        }

        override fun hashCode(): Int {
            return predicates.hashCode()
        }

        override fun toString(): String {
            val ret = StringBuilder()

            for (predicate in predicates) {
                if (ret.length > 0) ret.append(' ')
                ret.append(predicate.toString())
            }

            return ret.toString()
        }
    }
}