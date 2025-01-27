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

class StringVersion(override val friendlyString: String) : Version {
    override fun equals(other: Any?): Boolean {
        return if (other is StringVersion) {
            friendlyString == other.friendlyString
        } else {
            false
        }
    }

    override fun compareTo(other: Version?): Int {
        return friendlyString.compareTo(other?.friendlyString!!)
    }

    override fun toString(): String {
        return friendlyString
    }

    override fun hashCode(): Int {
        return javaClass.hashCode()
    }
}
