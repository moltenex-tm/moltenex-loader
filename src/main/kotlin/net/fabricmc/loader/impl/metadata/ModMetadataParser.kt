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

import kotlinx.serialization.*
import kotlinx.serialization.json.*
import net.fabricmc.loader.impl.util.log.Log
import net.fabricmc.loader.impl.util.log.LogCategory
import java.io.IOException
import java.io.InputStream
import java.nio.charset.StandardCharsets

object ModMetadataParser {
    const val LATEST_VERSION: Int = 1

    /**
     * Keys that will be ignored by any mod metadata parser.
     */
    val IGNORED_KEYS: Set<String> = setOf("\$schema")

    @Throws(ParseMetadataException::class)
    fun parseMetadata(
        `is`: InputStream, modPath: String, modParentPaths: List<String?>,
        versionOverrides: VersionOverrides, depOverrides: DependencyOverrides, isDevelopment: Boolean
    ): LoaderModMetadata {
        try {
            val ret = readModMetadata(`is`)

            versionOverrides.apply(ret)
            depOverrides.apply(ret)

            MetadataVerifier.verify(ret, isDevelopment)

            return ret
        } catch (e: ParseMetadataException) {
            e.setModPaths(modPath, modParentPaths)
            throw e
        } catch (t: Throwable) {
            val e = ParseMetadataException(t)
            e.setModPaths(modPath, modParentPaths)
            throw e
        }
    }

    @Throws(IOException::class, ParseMetadataException::class)
    private fun readModMetadata(`is`: InputStream): LoaderModMetadata {
        // Read the input stream as a string
        val json = String(`is`.readBytes(), StandardCharsets.UTF_8)

        // Parse the JSON using Kotlin Serialization
        val jsonElement = Json.parseToJsonElement(json)

        // Try to extract schemaVersion from the parsed object
        val schemaVersion = jsonElement.jsonObject["schemaVersion"]?.jsonPrimitive?.int ?: 0

        // Parse the mod metadata using the detected schema version
        return when (schemaVersion) {
            1 -> {
                com.moltenex.loader.impl.metadata.fabric.v1.ModMetadataParser.parse(jsonElement)
            }
            0 -> {
                com.moltenex.loader.impl.metadata.fabric.v0.ModMetadataParser.parse(jsonElement)
            }
            else -> {
                if (schemaVersion > 0) {
                    throw ParseMetadataException(
                        "This version of fabric-loader doesn't support the newer schema version of \"$schemaVersion\". Please update fabric-loader to be able to read this."
                    )
                }

                throw ParseMetadataException("Invalid/Unsupported schema version \"$schemaVersion\" was found")
            }
        }
    }

    fun logWarningMessages(id: String?, warnings: List<ParseWarning>) {
        if (warnings.isEmpty()) return

        val message = StringBuilder()

        message.append(String.format("The mod \"%s\" contains invalid entries in its mod json:", id))

        for (warning in warnings) {
            message.append(
                String.format(
                    "\n- %s \"%s\" at line %d column %d",
                    warning.reason, warning.key, warning.line, warning.column
                )
            )
        }

        Log.warn(LogCategory.METADATA, message.toString())
    }
}
