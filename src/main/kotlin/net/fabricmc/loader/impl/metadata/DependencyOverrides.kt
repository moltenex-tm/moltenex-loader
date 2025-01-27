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
import net.fabricmc.loader.api.metadata.ModDependency
import net.fabricmc.loader.impl.metadata.ParseMetadataException
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.*

class DependencyOverrides(configDir: Path) {
    private var dependencyOverrides: Map<String?, List<Entry>>? = null

    init {
        val path = configDir.resolve("fabric_loader_dependencies.json")

        if (!Files.exists(path)) {
            dependencyOverrides = emptyMap()
            throw IOException("Configuration file not found")
        }

        try {
            val jsonContent = String(Files.readAllBytes(path))
            dependencyOverrides = (Klaxon().parse<Map<String, List<Entry>>>(jsonContent)
                ?: throw ParseMetadataException("Failed to parse JSON file", null)) as Map<String?, List<Entry>>?
        } catch (e: IOException) {
            throw IOException("Failed to read the configuration file", e)
        } catch (e: ParseMetadataException) {
            throw ParseMetadataException("Failed to parse the configuration file", e)
        }
    }

    fun apply(metadata: LoaderModMetadata) {
        if (dependencyOverrides!!.isEmpty()) return

        val modOverrides = dependencyOverrides!![metadata.id] ?: return

        val deps: MutableList<ModDependency?> = ArrayList(metadata.dependencies)

        for (entry in modOverrides) {
            when (entry.operation) {
                Operation.REPLACE -> {
                    val it = deps.iterator()
                    while (it.hasNext()) {
                        val dep = it.next()

                        if (dep!!.kind == entry.kind) {
                            it.remove()
                        }
                    }

                    deps.addAll(entry.values)
                }

                Operation.REMOVE -> {
                    val it = deps.iterator()
                    while (it.hasNext()) {
                        val dep = it.next()

                        if (dep!!.kind == entry.kind) {
                            for (value in entry.values) {
                                if (value.modId == dep.modId) {
                                    it.remove()
                                    break
                                }
                            }
                        }
                    }
                }

                Operation.ADD -> deps.addAll(entry.values)
            }
        }

        metadata.dependencies = deps
    }

    val affectedModIds: Collection<String?>
        get() = dependencyOverrides!!.keys

    private data class Entry(val operation: Operation, val kind: ModDependency.Kind, val values: List<ModDependency>)

    private enum class Operation(val operator: String) {
        ADD("+"),
        REMOVE("-"),
        REPLACE(""); // needs to be last to properly match the operator (empty string would match everything)

        companion object {
            val VALUES: Array<Operation> = entries.toTypedArray()
        }
    }

    companion object {
        @Throws(ParseMetadataException::class, IOException::class)
        private fun parse(reader: String): Map<String?, List<Entry>> {
            // Use Klaxon for JSON parsing directly
            val parsedData = Klaxon().parse<Map<String, Any>>(reader)
                ?: throw ParseMetadataException("Invalid JSON format", null)

            val ret: MutableMap<String?, List<Entry>> = mutableMapOf()

            val version = parsedData["version"] as? Int
            if (version != 1) {
                throw ParseMetadataException("Unsupported version, must be 1", null)
            }

            val overrides = parsedData["overrides"] as? Map<String, Any>
            overrides?.forEach { (modId, overridesData) ->
                val overridesList = readKeys(overridesData)
                ret[modId] = overridesList
            }

            return ret
        }

        @Throws(IOException::class, ParseMetadataException::class)
        private fun readKeys(overridesData: Any): List<Entry> {
            val modOverrides = mutableMapOf<ModDependency.Kind, MutableMap<Operation, List<ModDependency>>>()

            if (overridesData is Map<*, *>) {
                for ((key, value) in overridesData) {
                    var op: Operation? = null
                    var kind: ModDependency.Kind? = null

                    if (key is String) {
                        for (operation in Operation.VALUES) {
                            if (key.startsWith(operation.operator)) {
                                op = operation
                                val kindKey = key.substring(operation.operator.length)
                                kind = ModDependency.Kind.entries.find { it.key == kindKey }
                                break
                            }
                        }
                    }

                    if (op == null || kind == null) {
                        throw ParseMetadataException("Invalid dependency key: $key", null)
                    }

                    if (value is List<*>) {
                        val deps = value.map { it as Map<*, *> }
                            .map { modDep ->
                                ModDependencyImpl(kind, modDep["modId"] as String, modDep["version"] as List<String>)
                            }
                        modOverrides.computeIfAbsent(kind) { EnumMap(Operation::class.java) }[op] = deps
                    }
                }
            }

            return modOverrides.flatMap { (kind, operations) ->
                operations.flatMap { (operation, dependencies) ->
                    listOf(Entry(operation, kind, dependencies))
                }
            }
        }
    }
}
