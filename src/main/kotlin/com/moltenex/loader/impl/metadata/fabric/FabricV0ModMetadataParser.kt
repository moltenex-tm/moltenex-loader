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
package com.moltenex.loader.impl.metadata.fabric

import com.beust.klaxon.JsonReader
import com.beust.klaxon.Klaxon
import net.fabricmc.loader.api.metadata.ContactInformation
import net.fabricmc.loader.api.metadata.ModEnvironment
import net.fabricmc.loader.api.metadata.Person
import net.fabricmc.loader.impl.metadata.*
import net.fabricmc.loader.impl.metadata.ContactInfoBackedPerson
import net.fabricmc.loader.impl.metadata.V0ModMetadata
import java.io.IOException
import java.util.regex.Pattern

internal object FabricV0ModMetadataParser {
    private val WEBSITE_PATTERN: Pattern = Pattern.compile("\\((.+)\\)")
    private val EMAIL_PATTERN: Pattern = Pattern.compile("<(.+)>")
    private val warnings: MutableList<ParseWarning> = ArrayList()
    @Throws(IOException::class, ParseMetadataException::class)
    fun parse(reader: JsonReader): LoaderModMetadata{
        val metadata = Klaxon().parse<FabricDetailsV0>(reader)
        val id = metadata!!.id
        val version = metadata.version
        val dependencies = metadata.dependencyHandler.dependencies
        dependencies.addAll(metadata.conflicts.dependencies)
        dependencies.addAll(metadata.recommends.dependencies)
        val environment: ModEnvironment = when(metadata.side){
            "universal" -> ModEnvironment.UNIVERSAL
            "client" -> ModEnvironment.CLIENT
            "server" -> ModEnvironment.SERVER
            else -> throw ParseMetadataException("Side must be a universal, client or server", reader)
        }
        val initializer = metadata.initializer
        val initializers = metadata.initializers
        val name = metadata.name
        val description = metadata.description
        val license = metadata.license
        return V0ModMetadata(
            id,
            version,
            dependencies,
            mixins(metadata),
            environment,
            initializer,
            initializers,
            name,
            description,
            authors(metadata, warnings),
            contributors(metadata, warnings),
            links(metadata),
            license
        )
    }

    fun authors(metadata: FabricDetailsV0?, warnings: MutableList<ParseWarning>): MutableList<Person> {
        return processPeople(metadata!!.authors, warnings)
    }

    fun contributors(metadata: FabricDetailsV0?, warnings: MutableList<ParseWarning>): MutableList<Person> {
        return processPeople(metadata!!.contributors, warnings)
    }

    private fun processPeople(peopleList: Collection<String>, warnings: MutableList<ParseWarning>): MutableList<Person> {
        val collection: MutableList<Person> = ArrayList()

        for (personString in peopleList) {
            val contactMap = mutableMapOf<String, String>()
            var name: String

            var parts: Array<String?> = personString.split(" ".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()

            // Extract website
            val websiteMatcher = parts[parts.size - 1]?.let { WEBSITE_PATTERN.matcher(it) }
            if (websiteMatcher != null && websiteMatcher.matches()) {
                contactMap["website"] = websiteMatcher.group()
                parts = parts.copyOf(parts.size - 1)
            }

            // Extract email
            val emailMatcher = parts[parts.size - 1]?.let { EMAIL_PATTERN.matcher(it) }
            if (emailMatcher != null && emailMatcher.matches()) {
                contactMap["email"] = emailMatcher.group()
                parts = parts.copyOf(parts.size - 1)
            }

            // Remaining parts are treated as the name
            name = parts.filterNotNull().joinToString(" ")

            if (name.isBlank() && contactMap.isEmpty()) {
                warnings.add(ParseWarning(0, 0, "input", "No valid data found in input string"))
                throw ParseMetadataException("Input string is not valid")
            }

            collection.add(ContactInfoBackedPerson(name, ContactInformationImpl(contactMap)))
        }

        return collection
    }

    fun mixins(metadata: FabricDetailsV0?): V0ModMetadata.Mixins {
        val client = metadata!!.mixins.client
        val common = metadata.mixins.common
        val server = metadata.mixins.server
        val final = V0ModMetadata.Mixins(client, common, server)
        return final
    }

    private fun links(metadata: FabricDetailsV0?): ContactInformation {
        val contactInfo: MutableMap<String, String> = HashMap()
        contactInfo["homepage"] = metadata!!.links.homepage
        contactInfo["issues"] = metadata.links.issues
        contactInfo["sources"] = metadata.links.sources
        val final = ContactInformationImpl(contactInfo)
        return final
    }
}