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
package com.moltenex.loader.impl.launch

import com.moltenex.loader.impl.launch.MoltenexLauncherBase.Companion.launcher
import net.fabricmc.loader.impl.util.ManifestUtil.getManifestValue
import net.fabricmc.loader.impl.util.SystemProperties
import net.fabricmc.loader.impl.util.log.Log
import net.fabricmc.loader.impl.util.log.LogCategory
import net.fabricmc.loader.impl.util.mappings.FilteringMappingVisitor
import net.fabricmc.mappingio.MappingReader
import net.fabricmc.mappingio.format.MappingFormat
import net.fabricmc.mappingio.format.tiny.Tiny1FileReader
import net.fabricmc.mappingio.format.tiny.Tiny2FileReader
import net.fabricmc.mappingio.tree.MappingTree
import net.fabricmc.mappingio.tree.MemoryMappingTree
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.net.JarURLConnection
import java.net.URLConnection
import java.util.jar.Attributes
import java.util.zip.ZipError

class MappingConfiguration {
    private var initializedMetadata = false
    private var initializedMappings = false

    private var gameId: String? = null
    private var gameVersion: String? = null
    private var namespaces: List<String>? = null
    var mappings: MemoryMappingTree? = null

    fun getGameId(): String? {
        initializeMetadata()

        return gameId
    }

    fun getGameVersion(): String? {
        initializeMetadata()

        return gameVersion
    }

    fun getNamespaces(): List<String>? {
        initializeMetadata()

        return namespaces
    }

    fun matches(gameId: String?, gameVersion: String?): Boolean {
        initializeMetadata()

        return (this.gameId == null || gameId == null || gameId == this.gameId)
                && (this.gameVersion == null || gameVersion == null || gameVersion == this.gameVersion)
    }

    fun getMappings(): MappingTree? {
        initializeMappings()

        return mappings
    }

    val targetNamespace: String
        get() = if (launcher!!.isDevelopment) "named" else "intermediary"

    fun requiresPackageAccessHack(): Boolean {
        // TODO
        return FIX_PACKAGE_ACCESS || targetNamespace == "named"
    }

    private fun initializeMetadata() {
        if (initializedMetadata) return

        val connection = openMappings()

        try {
            if (connection != null) {
                if (connection is JarURLConnection) {
                    val manifest = connection.manifest

                    if (manifest != null) {
                        gameId = getManifestValue(manifest, Attributes.Name("Game-Id"))
                        gameVersion = getManifestValue(manifest, Attributes.Name("Game-Version"))
                    }
                }

                BufferedReader(InputStreamReader(connection.getInputStream())).use { reader ->
                    val format = readMappingFormat(reader)
                    namespaces = when (format) {
                        MappingFormat.TINY_FILE -> Tiny1FileReader.getNamespaces(reader)
                        MappingFormat.TINY_2_FILE -> Tiny2FileReader.getNamespaces(reader)
                        else -> throw UnsupportedOperationException("Unsupported mapping format: $format")
                    }
                }
            }
        } catch (e: IOException) {
            throw RuntimeException("Error reading mapping metadata", e)
        }

        initializedMetadata = true
    }

    private fun initializeMappings() {
        if (initializedMappings) return

        initializeMetadata()
        val connection = openMappings()

        if (connection != null) {
            try {
                BufferedReader(InputStreamReader(connection.getInputStream())).use { reader ->
                    val time = System.currentTimeMillis()
                    mappings = MemoryMappingTree()
                    val mappingFilter = FilteringMappingVisitor(mappings!!)

                    val format = readMappingFormat(reader)

                    when (format) {
                        MappingFormat.TINY_FILE -> Tiny1FileReader.read(reader, mappingFilter)
                        MappingFormat.TINY_2_FILE -> Tiny2FileReader.read(reader, mappingFilter)
                        else -> throw UnsupportedOperationException("Unsupported mapping format: $format")
                    }
                    Log.debug(LogCategory.MAPPINGS, "Loading mappings took %d ms", System.currentTimeMillis() - time)
                }
            } catch (e: IOException) {
                throw RuntimeException("Error reading mappings", e)
            }
        }

        if (mappings == null) {
            Log.info(LogCategory.MAPPINGS, "Mappings not present!")
            mappings = MemoryMappingTree()
        }

        initializedMappings = true
    }

    private fun openMappings(): URLConnection? {
        val url = MappingConfiguration::class.java.classLoader.getResource("mappings/mappings.tiny")

        if (url != null) {
            try {
                return url.openConnection()
            } catch (e: IOException) {
                throw RuntimeException("Error reading $url", e)
            } catch (e: ZipError) {
                throw RuntimeException("Error reading $url", e)
            }
        }

        return null
    }

    @Throws(IOException::class)
    private fun readMappingFormat(reader: BufferedReader): MappingFormat? {
        // We will only ever need to read tiny here
        // so to strip the other formats from the included copy of mapping IO, don't use MappingReader.read()
        reader.mark(4096)
        val format = MappingReader.detectFormat(reader)
        reader.reset()

        return format
    }

    companion object {
        private val FIX_PACKAGE_ACCESS = System.getProperty(SystemProperties.FIX_PACKAGE_ACCESS) != null
    }
}
