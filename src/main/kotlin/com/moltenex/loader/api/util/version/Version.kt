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

import net.fabricmc.loader.api.VersionParsingException
import net.fabricmc.loader.api.metadata.ModMetadata
import net.fabricmc.loader.impl.util.version.VersionParser.parse


/**
 * Represents a version of a mod.
 *
 * @see ModMetadata.getVersion
 */
interface Version : Comparable<Version?> {
    /**
     * Returns the user-friendly representation of this version.
     */
    val friendlyString: String?

    companion object {
        /**
         * Parses a version from a string notation.
         *
         * @param string the string notation of the version
         * @return the parsed version
         * @throws VersionParsingException if a problem arises during version parsing
         */
        @Throws(VersionParsingException::class)
        fun parse(string: String?): Version {
            return parse(string, false)
        }
    }
}