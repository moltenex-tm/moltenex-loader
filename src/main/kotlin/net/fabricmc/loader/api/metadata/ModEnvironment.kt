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

import com.moltenex.loader.impl.metadata.fabric.serializers.ModEnvironmentSerializer
import kotlinx.serialization.Serializable
import net.fabricmc.api.EnvType

@Serializable(with = ModEnvironmentSerializer::class)
enum class ModEnvironment {
    CLIENT,
    SERVER,
    UNIVERSAL;

    fun matches(type: EnvType): Boolean {
        return when (this) {
            CLIENT -> type == EnvType.CLIENT
            SERVER -> type == EnvType.SERVER
            UNIVERSAL -> true
        }
    }
}
