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
package net.fabricmc.loader.impl.launch.knot

import org.spongepowered.asm.service.IPropertyKey

class MixinStringPropertyKey(@JvmField val key: String) : IPropertyKey {
    override fun equals(other: Any?): Boolean {
        return if (other !is MixinStringPropertyKey) {
            false
        } else {
            key == other.key
        }
    }

    override fun hashCode(): Int {
        return key.hashCode()
    }

    override fun toString(): String {
        return this.key
    }
}
