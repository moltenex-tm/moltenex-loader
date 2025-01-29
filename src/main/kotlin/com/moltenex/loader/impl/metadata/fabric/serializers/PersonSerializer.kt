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

import com.moltenex.loader.impl.metadata.fabric.common.ContactInfoBackedPerson
import com.moltenex.loader.impl.metadata.fabric.common.ContactInformationImpl
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.*
import net.fabricmc.loader.api.metadata.ContactInformation

object PersonSerializer : KSerializer<ContactInfoBackedPerson> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("Person") {
        element<String>("name")
        element<ContactInformation>("contact", isOptional = true)
    }

    override fun deserialize(decoder: Decoder): ContactInfoBackedPerson {
        val input = decoder as? JsonDecoder ?: throw SerializationException("Expected JsonDecoder")

        return when (val element = input.decodeJsonElement()) {
            is JsonPrimitive -> {
                // Handle the case where the person is a string
                parsePersonFromString(element.content)
            }
            is JsonObject -> {
                // Handle the case where the person is an object
                val name = element["name"]?.jsonPrimitive?.contentOrNull ?: throw SerializationException("Missing 'name' field")
                val contactMap = linkedMapOf<String, String>().apply {
                    element["email"]?.jsonPrimitive?.contentOrNull?.let { put("email", it) }
                    element["website"]?.jsonPrimitive?.contentOrNull?.let { put("website", it) }
                }
                ContactInfoBackedPerson(name, ContactInformationImpl(contactMap))
            }
            else -> throw SerializationException("Expected a string or an object for Person")
        }
    }

    override fun serialize(encoder: Encoder, value: ContactInfoBackedPerson) {
        val output = encoder as? JsonEncoder ?: throw SerializationException("Expected JsonEncoder")

        // If there is no contact information, serialize as a string (name only)
        if (value.contact.asMap().isEmpty()) {
            output.encodeString(value.name)
        } else {
            // Serialize as a JSON object with name and contact details
            val jsonObject = buildJsonObject {
                put("name", value.name)
                value.contact.asMap().forEach { (key, contactValue) ->
                    put(key, contactValue)
                }
            }
            output.encodeJsonElement(jsonObject)
        }
    }

    private fun parsePersonFromString(person: String): ContactInfoBackedPerson {
        val parts = person.split(" ").toMutableList()
        val contactMap = mutableMapOf<String, String>()

        val websiteRegex = Regex("""https?://\S+""")
        val emailRegex = Regex("""\S+@\S+\.\S+""")

        parts.lastOrNull()?.takeIf { websiteRegex.matches(it) }?.let {
            contactMap["website"] = it
            parts.removeAt(parts.lastIndex)
        }

        parts.lastOrNull()?.takeIf { emailRegex.matches(it) }?.let {
            contactMap["email"] = it
            parts.removeAt(parts.lastIndex)
        }

        val name = parts.joinToString(" ").trim()
        if (name.isEmpty()) throw IllegalArgumentException("Name cannot be empty")

        val contact = ContactInformationImpl(contactMap)

        return ContactInfoBackedPerson(name, contact)
    }

}