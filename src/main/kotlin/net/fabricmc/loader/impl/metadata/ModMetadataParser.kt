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

import com.beust.klaxon.Klaxon
import com.beust.klaxon.JsonReader
//import com.moltenex.loader.impl.metadata.fabric.OutdatedFabricV1ModMetadataParser
import com.moltenex.loader.impl.metadata.fabric.FabricV0ModMetadataParser
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
            val ret = readModMetadata(`is`, isDevelopment)

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
    private fun readModMetadata(`is`: InputStream, isDevelopment: Boolean): LoaderModMetadata {
        // Read the input stream as a string for Klaxon parsing
        val json = String(`is`.readBytes(), StandardCharsets.UTF_8)

        // Parse the JSON with Klaxon
        val klaxonJson = Klaxon().parseJsonObject(json.reader())

        // Now we need to convert Klaxon parsed data to a JsonReader
        val stringReader = json.reader()
        val jsonReader = JsonReader(stringReader)

        // Try to extract schemaVersion from the parsed object
        val schemaVersion = klaxonJson["schemaVersion"] as? Int ?: 0

        // Parse the mod metadata using the detected schema version
        return when (schemaVersion) {
            //1 -> OutdatedFabricV1ModMetadataParser.parse(jsonReader)
            0 -> {
                // For version 0, we need to pass the JSON reader to the V0 parser
                FabricV0ModMetadataParser.parse(jsonReader)
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
