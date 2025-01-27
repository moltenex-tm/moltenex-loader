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
package net.fabricmc.loader.impl.discovery

import com.moltenex.loader.api.util.version.Version
import net.fabricmc.api.EnvType
import net.fabricmc.loader.api.metadata.*
import net.fabricmc.loader.impl.metadata.AbstractModMetadata
import net.fabricmc.loader.impl.metadata.EntrypointMetadata
import net.fabricmc.loader.impl.metadata.LoaderModMetadata
import net.fabricmc.loader.impl.metadata.NestedJarEntry
import java.util.*

internal class BuiltinMetadataWrapper(private val parent: ModMetadata) : AbstractModMetadata(), LoaderModMetadata {
    override var version: Version? = parent.version
    override var dependencies: Collection<ModDependency?>

    init {
        dependencies = parent.dependencies
    }

    override val type: String?
        get() = parent.type

    override val id: String?
        get() = parent.id

    override val provides: Collection<String?>?
        get() = parent.provides

    override val environment: ModEnvironment?
        get() = parent.environment

    override val name: String?
        get() = parent.name

    override val description: String?
        get() = parent.description

    override val authors: Collection<Person?>?
        get() = parent.authors

    override val contributors: Collection<Person?>?
        get() = parent.contributors

    override val contact: ContactInformation?
        get() = parent.contact

    override val license: Collection<String?>?
        get() = parent.license

    override fun getIconPath(size: Int): Optional<String> {
        return parent.getIconPath(size)
    }

    override fun containsCustomValue(key: String?): Boolean {
        return parent.containsCustomValue(key)
    }

    override fun getCustomValue(key: String?): CustomValue? {
        return parent.getCustomValue(key)
    }

    override val customValues: Map<String?, CustomValue?>?
        get() = parent.customValues

    override val schemaVersion: Int
        get() = Int.MAX_VALUE

    override val languageAdapterDefinitions: Map<String?, String?>
        get() = emptyMap()

    override val jars: Collection<NestedJarEntry>
        get() = emptyList()

    override fun getMixinConfigs(type: EnvType?): Collection<String?>? {
        return emptyList<String>()
    }

    override val accessWidener: String?
        get() = null

    override fun loadsInEnvironment(type: EnvType?): Boolean {
        return true
    }

    override val oldInitializers: Collection<String>
        get() = emptyList()

    override fun getEntrypoints(type: String?): List<EntrypointMetadata?>? {
        return emptyList<EntrypointMetadata>()
    }

    override val entrypointKeys: Collection<String>
        get() = emptyList()

    override fun emitFormatWarnings() {}
}
