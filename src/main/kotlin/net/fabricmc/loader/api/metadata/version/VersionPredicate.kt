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

import com.moltenex.loader.api.util.version.VersionInterval
import com.moltenex.loader.api.util.version.Version
import net.fabricmc.loader.api.VersionParsingException
import net.fabricmc.loader.impl.util.version.VersionPredicateParser
import java.util.function.Predicate

interface VersionPredicate : Predicate<Version?> {
    /**
     * Get all terms that have to be satisfied for this predicate to match.
     *
     * @return Required predicate terms, empty if anything matches
     */
    val terms: Collection<PredicateTerm?>?

    /**
     * Get the version interval representing the matched versions.
     *
     * @return Covered version interval or null if nothing
     */
	val interval: VersionInterval?

    interface PredicateTerm {
        val operator: VersionComparisonOperator?
        val referenceVersion: Version?
    }

    companion object {
        @Throws(VersionParsingException::class)
        fun parse(predicate: String): VersionPredicate {
            return VersionPredicateParser.parse(predicate)
        }

        @Throws(VersionParsingException::class)
        fun parse(predicates: Collection<String>): Collection<VersionPredicate> {
            return VersionPredicateParser.parse(predicates)
        }
    }
}
