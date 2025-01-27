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
package com.moltenex.loader.api.util.version

import net.fabricmc.loader.impl.util.version.VersionIntervalImpl

/**
 * Representation of a version interval, closed or open.
 *
 *
 * The represented version interval is contiguous between its lower and upper limit, disjoint intervals are built
 * using collections of [VersionInterval]. Empty intervals may be represented by `null` or any interval
 * @code (x,x)} with x being a non-`null` version and both endpoints being exclusive.
 */
interface VersionInterval {
    /**
     * Get whether the interval uses [SemanticVersion] compatible bounds.
     *
     * @return True if both bounds are open (null), [SemanticVersion] instances or a combination of both, false otherwise.
     */
    val isSemantic: Boolean

    /**
     * Get the lower limit of the version interval.
     *
     * @return Version's lower limit or null if none, inclusive depending on [.isMinInclusive]
     */
    val min: Version?

    /**
     * Get whether the lower limit of the version interval is inclusive.
     *
     * @return True if inclusive, false otherwise
     */
    val isMinInclusive: Boolean

    /**
     * Get the upper limit of the version interval.
     *
     * @return Version's upper limit or null if none, inclusive depending on [.isMaxInclusive]
     */
    val max: Version?

    /**
     * Get whether the upper limit of the version interval is inclusive.
     *
     * @return True if inclusive, false otherwise
     */
    val isMaxInclusive: Boolean

    fun and(o: VersionInterval?): VersionInterval {
        return and(this, o)
    }

    fun or(o: Collection<VersionInterval?>?): List<VersionInterval> {
        return or(o, this)
    }

    companion object {
        /**
         * Compute the intersection between two version intervals.
         */
        fun and(a: VersionInterval?, b: VersionInterval?): VersionInterval {
            return VersionIntervalImpl.and(a, b)!!
        }

        /**
         * Compute the intersection between two potentially disjoint of version intervals.
         */
        fun and(a: Collection<VersionInterval>?, b: Collection<VersionInterval>?): List<VersionInterval> {
            return VersionIntervalImpl.and(a!!, b!!)
        }

        /**
         * Compute the union between multiple version intervals.
         */
        fun or(a: Collection<VersionInterval?>?, b: VersionInterval?): List<VersionInterval> {
            return VersionIntervalImpl.or(a!!, b)
        }

        fun not(interval: VersionInterval): List<VersionInterval> {
            return VersionIntervalImpl.not(interval)
        }

        fun not(intervals: Collection<VersionInterval?>?): List<VersionInterval> {
            return VersionIntervalImpl.not(intervals!!)!!
        }

        val INFINITE: VersionInterval = VersionIntervalImpl(null, false, null, false)
    }
}