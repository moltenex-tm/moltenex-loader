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
package com.moltenex.loader.impl.metadata.fabric.v1


import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement
import net.fabricmc.loader.impl.util.version.VersionParser

internal object ModMetadataParser {
    // Parsing a JsonElement directly into ModMetadata
    private fun parseModMetadata(jsonElement: JsonElement): ModMetadata {
        val jsonFormat = Json { ignoreUnknownKeys = true }
        return jsonFormat.decodeFromJsonElement(jsonElement)
    }

    fun parse(jsonElement: JsonElement): net.fabricmc.loader.impl.metadata.V1ModMetadata {
        val metadata = parseModMetadata(jsonElement)
        return net.fabricmc.loader.impl.metadata.V1ModMetadata(
            metadata.id,
            VersionParser.parse(metadata.version, false),
            metadata.provides,
            metadata.environment,
            metadata.entrypoints,
            metadata.jars,
            metadata.mixins,
            metadata.accessWidener,
            metadata.dependencies,
            metadata.hasRequires,
            metadata.name,
            metadata.description,
            metadata.authors,
            metadata.contributors,
            metadata.contact,
            metadata.license,
            metadata.icon,
            metadata.languageAdapters,
            metadata.customValues
        )
    }
}