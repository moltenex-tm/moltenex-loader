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

import com.moltenex.loader.api.util.version.Version
import com.moltenex.loader.api.util.version.Version.Companion.parse
import com.moltenex.loader.impl.metadata.fabric.common.ContactInformationImpl
import net.fabricmc.loader.api.VersionParsingException
import net.fabricmc.loader.api.metadata.*
import java.util.*

class BuiltinModMetadata private constructor(
    override val id: String, override var version: Version?,
    override val environment: ModEnvironment,
    override val name: String, override val description: String,
    authors: Collection<Person>, contributors: Collection<Person>,
    override val contact: ContactInformation,
    license: Collection<String>,
    private val icons: NavigableMap<Int, String>,
    dependencies: Collection<ModDependency>
) :
    AbstractModMetadata() {
    override val authors: Collection<Person> = Collections.unmodifiableCollection(authors)
    override val contributors: Collection<Person> =
        Collections.unmodifiableCollection(contributors)
    override val license: Collection<String> =
        Collections.unmodifiableCollection(license)
    override var dependencies: Collection<ModDependency?> =
        Collections.unmodifiableCollection(dependencies)

    override val type: String
        get() = TYPE_BUILTIN

    override val provides: Collection<String>
        get() = emptyList()

    override fun getIconPath(size: Int): Optional<String> {
        if (icons.isEmpty()) return Optional.empty()

        val key = size
        var ret = icons.ceilingEntry(key)
        if (ret == null) ret = icons.lastEntry()

        return Optional.of(ret!!.value)
    }

    override fun containsCustomValue(key: String?): Boolean {
        return false
    }

    override fun getCustomValue(key: String?): CustomValue? {
        return null
    }

    override val customValues: Map<String?, CustomValue?>
        get() = emptyMap()

    class Builder(private val id: String, version: String?) {
        private var version: Version? = null
        private var environment = ModEnvironment.UNIVERSAL
        private var name: String
        private var description = ""
        private val authors: MutableCollection<Person> = ArrayList()
        private val contributors: MutableCollection<Person> = ArrayList()
        private var contact = ContactInformation.EMPTY
        private val license: MutableCollection<String> = ArrayList()
        private val icons: NavigableMap<Int, String> = TreeMap()
        private val dependencies: MutableCollection<ModDependency> = ArrayList()

        init {
            this.name = this.id

            try {
                this.version = parse(version)
            } catch (e: VersionParsingException) {
                throw RuntimeException(e)
            }
        }

        fun setEnvironment(environment: ModEnvironment): Builder {
            this.environment = environment
            return this
        }

        fun setName(name: String): Builder {
            this.name = name
            return this
        }

        fun setDescription(description: String): Builder {
            this.description = description
            return this
        }

        fun addAuthor(name: String, contactMap: Map<String, String>): Builder {
            authors.add(createPerson(name, contactMap))
            return this
        }

        fun addContributor(name: String, contactMap: Map<String, String>): Builder {
            contributors.add(createPerson(name, contactMap))
            return this
        }

        fun setContact(contact: ContactInformation): Builder {
            this.contact = contact
            return this
        }

        fun addLicense(license: String): Builder {
            this.license.add(license)
            return this
        }

        fun addIcon(size: Int, path: String): Builder {
            icons[size] = path
            return this
        }

        fun addDependency(dependency: ModDependency): Builder {
            dependencies.add(dependency)
            return this
        }

        fun build(): ModMetadata {
            return BuiltinModMetadata(
                id,
                version,
                environment,
                name,
                description,
                authors,
                contributors,
                contact,
                license,
                icons,
                dependencies
            )
        }

        companion object {
            private fun createPerson(name: String, contactMap: Map<String, String>): Person {
                return object : Person {
                    override val name: String
                        get() = name

                    override val contact: ContactInformation =
                        if (contactMap.isEmpty()) ContactInformation.EMPTY else ContactInformationImpl(contactMap)
                }
            }
        }
    }
}
