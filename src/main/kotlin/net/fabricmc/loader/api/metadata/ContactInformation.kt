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
package net.fabricmc.loader.api.metadata

import java.util.*

/**
 * Represents a contact information.
 */
interface ContactInformation {
    /**
     * Gets a certain type of contact information.
     *
     * @param key the type of contact information
     * @return an optional contact information
     */
    fun get(key: String?): Optional<String>

    /**
     * Gets all contact information provided as a map from contact type to information.
     */
    fun asMap(): Map<String, String>

    companion object {
        /**
         * An empty contact information.
         */
        @JvmField
        val EMPTY: ContactInformation = object : ContactInformation {
            override fun get(key: String?): Optional<String> {
                return Optional.empty()
            }

            override fun asMap(): Map<String, String> {
                return emptyMap()
            }
        }
    }
}
