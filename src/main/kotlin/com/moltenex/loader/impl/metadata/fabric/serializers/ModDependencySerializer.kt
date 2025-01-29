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

import com.moltenex.loader.impl.metadata.fabric.common.ModDependencyImpl
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
import net.fabricmc.loader.api.metadata.ModDependency

object ModDependencySerializer : KSerializer<ModDependencyImpl> {

    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("ModDependency") {
        element<String>("kind")
        element<String>("id")
        element<List<String>>("versionRange") // Change versionRange to List<String>
    }

    override fun serialize(encoder: Encoder, value: ModDependencyImpl) {
        val composite = encoder.beginStructure(descriptor)
        composite.encodeStringElement(descriptor, 0, value.kind.name)
        composite.encodeStringElement(descriptor, 1, value.modId)
        composite.encodeSerializableElement(descriptor, 2, ListSerializer(String.serializer()), value.matcherStringList)
        composite.endStructure(descriptor)
    }

    override fun deserialize(decoder: Decoder): ModDependencyImpl {
        val input = decoder.beginStructure(descriptor)
        var kind: ModDependency.Kind? = null
        var id: String? = null
        var versionMatchers: List<String> = emptyList()

        loop@ while (true) {
            when (val index = input.decodeElementIndex(descriptor)) {
                CompositeDecoder.DECODE_DONE -> break@loop
                0 -> kind = ModDependency.Kind.valueOf(input.decodeStringElement(descriptor, index))
                1 -> id = input.decodeStringElement(descriptor, index)
                2 -> versionMatchers = input.decodeSerializableElement(descriptor, 2, ListSerializer(String.serializer()))
                else -> throw SerializationException("Unexpected index: $index")
            }
        }

        input.endStructure(descriptor)

        if (kind == null || id == null) {
            throw SerializationException("Missing required fields: kind or id")
        }

        return ModDependencyImpl(kind, id, versionMatchers)
    }
}