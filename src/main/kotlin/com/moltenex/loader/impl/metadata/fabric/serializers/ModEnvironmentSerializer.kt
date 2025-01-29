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
package com.moltenex.loader.impl.metadata.fabric.serializers

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*
import net.fabricmc.loader.api.metadata.ModEnvironment

object ModEnvironmentSerializer : KSerializer<ModEnvironment> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("ModEnvironment", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): ModEnvironment {
        val rawEnvironment = decoder.decodeString()
        return when (rawEnvironment.lowercase()) {
            "universal" -> ModEnvironment.UNIVERSAL
            "client" -> ModEnvironment.CLIENT
            "server" -> ModEnvironment.SERVER
            else -> throw IllegalArgumentException("Invalid side type: $rawEnvironment")
        }
    }

    override fun serialize(encoder: Encoder, value: ModEnvironment) {
        val rawEnvironment = when (value) {
            ModEnvironment.UNIVERSAL -> "universal"
            ModEnvironment.CLIENT -> "client"
            ModEnvironment.SERVER -> "server"
        }
        encoder.encodeString(rawEnvironment)
    }
}
