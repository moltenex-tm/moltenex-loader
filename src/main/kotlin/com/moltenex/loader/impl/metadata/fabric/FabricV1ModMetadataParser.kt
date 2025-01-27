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
/*
import com.beust.klaxon.JsonReader
import com.moltenex.loader.api.util.version.Version
import net.fabricmc.loader.api.VersionParsingException
import net.fabricmc.loader.api.metadata.*
import net.fabricmc.loader.impl.lib.gson.JsonReader
import net.fabricmc.loader.impl.lib.gson.JsonToken
import net.fabricmc.loader.impl.metadata.*
import net.fabricmc.loader.impl.metadata.ContactInfoBackedPerson
import net.fabricmc.loader.impl.metadata.ParseMetadataException.MissingField
import net.fabricmc.loader.impl.metadata.SimplePerson
import net.fabricmc.loader.impl.metadata.V1ModMetadata
import net.fabricmc.loader.impl.metadata.V1ModMetadata.*
import net.fabricmc.loader.impl.util.version.VersionParser.parse
import java.io.IOException
import java.util.*

internal object FabricV1ModMetadataParser {
    /**
     * Reads a `fabric.mod.json` file of schema version `1`.
     *
     * @param logger the logger to print warnings to
     * @param reader the json reader to read the file with
     * @return the metadata of this file, null if the file could not be parsed
     * @throws IOException         if there was any issue reading the file
     */
    @JvmStatic
	@Throws(IOException::class, ParseMetadataException::class)
    fun parse(reader: JsonReader): LoaderModMetadata {
        val warnings: MutableList<ParseWarning> = ArrayList()

        // All the values the `fabric.mod.json` may contain:
        // Required
        var id: String? = null
        var version: Version? = null

        // Optional (id provides)
        val provides: MutableList<String> = ArrayList()

        // Optional (mod loading)
        var environment = ModEnvironment.UNIVERSAL // Default is always universal
        val entrypoints: MutableMap<String, List<EntrypointMetadata>> = HashMap()
        val jars: MutableList<NestedJarEntry> = ArrayList()
        val mixins: MutableList<MixinEntry> = ArrayList()
        var accessWidener: String? = null

        // Optional (dependency resolution)
        val dependencies: MutableList<ModDependency> = ArrayList()
        // Happy little accidents
        var hasRequires = false

        // Optional (metadata)
        var name: String? = null
        var description: String? = null
        val authors: MutableList<Person> = ArrayList()
        val contributors: MutableList<Person> = ArrayList()
        var contact: ContactInformation? = null
        val license: MutableList<String> = ArrayList()
        var icon: IconEntry? = null

        // Optional (language adapter providers)
        val languageAdapters: MutableMap<String, String> = HashMap()

        // Optional (custom values)
        val customValues: MutableMap<String, CustomValue> = HashMap()

        while (reader.hasNext()) {
            // Work our way from required to entirely optional
            when (val key = reader.nextName()) {
                "schemaVersion" -> {
                    // Duplicate field, make sure it matches our current schema version
                    if (reader.peek() != JsonToken.NUMBER) {
                        throw ParseMetadataException("Duplicate \"schemaVersion\" field is not a number", reader)
                    }

                    val read = reader.nextInt()

                    if (read != 1) {
                        throw ParseMetadataException(
                            "Duplicate \"schemaVersion\" field does not match the predicted schema version of 1. Duplicate field value is $read", reader
                        )
                    }
                }

                "id" -> {
                    if (reader.peek() != JsonToken.STRING) {
                        throw ParseMetadataException(
                            "Mod id must be a non-empty string with a length of 3-64 characters.",
                            reader
                        )
                    }

                    id = reader.nextString()
                }

                "version" -> {
                    if (reader.peek() != JsonToken.STRING) {
                        throw ParseMetadataException("Version must be a non-empty string", reader)
                    }

                    try {
                        version = parse(reader.nextString(), false)
                    } catch (e: VersionParsingException) {
                        throw ParseMetadataException("Failed to parse version", e)
                    }
                }

                "provides" -> readProvides(reader, provides)
                "environment" -> {
                    if (reader.peek() != JsonToken.STRING) {
                        throw ParseMetadataException("Environment must be a string", reader)
                    }

                    environment = readEnvironment(reader)
                }

                "entrypoints" -> readEntrypoints(warnings, reader, entrypoints)
                "jars" -> readNestedJarEntries(warnings, reader, jars)
                "mixins" -> readMixinConfigs(warnings, reader, mixins)
                "accessWidener" -> {
                    if (reader.peek() != JsonToken.STRING) {
                        throw ParseMetadataException("Access Widener file must be a string", reader)
                    }

                    accessWidener = reader.nextString()
                }

                "depends" -> readDependenciesContainer(reader, ModDependency.Kind.DEPENDS, dependencies)
                "recommends" -> readDependenciesContainer(reader, ModDependency.Kind.RECOMMENDS, dependencies)
                "suggests" -> readDependenciesContainer(reader, ModDependency.Kind.SUGGESTS, dependencies)
                "conflicts" -> readDependenciesContainer(reader, ModDependency.Kind.CONFLICTS, dependencies)
                "breaks" -> readDependenciesContainer(reader, ModDependency.Kind.BREAKS, dependencies)
                "dependencyHandler" -> readDependenciesContainer(reader, ModDependency.Kind.DEPENDS, dependencies)

                "name" -> {
                    if (reader.peek() != JsonToken.STRING) {
                        throw ParseMetadataException("Mod name must be a string", reader)
                    }

                    name = reader.nextString()
                }

                "description" -> {
                    if (reader.peek() != JsonToken.STRING) {
                        throw ParseMetadataException("Mod description must be a string", reader)
                    }

                    description = reader.nextString()
                }

                "authors" -> readPeople(warnings, reader, authors)
                "contributors" -> readPeople(warnings, reader, contributors)
                "contact" -> contact = readContactInfo(reader)
                "license" -> readLicense(reader, license)
                "icon" -> icon = readIcon(reader)
                "languageAdapters" -> readLanguageAdapters(reader, languageAdapters)
                "custom" -> readCustomValues(reader, customValues)
                else -> {
                    if (!ModMetadataParser.IGNORED_KEYS.contains(key)) {
                        warnings.add(ParseWarning(reader.lineNumber, reader.column, key, "Unsupported root entry"))
                    }

                    reader.skipValue()
                }
            }
        }

        // Validate all required fields are resolved
        if (id == null) {
            throw MissingField("id")
        }

        if (version == null) {
            throw MissingField("version")
        }

        ModMetadataParser.logWarningMessages(id, warnings)

        return V1ModMetadata(
            id, version, provides,
            environment, entrypoints, jars, mixins, accessWidener.toString(),
            dependencies, hasRequires,
            name, description, authors, contributors, contact, license, icon, languageAdapters, customValues
        )
    }

    @Throws(IOException::class, ParseMetadataException::class)
    private fun readProvides(reader: JsonReader, provides: MutableList<String>) {
        if (reader.peek() != JsonToken.BEGIN_ARRAY) {
            throw ParseMetadataException("Provides must be an array")
        }

        reader.beginArray()

        while (reader.hasNext()) {
            if (reader.peek() != JsonToken.STRING) {
                throw ParseMetadataException("Provided id must be a string", reader)
            }

            provides.add(reader.nextString())
        }

        reader.endArray()
    }

    @Throws(ParseMetadataException::class, IOException::class)
    private fun readEnvironment(reader: JsonReader): ModEnvironment {
        val environment = reader.nextString().lowercase()

        return if (environment.isEmpty() || environment == "*") {
            ModEnvironment.UNIVERSAL
        } else if (environment == "client") {
            ModEnvironment.CLIENT
        } else if (environment == "server") {
            ModEnvironment.SERVER
        } else {
            throw ParseMetadataException("Invalid environment type: $environment!", reader)
        }
    }

    @Throws(IOException::class, ParseMetadataException::class)
    private fun readEntrypoints(
        warnings: MutableList<ParseWarning>,
        reader: JsonReader,
        entrypoints: MutableMap<String, List<EntrypointMetadata>>
    ) {
        // Entrypoints must be an object
        if (reader.peek() != JsonToken.BEGIN_OBJECT) {
            throw ParseMetadataException("Entrypoints must be an object", reader)
        }

        reader.beginObject()

        while (reader.hasNext()) {
            val key = reader.nextName()

            val metadata: MutableList<EntrypointMetadata> = ArrayList()

            if (reader.peek() != JsonToken.BEGIN_ARRAY) {
                throw ParseMetadataException("Entrypoint list must be an array!", reader)
            }

            reader.beginArray()

            while (reader.hasNext()) {
                var adapter: String? = "default"
                var value: String? = null

                // Entrypoints may be specified directly as a string or as an object to allow specification of the language adapter to use.
                when (reader.peek()) {
                    JsonToken.STRING -> value = reader.nextString()
                    JsonToken.BEGIN_OBJECT -> {
                        reader.beginObject()

                        while (reader.hasNext()) {
                            when (val entryKey = reader.nextName()) {
                                "adapter" -> adapter = reader.nextString()
                                "value" -> value = reader.nextString()
                                else -> {
                                    warnings.add(
                                        ParseWarning(
                                            reader.lineNumber,
                                            reader.column,
                                            entryKey,
                                            "Invalid entry in entrypoint metadata"
                                        )
                                    )
                                    reader.skipValue()
                                }
                            }
                        }

                        reader.endObject()
                    }

                    else -> throw ParseMetadataException(
                        "Entrypoint must be a string or object with \"value\" field",
                        reader
                    )
                }

                if (value == null) {
                    throw MissingField("Entrypoint value must be present")
                }

                metadata.add(EntrypointMetadataImpl(adapter.toString(), value))
            }

            reader.endArray()

            // Empty arrays are acceptable, do not check if the List of metadata is empty
            entrypoints[key] = metadata
        }

        reader.endObject()
    }

    @Throws(IOException::class, ParseMetadataException::class)
    private fun readNestedJarEntries(
        warnings: MutableList<ParseWarning>,
        reader: JsonReader,
        jars: MutableList<NestedJarEntry>
    ) {
        if (reader.peek() != JsonToken.BEGIN_ARRAY) {
            throw ParseMetadataException("Jar entries must be in an array", reader)
        }

        reader.beginArray()

        while (reader.hasNext()) {
            if (reader.peek() != JsonToken.BEGIN_OBJECT) {
                throw ParseMetadataException("Invalid type for JAR entry!", reader)
            }

            reader.beginObject()
            var file: String? = null

            while (reader.hasNext()) {
                val key = reader.nextName()

                if (key == "file") {
                    if (reader.peek() != JsonToken.STRING) {
                        throw ParseMetadataException("\"file\" entry in jar object must be a string", reader)
                    }

                    file = reader.nextString()
                } else {
                    warnings.add(ParseWarning(reader.lineNumber, reader.column, key, "Invalid entry in jar entry"))
                    reader.skipValue()
                }
            }

            reader.endObject()

            if (file == null) {
                throw ParseMetadataException("Missing mandatory key 'file' in JAR entry!", reader)
            }

            jars.add(JarEntry(file))
        }

        reader.endArray()
    }

    @Throws(IOException::class, ParseMetadataException::class)
    private fun readMixinConfigs(
        warnings: MutableList<ParseWarning>,
        reader: JsonReader,
        mixins: MutableList<MixinEntry>
    ) {
        if (reader.peek() != JsonToken.BEGIN_ARRAY) {
            throw ParseMetadataException("Mixin configs must be in an array", reader)
        }

        reader.beginArray()

        while (reader.hasNext()) {
            when (reader.peek()) {
                JsonToken.STRING ->                // All mixin configs specified via string are assumed to be universal
                    mixins.add(MixinEntry(reader.nextString(), ModEnvironment.UNIVERSAL))

                JsonToken.BEGIN_OBJECT -> {
                    reader.beginObject()

                    var config: String? = null
                    var environment: ModEnvironment? = null

                    while (reader.hasNext()) {
                        when (val key = reader.nextName()) {
                            "environment" -> environment = readEnvironment(reader)
                            "config" -> {
                                if (reader.peek() != JsonToken.STRING) {
                                    throw ParseMetadataException("Value of \"config\" must be a string", reader)
                                }

                                config = reader.nextString()
                            }

                            else -> {
                                warnings.add(
                                    ParseWarning(
                                        reader.lineNumber,
                                        reader.column,
                                        key,
                                        "Invalid entry in mixin config entry"
                                    )
                                )
                                reader.skipValue()
                            }
                        }
                    }

                    reader.endObject()

                    if (environment == null) {
                        environment = ModEnvironment.UNIVERSAL // Default to universal
                    }

                    if (config == null) {
                        throw MissingField("Missing mandatory key 'config' in mixin entry!")
                    }

                    mixins.add(MixinEntry(config, environment))
                }

                else -> {
                    warnings.add(ParseWarning(reader.lineNumber, reader.column, "Invalid mixin entry type"))
                    reader.skipValue()
                }
            }
        }

        reader.endArray()
    }

    @Throws(IOException::class, ParseMetadataException::class)
    private fun readDependenciesContainer(
        reader: JsonReader,
        kind: ModDependency.Kind,
        out: MutableList<ModDependency>
    ) {
        if (reader.peek() != JsonToken.BEGIN_OBJECT) {
            throw ParseMetadataException("Dependency container must be an object!", reader)
        }

        reader.beginObject()

        while (reader.hasNext()) {
            val modId = reader.nextName()
            val matcherStringList: MutableList<String> = ArrayList()

            when (reader.peek()) {
                JsonToken.STRING -> matcherStringList.add(reader.nextString())
                JsonToken.BEGIN_ARRAY -> {
                    reader.beginArray()

                    while (reader.hasNext()) {
                        if (reader.peek() != JsonToken.STRING) {
                            throw ParseMetadataException(
                                "Dependency version range array must only contain string values",
                                reader
                            )
                        }

                        matcherStringList.add(reader.nextString())
                    }

                    reader.endArray()
                }

                else -> throw ParseMetadataException(
                    "Dependency version range must be a string or string array!",
                    reader
                )
            }

            try {
                out.add(ModDependencyImpl(kind, modId, matcherStringList))
            } catch (e: VersionParsingException) {
                throw ParseMetadataException(e)
            }
        }

        reader.endObject()
    }

    @Throws(IOException::class, ParseMetadataException::class)
    private fun readPeople(warnings: MutableList<ParseWarning>, reader: JsonReader, people: MutableList<Person>) {
        if (reader.peek() != JsonToken.BEGIN_ARRAY) {
            throw ParseMetadataException("List of people must be an array", reader)
        }

        reader.beginArray()

        while (reader.hasNext()) {
            when (reader.peek()) {
                JsonToken.STRING ->                // Just a name
                    people.add(SimplePerson(reader.nextString()))

                JsonToken.BEGIN_OBJECT -> {
                    // Map-backed impl
                    reader.beginObject()
                    // Name is required
                    var personName: String? = null
                    var contactInformation: ContactInformation? = null

                    while (reader.hasNext()) {
                        when (val key = reader.nextName()) {
                            "name" -> {
                                if (reader.peek() != JsonToken.STRING) {
                                    throw ParseMetadataException(
                                        "Name of person in dependency container must be a string",
                                        reader
                                    )
                                }

                                personName = reader.nextString()
                            }

                            "contact" -> contactInformation = readContactInfo(reader)
                            else -> {
                                // Ignore unsupported keys
                                warnings.add(
                                    ParseWarning(
                                        reader.lineNumber,
                                        reader.column,
                                        key,
                                        "Invalid entry in person"
                                    )
                                )
                                reader.skipValue()
                            }
                        }
                    }

                    reader.endObject()

                    if (personName == null) {
                        throw MissingField("Person object must have a 'name' field!")
                    }

                    if (contactInformation == null) {
                        contactInformation = ContactInformation.EMPTY // Empty if not specified
                    }

                    people.add(ContactInfoBackedPerson(personName, contactInformation))
                }

                else -> throw ParseMetadataException("Person type must be an object or string!", reader)
            }
        }

        reader.endArray()
    }

    @Throws(IOException::class, ParseMetadataException::class)
    private fun readContactInfo(reader: JsonReader): ContactInformation {
        if (reader.peek() != JsonToken.BEGIN_OBJECT) {
            throw ParseMetadataException("Contact info must in an object", reader)
        }

        reader.beginObject()

        val map: MutableMap<String, String> = HashMap()

        while (reader.hasNext()) {
            val key = reader.nextName()

            if (reader.peek() != JsonToken.STRING) {
                throw ParseMetadataException("Contact information entries must be a string", reader)
            }

            map[key] = reader.nextString()
        }

        reader.endObject()

        // Map is wrapped as unmodifiable in the contact info impl
        return ContactInformationImpl(map)
    }

    @Throws(IOException::class, ParseMetadataException::class)
    private fun readLicense(reader: JsonReader, license: MutableList<String>) {
        when (reader.peek()) {
            JsonToken.STRING -> license.add(reader.nextString())
            JsonToken.BEGIN_ARRAY -> {
                reader.beginArray()

                while (reader.hasNext()) {
                    if (reader.peek() != JsonToken.STRING) {
                        throw ParseMetadataException("List of licenses must only contain strings", reader)
                    }

                    license.add(reader.nextString())
                }

                reader.endArray()
            }

            else -> throw ParseMetadataException("License must be a string or array of strings!", reader)
        }
    }

    @Throws(IOException::class, ParseMetadataException::class)
    private fun readIcon(reader: JsonReader): IconEntry {
        when (reader.peek()) {
            JsonToken.STRING -> return Single(reader.nextString())
            JsonToken.BEGIN_OBJECT -> {
                reader.beginObject()

                val iconMap: SortedMap<Int, String> = TreeMap(Comparator.naturalOrder())

                while (reader.hasNext()) {
                    val key = reader.nextName()

                    val size: Int

                    try {
                        size = key.toInt()
                    } catch (e: NumberFormatException) {
                        throw ParseMetadataException("Could not parse icon size '$key'!", e)
                    }

                    if (size < 1) {
                        throw ParseMetadataException("Size must be positive!", reader)
                    }

                    if (reader.peek() != JsonToken.STRING) {
                        throw ParseMetadataException("Icon path must be a string", reader)
                    }

                    iconMap[size] = reader.nextString()
                }

                reader.endObject()

                if (iconMap.isEmpty()) {
                    throw ParseMetadataException("Icon object must not be empty!", reader)
                }

                return MapEntry(iconMap)
            }

            else -> throw ParseMetadataException("Icon entry must be an object or string!", reader)
        }
    }

    @Throws(IOException::class, ParseMetadataException::class)
    private fun readLanguageAdapters(reader: JsonReader, languageAdapters: MutableMap<String, String>) {
        if (reader.peek() != JsonToken.BEGIN_OBJECT) {
            throw ParseMetadataException("Language adapters must be in an object", reader)
        }

        reader.beginObject()

        while (reader.hasNext()) {
            val adapter = reader.nextName()

            if (reader.peek() != JsonToken.STRING) {
                throw ParseMetadataException("Value of language adapter entry must be a string", reader)
            }

            languageAdapters[adapter] = reader.nextString()
        }

        reader.endObject()
    }

    @Throws(IOException::class, ParseMetadataException::class)
    private fun readCustomValues(reader: JsonReader, customValues: MutableMap<String, CustomValue>) {
        if (reader.peek() != JsonToken.BEGIN_OBJECT) {
            throw ParseMetadataException("Custom values must be in an object!", reader)
        }

        reader.beginObject()

        while (reader.hasNext()) {
            customValues[reader.nextName()] = CustomValueImpl.readCustomValue(reader)
        }

        reader.endObject()
    }

}*/