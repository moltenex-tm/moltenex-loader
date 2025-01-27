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
internal object V0ModMetadataParser {
    private val WEBSITE_PATTERN: Pattern = Pattern.compile("\\((.+)\\)")
    private val EMAIL_PATTERN: Pattern = Pattern.compile("<(.+)>")

    // Define the data classes that Klaxon will use to parse the JSON
    data class LoaderModMetadata(
        val id: String,
        val version: Version,
        val dependencies: List<ModDependency>,
        val mixins: V0ModMetadata.Mixins?,
        val environment: ModEnvironment,
        val initializer: String?,
        val initializers: List<String>,
        val name: String?,
        val description: String?,
        val authors: List<Person>,
        val contributors: List<Person>,
        val links: ContactInformation?,
        val license: String?
    )

    @JvmStatic
    @Throws(IOException::class, ParseMetadataException::class)
    fun parse(json: String): LoaderModMetadata {
        val warnings: MutableList<ParseWarning> = ArrayList()

        // All the values the `fabric.mod.json` may contain
        var id: String? = null
        var version: Version? = null
        val dependencies: MutableList<ModDependency> = ArrayList()
        var mixins: V0ModMetadata.Mixins? = null
        var environment = ModEnvironment.UNIVERSAL // Default is always universal
        var initializer: String? = null
        val initializers: MutableList<String> = ArrayList()
        var name: String? = null
        var description: String? = null
        val authors: MutableList<Person> = ArrayList()
        val contributors: MutableList<Person> = ArrayList()
        var links: ContactInformation? = null
        var license: String? = null

        // Parse the JSON using Klaxon
        val parser = Klaxon()
        val jsonObject = parser.parse<JsonObject>(json)

        if (jsonObject == null) {
            throw ParseMetadataException("Failed to parse JSON")
        }

        // Now handle each expected field and map them from the JSON to the properties
        jsonObject.forEach { key, value ->
            when (key) {
                "schemaVersion" -> {
                    if (value != 0) {
                        throw ParseMetadataException(
                            "Unexpected schemaVersion: expected 0 but got $value"
                        )
                    }
                }

                "id" -> {
                    id = value.string
                        ?: throw ParseMetadataException("Mod id must be a non-empty string")
                }

                "version" -> {
                    version = try {
                        parse(value.string ?: throw ParseMetadataException("Version must be a non-empty string"), false)
                    } catch (e: VersionParsingException) {
                        throw ParseMetadataException("Failed to parse version", e)
                    }
                }

                "dependencyHandler", "conflicts" -> {
                    readDependenciesContainer(value.array(), ModDependency.Kind.DEPENDS, dependencies)
                }

                "mixins" -> mixins = readMixins(warnings, value.array())

                "side" -> {
                    environment = when (val rawEnvironment = value.string) {
                        "universal" -> ModEnvironment.UNIVERSAL
                        "client" -> ModEnvironment.CLIENT
                        "server" -> ModEnvironment.SERVER
                        else -> {
                            warnings.add(ParseWarning(0, 0, rawEnvironment, "Invalid side type"))
                            ModEnvironment.UNIVERSAL
                        }
                    }
                }

                "initializer" -> {
                    if (initializers.isNotEmpty()) {
                        throw ParseMetadataException("initializer and initializers cannot coexist")
                    }
                    initializer = value.string
                }

                "initializers" -> {
                    if (initializer != null) {
                        throw ParseMetadataException("initializer and initializers cannot coexist")
                    }
                    value.array<String>()?.let { initializers.addAll(it) }
                        ?: throw ParseMetadataException("Initializers must be a list")
                }

                "name" -> name = value.string
                "description" -> description = value.string
                "authors" -> readPeople(warnings, value.array(), authors)
                "contributors" -> readPeople(warnings, value.array(), contributors)
                "links" -> links = readLinks(warnings, value.obj())
                "license" -> license = value.string

                else -> {
                    if (!ModMetadataParser.IGNORED_KEYS.contains(key)) {
                        warnings.add(ParseWarning(0, 0, key, "Unsupported root entry"))
                    }
                }
            }
        }

        // Validate that all required fields are set
        if (id == null) {
            throw ParseMetadataException.MissingField("id")
        }
        if (version == null) {
            throw ParseMetadataException.MissingField("version")
        }

        ModMetadataParser.logWarningMessages(id, warnings)

        // Set default values if necessary
        if (links == null) {
            links = ContactInformation.EMPTY
        }

        return V0ModMetadata(
            id,
            version,
            dependencies,
            mixins,
            environment,
            initializer,
            initializers,
            name,
            description,
            authors,
            contributors,
            links,
            license
        )
    }
}*/