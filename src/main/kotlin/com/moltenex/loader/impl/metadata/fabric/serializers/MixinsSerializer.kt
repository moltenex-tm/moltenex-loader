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

import com.moltenex.loader.impl.metadata.fabric.v0.Mixins
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

object MixinsSerializer : KSerializer<Mixins> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("Mixins") {
        element<List<String?>>("client", isOptional = true)
        element<List<String?>>("common", isOptional = true)
        element<List<String?>>("server", isOptional = true)
    }

    override fun serialize(encoder: Encoder, value: Mixins) {
        val composite = encoder.beginStructure(descriptor)

        // Serialize each collection if it's not empty
        if (value.client.isNotEmpty()) composite.encodeSerializableElement(descriptor, 0, ListSerializer(String.serializer()),
            value.client.toList() as List<String>
        )
        if (value.common.isNotEmpty()) composite.encodeSerializableElement(descriptor, 1, ListSerializer(String.serializer()),
            value.common.toList() as List<String>
        )
        if (value.server.isNotEmpty()) composite.encodeSerializableElement(descriptor, 2, ListSerializer(String.serializer()),
            value.server.toList() as List<String>
        )

        composite.endStructure(descriptor)
    }

    override fun deserialize(decoder: Decoder): Mixins {
        val input = decoder.beginStructure(descriptor)
        val client = mutableListOf<String?>()
        val common = mutableListOf<String?>()
        val server = mutableListOf<String?>()

        // Deserialize each field based on the element index
        loop@ while (true) {
            when (val index = input.decodeElementIndex(descriptor)) {
                CompositeDecoder.DECODE_DONE -> break@loop
                0 -> client.addAll(input.decodeSerializableElement(descriptor, 0, ListSerializer(String.serializer())))
                1 -> common.addAll(input.decodeSerializableElement(descriptor, 1, ListSerializer(String.serializer())))
                2 -> server.addAll(input.decodeSerializableElement(descriptor, 2, ListSerializer(String.serializer())))
                else -> throw SerializationException("Unexpected index: $index")
            }
        }

        input.endStructure(descriptor)
        return Mixins(client, common, server)
    }
}
