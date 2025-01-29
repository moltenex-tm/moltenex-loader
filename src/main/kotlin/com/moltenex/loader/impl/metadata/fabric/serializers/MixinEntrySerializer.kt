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

import kotlinx.serialization.*
import kotlinx.serialization.encoding.*
import kotlinx.serialization.descriptors.*
import net.fabricmc.loader.api.metadata.ModEnvironment
import net.fabricmc.loader.impl.metadata.V1ModMetadata

object MixinEntrySerializer : KSerializer<V1ModMetadata.MixinEntry> {

    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("MixinEntry") {
        element<String>("config")
        element("environment", ModEnvironment.serializer().descriptor)
    }

    override fun serialize(encoder: Encoder, value: V1ModMetadata.MixinEntry) {
        val compositeEncoder = encoder.beginStructure(descriptor)
        compositeEncoder.encodeStringElement(descriptor, 0, value.config)
        compositeEncoder.encodeSerializableElement(descriptor, 1, ModEnvironment.serializer(), value.environment)
        compositeEncoder.endStructure(descriptor)
    }

    override fun deserialize(decoder: Decoder): V1ModMetadata.MixinEntry {
        val compositeDecoder = decoder.beginStructure(descriptor)
        var config: String? = null
        var environment: ModEnvironment? = null

        loop@ while (true) {
            when (val index = compositeDecoder.decodeElementIndex(descriptor)) {
                CompositeDecoder.DECODE_DONE -> break@loop
                0 -> config = compositeDecoder.decodeStringElement(descriptor, index)
                1 -> environment = compositeDecoder.decodeSerializableElement(descriptor, index, ModEnvironment.serializer())
                else -> throw SerializationException("Unknown index $index")
            }
        }

        compositeDecoder.endStructure(descriptor)

        return V1ModMetadata.MixinEntry(
            config = config ?: throw SerializationException("Missing required field 'config'"),
            environment = environment ?: throw SerializationException("Missing required field 'environment'")
        )
    }
}