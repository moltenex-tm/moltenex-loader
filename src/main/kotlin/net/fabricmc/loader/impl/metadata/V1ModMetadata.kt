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

import net.fabricmc.api.EnvType
import com.moltenex.loader.api.util.version.Version
import net.fabricmc.loader.api.metadata.*
import net.fabricmc.loader.impl.util.log.Log
import net.fabricmc.loader.impl.util.log.LogCategory
import java.util.*

internal class V1ModMetadata(// Required values
    override val id: String,
    override var version: Version?,
    provides: Collection<String>,
    override val environment: ModEnvironment,
    entrypoints: Map<String, List<EntrypointMetadata?>>,
    jars: Collection<NestedJarEntry>,
    mixins: Collection<MixinEntry>,
    override val accessWidener: String,
    dependencies: Collection<ModDependency?>, // Happy little accidents
    private val hasRequires: Boolean,
    name: String?,
    description: String?,
    authors: Collection<Person>,
    contributors: Collection<Person>,
    contact: ContactInformation?,
    license: Collection<String>,
    icon: IconEntry?,
    languageAdapters: Map<String, String>,
    customValues: Map<String, CustomValue>
) : AbstractModMetadata(),
    LoaderModMetadata {
    override val provides: Collection<String> =
        Collections.unmodifiableCollection(provides)

    override val name: String = name.toString()
        get() = field.takeIf { it.isNotEmpty() } ?: id

    private val entrypoints: Map<String, List<EntrypointMetadata?>> =
        Collections.unmodifiableMap(entrypoints)
    override val jars: Collection<NestedJarEntry> =
        Collections.unmodifiableCollection(jars)
    private val mixins: Collection<MixinEntry> = Collections.unmodifiableCollection(mixins)

    override var dependencies: Collection<ModDependency?>

    override var description: String? = null
    override val authors: Collection<Person>
    override val contributors: Collection<Person>
    override var contact: ContactInformation? = null
    override val license: Collection<String>
    private var icon: IconEntry? = null

    // Internal stuff
    // Optional (language adapter providers)
    override val languageAdapterDefinitions: Map<String?, String?>?

    // Optional (custom values)
    override val customValues: Map<String?, CustomValue?>?

    init {
        this.dependencies = Collections.unmodifiableCollection(dependencies)

        // Empty description if not specified
        if (description != null) {
            this.description = description
        } else {
            this.description = ""
        }

        this.authors = Collections.unmodifiableCollection(authors)
        this.contributors = Collections.unmodifiableCollection(contributors)

        if (contact != null) {
            this.contact = contact
        } else {
            this.contact = ContactInformation.EMPTY
        }

        this.license = Collections.unmodifiableCollection(license)

        if (icon != null) {
            this.icon = icon
        } else {
            this.icon = NO_ICON
        }

        this.languageAdapterDefinitions = Collections.unmodifiableMap(languageAdapters)
        this.customValues = Collections.unmodifiableMap(customValues)
    }

    override val schemaVersion: Int
        get() = 1

    override val type: String
        get() = TYPE_FABRIC_MOD // Fabric Mod

    override fun loadsInEnvironment(type: EnvType?): Boolean {
        return environment.matches(type!!)
    }

    override fun getIconPath(size: Int): Optional<String> {
        return icon!!.getIconPath(size)
    }

    override fun getMixinConfigs(type: EnvType?): Collection<String?> {
        val mixinConfigs: MutableList<String?> = ArrayList()

        // This is only ever called once, so no need to store the result of this.
        for (mixin in this.mixins) {
            if (mixin.environment.matches(type!!)) {
                mixinConfigs.add(mixin.config)
            }
        }

        return mixinConfigs
    }

    override val oldInitializers: Collection<String>
        get() = emptyList() // Not applicable in V1

    override fun getEntrypoints(type: String?): List<EntrypointMetadata?> {
        if (type == null) {
            return emptyList<EntrypointMetadata>()
        }

        val entrypoints = entrypoints[type]!!

        return entrypoints
    }

    override val entrypointKeys: Collection<String>
        get() = entrypoints.keys

    override fun emitFormatWarnings() {
        if (hasRequires) {
            Log.warn(
                LogCategory.METADATA,
                "Mod `${this.id}` (${this.version}) uses 'dependencyHandler' key in fabric.mod.json, which is not supported - use 'depends'",
                this.id,
                this.version
            )
        }
    }

    internal class EntrypointMetadataImpl(override val adapter: String, override val value: String) : EntrypointMetadata

    internal class JarEntry(override val file: String) : NestedJarEntry

    internal class MixinEntry(val config: String, val environment: ModEnvironment)

    internal fun interface IconEntry {
        fun getIconPath(size: Int): Optional<String>
    }

    internal class Single(private val icon: String) : IconEntry {
        override fun getIconPath(size: Int): Optional<String> {
            return Optional.of(this.icon)
        }
    }

    internal class MapEntry(private val icons: SortedMap<Int, String>) : IconEntry {
        override fun getIconPath(size: Int): Optional<String> {
            var iconValue = -1

            for (i in icons.keys) {
                iconValue = i

                if (iconValue >= size) {
                    break
                }
            }

            return Optional.of(icons[iconValue]!!)
        }
    }

    companion object {
        val NO_ICON: IconEntry = IconEntry { Optional.empty<String?>() }
    }
}
