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
package net.fabricmc.loader.impl.metadata
/*
import com.beust.klaxon.*
import moltenex.loader.api.version.Version
import net.fabricmc.loader.api.VersionParsingException
import net.fabricmc.loader.api.metadata.*
import net.fabricmc.loader.impl.metadata.ParseMetadataException.MissingField
import net.fabricmc.loader.impl.metadata.V1ModMetadata.EntrypointMetadataImpl
import java.io.IOException
import java.util.*
import kotlin.Comparator
import kotlin.collections.ArrayList
import kotlin.collections.HashMap

internal object V1ModMetadataParser {
    /**
     * Reads a `fabric.mod.json` file of schema version `1` using Klaxon.
     *
     * @param reader the JSON reader to read the file with
     * @return the metadata of this file, null if the file could not be parsed
     * @throws IOException if there was any issue reading the file
     */
    @JvmStatic
    @Throws(IOException::class, ParseMetadataException::class)
    fun parse(reader: JsonReader): LoaderModMetadata {
        val warnings: MutableList<ParseWarning> = ArrayList()

        // Initialize the required fields
        var id: String? = null
        var version: Version? = null

        // Optional fields
        val provides: MutableList<String> = ArrayList()
        var environment = ModEnvironment.UNIVERSAL // Default is always universal
        val entrypoints: MutableMap<String, List<EntrypointMetadata>> = HashMap()
        val jars: MutableList<NestedJarEntry> = ArrayList()
        val mixins: MutableList<V1ModMetadata.MixinEntry> = ArrayList()
        var accessWidener: String? = null
        val dependencies: MutableList<ModDependency> = ArrayList()
        var hasRequires = false

        var name: String? = null
        var description: String? = null
        val authors: MutableList<Person> = ArrayList()
        val contributors: MutableList<Person> = ArrayList()
        var contact: ContactInformation? = null
        val license: MutableList<String> = ArrayList()
        var icon: V1ModMetadata.IconEntry? = null
        val languageAdapters: MutableMap<String, String> = HashMap()
        val customValues: MutableMap<String, CustomValue> = HashMap()

        // Use Klaxon to parse the JSON
        val json = Klaxon().parse<JsonObject>(reader) ?: throw ParseMetadataException("Failed to parse JSON", reader)

        json.forEach { (key, value) ->
            when (key) {
                "schemaVersion" -> {
                    val schemaVersion = value as? Int ?: throw ParseMetadataException("Invalid schema version", reader)
                    if (schemaVersion != 1) {
                        throw ParseMetadataException("Expected schema version 1, found $schemaVersion", reader)
                    }
                }
                "id" -> {
                    id = value as? String ?: throw ParseMetadataException("Mod id must be a string", reader)
                }
                "version" -> {
                    version = try {
                        parse(value as String, false)
                    } catch (e: VersionParsingException) {
                        throw ParseMetadataException("Failed to parse version", e)
                    }
                }
                "provides" -> provides.addAll(value as List<String>)
                "environment" -> {
                    environment = when (val env = value as String) {
                        "universal" -> ModEnvironment.UNIVERSAL
                        "client" -> ModEnvironment.CLIENT
                        "server" -> ModEnvironment.SERVER
                        else -> {
                            warnings.add(ParseWarning("Invalid environment type: $env"))
                            ModEnvironment.UNIVERSAL
                        }
                    }
                }
                "entrypoints" -> readEntrypoints(warnings, value as JsonObject, entrypoints)
                "jars" -> readNestedJarEntries(warnings, value as JsonArray<JsonObject>, jars)
                "mixins" -> readMixinConfigs(warnings, value as JsonArray<JsonObject>, mixins)
                "accessWidener" -> accessWidener = value as? String
                "depends" -> readDependenciesContainer(value, ModDependency.Kind.DEPENDS, dependencies)
                "recommends" -> readDependenciesContainer(value , ModDependency.Kind.RECOMMENDS, dependencies)
                "suggests" -> readDependenciesContainer(value, ModDependency.Kind.SUGGESTS, dependencies)
                "conflicts" -> readDependenciesContainer(value, ModDependency.Kind.CONFLICTS, dependencies)
                "breaks" -> readDependenciesContainer(value, ModDependency.Kind.BREAKS, dependencies)
                "dependencyHandler" -> {
                    hasRequires = true
                }
                "name" -> name = value as? String
                "description" -> description = value as? String
                "authors" -> readPeople(warnings, value as JsonArray<JsonObject>, authors)
                "contributors" -> readPeople(warnings, value as JsonArray<JsonObject>, contributors)
                "contact" -> contact = readContactInfo(value as JsonObject)
                "license" -> license = readLicense(value as JsonObject)
                "icon" -> icon = readIcon(value as JsonObject)
                "languageAdapters" -> readLanguageAdapters(value as JsonObject, languageAdapters)
                "custom" -> readCustomValues(value as JsonObject, customValues)
                else -> {
                    if (!ModMetadataParser.IGNORED_KEYS.contains(key)) {
                        warnings.add(ParseWarning("Unsupported root entry: $key"))
                    }
                }
            }
        }

        // Validate all required fields are resolved
        if (id == null) throw ParseMetadataException.MissingField("id")
        if (version == null) throw ParseMetadataException.MissingField("version")

        ModMetadataParser.logWarningMessages(id, warnings)

        return V1ModMetadata(
            id, version, provides, environment, entrypoints, jars, mixins, accessWidener,
            dependencies, hasRequires, name, description, authors, contributors, contact, license, icon, languageAdapters, customValues
        )
    }

    @Throws(IOException::class, ParseMetadataException::class)
    private fun readProvides(json: JsonObject, provides: MutableList<String>) {
        // Check if "provides" key exists and is an array
        val providesArray = json["provides"] as? JsonArray<*>
            ?: throw ParseMetadataException("Provides must be an array")

        for (item in providesArray) {
            if (item !is String) {
                throw ParseMetadataException("Provided id must be a string")
            }
            provides.add(item)
        }
    }

    @Throws(ParseMetadataException::class)
    private fun readEnvironment(jsonValue: Any?): ModEnvironment {
        // Ensure that the value is a string (Klaxon uses JsonValue to wrap data)
        val environment = jsonValue?.string?.lowercase()

        // Check for null or invalid input and handle accordingly
        return when {
            environment.isNullOrEmpty() || environment == "*" -> ModEnvironment.UNIVERSAL
            environment == "client" -> ModEnvironment.CLIENT
            environment == "server" -> ModEnvironment.SERVER
            else -> throw ParseMetadataException("Invalid environment type: $environment!")
        }
    }

    //TODO readEntryPoints
    @Throws(ParseMetadataException::class)
    private fun readEntrypoints(
        warnings: MutableList<ParseWarning>,
        json: Any, // Accept Klaxon JsonObject as input
        entrypoints: MutableMap<String, List<EntrypointMetadata>>
    ) {
        if (json !is JsonObject) {
            throw ParseMetadataException("Entrypoints must be an object")
        }

        // Iterate over the entries in the JSON object
        for ((key, value) in json) {
            val metadata: MutableList<EntrypointMetadata> = mutableListOf()

            if (value !is JsonArray<*>) {
                throw ParseMetadataException("Entrypoint list must be an array!", key)
            }

            // Iterate over each item in the array
            for (item in value) {
                var adapter: String? = "default"
                var entryValue: String? = null

                when (item) {
                    is JsonObject -> {
                        // Parse the object
                        val entryAdapter = item.string("adapter") ?: "default"
                        entryValue = item.string("value")

                        // Add warnings for invalid fields
                        item.forEach { (entryKey, _) ->
                            if (entryKey != "adapter" && entryKey != "value") {
                                warnings.add(
                                    ParseWarning(
                                        line = 0, // You can set the line number from the actual parsing context
                                        column = 0, // Same with the column
                                        key = entryKey,
                                        reason = "Invalid entry in entrypoint metadata"
                                    )
                                )
                            }
                        }
                    }
                    else -> throw ParseMetadataException(
                        "Entrypoint must be a string or object with \"value\" field",
                        key
                    )
                }

                // Ensure the value is present
                if (entryValue == null) {
                    throw MissingField("Entrypoint value must be present")
                }

                metadata.add(EntrypointMetadataImpl(adapter.toString(), entryValue))
            }

            // Allow empty arrays
            entrypoints[key] = metadata
        }
    }

    @Throws(ParseMetadataException::class)
    private fun readNestedJarEntries(
        warnings: MutableList<ParseWarning>,
        json: JsonArray<JsonObject>,  // Using JsonArray instead of JsonReader
        jars: MutableList<NestedJarEntry>
    ) {
        // Ensure the input is an array
        if (json.isEmpty()) {
            throw ParseMetadataException("Jar entries must be in an array")
        }

        for (entry in json) {
            // Each entry must be an object
            val jarEntryObj = entry ?: throw ParseMetadataException("Invalid type for JAR entry!")

            var file: String? = null

            // Iterate through each key in the jar entry object
            for ((key, value) in jarEntryObj) {
                when (key) {
                    "file" -> {
                        if (value == null) {
                            throw ParseMetadataException("\"file\" entry in jar object must be a string")
                        }
                        file = value.toString()
                    }
                    else -> {
                        warnings.add(ParseWarning(259,2 ,"Invalid entry in jar entry: $key"))
                    }
                }
            }

            // Check if the "file" entry was provided
            if (file == null) {
                throw ParseMetadataException("Missing mandatory key 'file' in JAR entry!")
            }

            // Add the parsed entry to the jars list
            jars.add(V1ModMetadata.JarEntry(file))
        }
    }

    @Throws(ParseMetadataException::class)
    private fun readMixinConfigs(
        warnings: MutableList<ParseWarning>,
        json: Any, // Accept Klaxon JsonArray as input
        mixins: MutableList<V1ModMetadata.MixinEntry>
    ) {
        if (json !is JsonArray<*>) {
            throw ParseMetadataException("Mixin configs must be in an array")
        }

        // Iterate over the array
        for (item in json) {
            when (item) {
                is JsonValue -> {
                    // Handle item as a string if it's a JsonValue
                    if (item.string != null) {
                        // All mixin configs specified via string are assumed to be universal
                        mixins.add(V1ModMetadata.MixinEntry(item.string!!, ModEnvironment.UNIVERSAL))
                    } else if (item.obj != null) {
                        // Process as a JsonObject if it's an object
                        var config: String? = null
                        var environment: ModEnvironment? = null

                        // Process the object
                        for ((key, value) in item.obj!!) {
                            when (key) {
                                "environment" -> environment = readEnvironment(value)
                                "config" -> {
                                    config = value.toString()
                                }

                                else -> {
                                    warnings.add(
                                        ParseWarning(
                                            line = 0, // Adjust to the actual line number if available
                                            column = 0, // Adjust column as well
                                            key = key,
                                            reason = "Invalid entry in mixin config entry"
                                        )
                                    )
                                }
                            }
                        }

                        // If environment is null, default to universal
                        if (environment == null) {
                            environment = ModEnvironment.UNIVERSAL
                        }

                        // Ensure the config is present
                        if (config == null) {
                            throw MissingField("Missing mandatory key 'config' in mixin entry!")
                        }

                        // Add the MixinEntry
                        mixins.add(V1ModMetadata.MixinEntry(config, environment))
                    }
                }

                else -> {
                    warnings.add(ParseWarning(
                        line = 0, // Adjust to the actual line number if available
                        column = 0, // Adjust column as well
                        key = "Invalid mixin entry type",
                        reason = "Invalid mixin entry type"
                    ))
                }
            }
        }
    }

    @Throws(ParseMetadataException::class)
    private fun readDependenciesContainer(
        json: JsonObject,  // Using JsonObject instead of JsonReader
        kind: ModDependency.Kind,
        out: MutableList<ModDependency>
    ) {
        // Ensure the input is an object
        if (json.isEmpty()) {
            throw ParseMetadataException("Dependency container must be an object!")
        }

        // Iterate over each entry in the object
        for ((modId, value) in json) {
            val matcherStringList: MutableList<String> = ArrayList()

            when (value) {
                is String -> {
                    // Handle string value directly
                    matcherStringList.add(value)
                }

                is JsonArray<*> -> {
                    // Handle array of strings
                    for (item in value) {
                        if (item is String) {
                            matcherStringList.add(item)
                        } else {
                            throw ParseMetadataException(
                                "Dependency version range array must only contain string values"
                            )
                        }
                    }
                }

                else -> throw ParseMetadataException(
                    "Dependency version range must be a string or string array!"
                )
            }

            try {
                out.add(ModDependencyImpl(kind, modId, matcherStringList))
            } catch (e: VersionParsingException) {
                throw ParseMetadataException(e)
            }
        }
    }

    @Throws(ParseMetadataException::class)
    private fun readPeople(
        warnings: MutableList<ParseWarning>,
        json: JsonArray<*>,  // Using JsonArray for the list of people
        people: MutableList<Person>
    ) {
        if (json.isEmpty()) {
            throw ParseMetadataException("List of people must be an array")
        }

        for (item in json) {
            when (item) {
                is String -> {
                    // Just a name
                    people.add(SimplePerson(item))
                }

                is JsonObject -> {
                    // Map-backed impl
                    val personName = item.string("name")
                    val contactInformation = item.obj("contact")?.let { readContactInfo(it) }

                    if (personName == null) {
                        throw MissingField("Person object must have a 'name' field!")
                    }

                    if (contactInformation == null) {
                        // Empty if not specified
                        people.add(ContactInfoBackedPerson(personName, ContactInformation.EMPTY))
                    } else {
                        people.add(ContactInfoBackedPerson(personName, contactInformation))
                    }
                }

                else -> throw ParseMetadataException("Person type must be an object or string!")
            }
        }
    }

    @Throws(ParseMetadataException::class)
    private fun readContactInfo(
        json: JsonObject  // Using JsonObject to represent the contact info
    ): ContactInformation {
        if (json.isEmpty()) {
            throw ParseMetadataException("Contact info must be an object")
        }

        val map: MutableMap<String, String> = HashMap()

        for ((key, value) in json) {
            if (value !is String) {
                throw ParseMetadataException("Contact information entries must be a string")
            }
            map[key] = value
        }

        // Map is wrapped as unmodifiable in the contact info impl
        return ContactInformationImpl(map)
    }

    @Throws(ParseMetadataException::class)
    private fun readLicense(
        json: Any,  // Using `Any` to allow both `String` or `JsonArray`
        license: MutableList<String>
    ) {
        when (json) {
            is String -> {
                license.add(json)
            }
            is JsonArray<*> -> {
                json.forEach {
                    if (it !is String) {
                        throw ParseMetadataException("List of licenses must only contain strings")
                    }
                    license.add(it)
                }
            }
            else -> throw ParseMetadataException("License must be a string or array of strings!")
        }
    }

    @Throws(ParseMetadataException::class)
    private fun readIcon(json: Any): V1ModMetadata.IconEntry {
        when (json) {
            is String -> {
                // Return Single if the JSON is a string
                return V1ModMetadata.Single(json)
            }
            is JsonObject -> {
                val iconMap: SortedMap<Int, String> = TreeMap(Comparator.naturalOrder())

                // Iterate over each field in the JSON object
                for ((key, value) in json) {
                    val size: Int
                    try {
                        size = key.toInt()  // Convert the key (which is a String) to an Int
                    } catch (e: NumberFormatException) {
                        throw ParseMetadataException("Could not parse icon size '$key'!", e)
                    }

                    if (size < 1) {
                        throw ParseMetadataException("Size must be positive!")
                    }

                    if (value !is String) {
                        throw ParseMetadataException("Icon path must be a string")
                    }

                    iconMap[size] = value
                }

                if (iconMap.isEmpty()) {
                    throw ParseMetadataException("Icon object must not be empty!")
                }

                return V1ModMetadata.MapEntry(iconMap)
            }
            else -> throw ParseMetadataException("Icon entry must be an object or string!")
        }
    }

    @Throws(ParseMetadataException::class)
    private fun readLanguageAdapters(json: Any, languageAdapters: MutableMap<String, String>) {
        if (json !is JsonObject) {
            throw ParseMetadataException("Language adapters must be in an object")
        }

        // Iterate over the JSON object
        for ((adapter, value) in json) {
            if (value !is String) {
                throw ParseMetadataException("Value of language adapter entry must be a string")
            }
            languageAdapters[adapter] = value
        }
    }

    @Throws(ParseMetadataException::class)
    private fun readCustomValues(json: Any, customValues: MutableMap<String, CustomValue>) {
        if (json !is JsonObject) {
            throw ParseMetadataException("Custom values must be in an object!")
        }

        // Iterate over the JSON object
        for ((key, value) in json) {
            // We assume `CustomValueImpl.readCustomValue()` can process the value
            customValues[key] = CustomValueImpl.readCustomValue(value)
        }
    }
}*/