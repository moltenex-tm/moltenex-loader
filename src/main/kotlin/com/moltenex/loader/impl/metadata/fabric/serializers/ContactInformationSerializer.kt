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

import com.moltenex.loader.impl.metadata.fabric.common.ContactInformationImpl
import kotlinx.serialization.*
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*

object ContactInformationSerializer : KSerializer<ContactInformationImpl> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("ContactInformation") {
        element<Map<String, String>>("data")
    }

    override fun serialize(encoder: Encoder, value: ContactInformationImpl) {
        val map = value.asMap()

        // Ensure order is maintained
        val sortedMap = LinkedHashMap<String, String>().apply { putAll(map) }

        encoder.encodeSerializableValue(serializer<Map<String, String>>(), sortedMap)
    }

    override fun deserialize(decoder: Decoder): ContactInformationImpl {
        val map = decoder.decodeSerializableValue(serializer<Map<String, String>>())

        // Maintain insertion order
        val orderedMap = LinkedHashMap(map)

        return ContactInformationImpl(orderedMap)
    }
}
