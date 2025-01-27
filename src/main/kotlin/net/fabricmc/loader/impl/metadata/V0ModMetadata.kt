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
import java.util.*

internal class V0ModMetadata(// Required
    override val id: String,
    override var version: Version?,
    dependencies: Collection<ModDependency?>,
    mixins: Mixins?,
    environment: ModEnvironment,
    initializer: String?,
    initializers: Collection<String?>,
    name: String?,
    description: String?,
    authors: Collection<Person>,
    contributors: Collection<Person>,
    links: ContactInformation,
    license: String?
) : AbstractModMetadata(), LoaderModMetadata {
    // Optional (Environment)
    override var dependencies: Collection<ModDependency?>
    private var mixins: Mixins? = null
    override val environment: ModEnvironment // REMOVEME: Replacing Side in old metadata with this
    private val initializer: String?
    private val initializers: Collection<String?>

    // Optional (metadata)
    override val name: String?
    get(){
        if (field != null && field.isEmpty()) {
            return this.id
        }

        return field
    }
    override var description: String? = null
    override val authors: Collection<Person>
    override val contributors: Collection<Person>
    override val contact: ContactInformation
    override val license: Collection<String?>?

    init {
        this.dependencies = Collections.unmodifiableCollection(dependencies)

        if (mixins == null) {
            this.mixins = EMPTY_MIXINS
        } else {
            this.mixins = mixins
        }

        this.environment = environment
        this.initializer = initializer
        this.initializers = Collections.unmodifiableCollection(initializers)
        this.name = name

        if (description == null) {
            this.description = ""
        } else {
            this.description = description
        }

        this.authors = Collections.unmodifiableCollection(authors)
        this.contributors = Collections.unmodifiableCollection(contributors)
        this.contact = links
        this.license = listOf(license)
    }

    override val schemaVersion: Int
        get() = 0

    override val type: String
        get() = TYPE_FABRIC_MOD

    override val provides: Collection<String>
        get() = emptyList()

    override fun loadsInEnvironment(type: EnvType?): Boolean {
        return environment.matches(type!!)
    }

    fun getLicense(): Set<Collection<String?>?> {
        return setOf(this.license)
    }

    override fun getIconPath(size: Int): Optional<String> {
        // honor Mod Menu's de-facto standard
        return Optional.of("assets/$id/icon.png")
    }

    override val customValues: Map<String?, CustomValue?>
        get() = emptyMap()

    override fun containsCustomValue(key: String?): Boolean {
        return false
    }

    override fun getCustomValue(key: String?): CustomValue? {
        return null
    }

    override val languageAdapterDefinitions: Map<String?, String?>
        // Internals
        get() = emptyMap()

    override val jars: Collection<NestedJarEntry>
        get() = emptyList()

    override val oldInitializers: Collection<String?>
        get() {
            return if (this.initializer != null) {
                listOf<String?>(this.initializer)
            } else if (!initializers.isEmpty()) {
                initializers
            } else {
                emptyList<String>()
            }
        }

    override fun getEntrypoints(type: String?): List<EntrypointMetadata?> {
        return emptyList<EntrypointMetadata>()
    }

    override val entrypointKeys: Collection<String>
        get() = emptyList()

    override fun emitFormatWarnings() {}

    override fun getMixinConfigs(type: EnvType?): Collection<String?> {
        val mixinConfigs: MutableList<String?> = ArrayList(
            mixins!!.common
        )

        when (type) {
            EnvType.CLIENT -> mixinConfigs.addAll(mixins!!.client)
            EnvType.SERVER -> mixinConfigs.addAll(mixins!!.server)
            null -> throw IllegalArgumentException("Unsupported type $type")
        }

        return mixinConfigs
    }

    override val accessWidener: String?
        get() = null // intentional null

    internal class Mixins(client: Collection<String?>, common: Collection<String?>, server: Collection<String?>) {
        val client: Collection<String?> =
            Collections.unmodifiableCollection(client)
        val common: Collection<String?> =
            Collections.unmodifiableCollection(common)
        val server: Collection<String?> =
            Collections.unmodifiableCollection(server)
    }

    companion object {
        private val EMPTY_MIXINS = Mixins(emptyList<String>(), emptyList<String>(), emptyList<String>())
    }
}
