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
package net.fabricmc.loader.impl.metadata

import net.fabricmc.loader.api.metadata.ContactInformation
import java.util.*

class ContactInformationImpl(map: Map<String, String>) : ContactInformation {
    private val map: Map<String, String> =
        Collections.unmodifiableMap(map)

    override fun get(key: String?): Optional<String> {
        return Optional.ofNullable(map[key])
    }

    override fun asMap(): Map<String, String> {
        return map
    }
}
