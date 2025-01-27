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
package net.fabricmc.loader.impl.entrypoint

import com.moltenex.loader.impl.launch.MoltenexLauncherBase
import net.fabricmc.loader.api.EntrypointException
import net.fabricmc.loader.api.LanguageAdapter
import net.fabricmc.loader.api.LanguageAdapterException
import net.fabricmc.loader.api.entrypoint.EntrypointContainer
import net.fabricmc.loader.impl.ModContainerImpl
import net.fabricmc.loader.impl.metadata.EntrypointMetadata
import net.fabricmc.loader.impl.util.log.Log.debug
import net.fabricmc.loader.impl.util.log.LogCategory
import java.util.*

class EntrypointStorage {
    interface Entry {
        @Throws(Exception::class)
        fun <T> getOrCreate(type: Class<T>): T?
        val isOptional: Boolean
        val modContainer: ModContainerImpl
        val definition: String?
    }

    @Suppress("deprecation")
    private class OldEntry(
        override val modContainer: ModContainerImpl,
        private val languageAdapter: String,
        private val value: String
    ) : Entry {
        private var `object`: Any? = null

        override fun toString(): String {
            return modContainer.getInfo().id + "->" + value
        }

        @Synchronized
        @Throws(Exception::class)
        override fun <T> getOrCreate(type: Class<T>): T? {
            if (`object` == null) {
                val adapter = Class.forName(languageAdapter, true, MoltenexLauncherBase.launcher!!.targetClassLoader)
                    .getConstructor().newInstance() as LanguageAdapter
                `object` = adapter.createInstance(value, options)
            }

            return if (`object` == null || !type.isAssignableFrom(`object`!!.javaClass)) {
                null
            } else {
                `object` as T
            }
        }

        override val isOptional: Boolean
            get() = true

        override val definition: String?
            get() = value

        companion object {
            private val options = LanguageAdapter.Options.Builder.create()
                .missingSuperclassBehaviour(LanguageAdapter.MissingSuperclassBehavior.RETURN_NULL)
                .build()
        }
    }

    private class NewEntry(
        override val modContainer: ModContainerImpl,
        private val adapter: LanguageAdapter?,
        override val definition: String?
    ) : Entry {
        private val instanceMap: MutableMap<Class<*>, Any> =
            IdentityHashMap(1)

        override fun toString(): String {
            return modContainer.metadata.id + "->(0.3.x)" + definition
        }

        @Synchronized
        @Throws(Exception::class)
        override fun <T> getOrCreate(type: Class<T>): T? {
            // this impl allows reentrancy (unlike computeIfAbsent)
            var ret = instanceMap[type] as T?

            if (ret == null) {
                ret = adapter!!.create(modContainer, definition, type)
                checkNotNull(ret)
                val prev = instanceMap.putIfAbsent(type, ret) as T?
                if (prev != null) ret = prev
            }

            return ret
        }

        override val isOptional: Boolean
            get() = false
    }

    private val entryMap: MutableMap<String, MutableList<Entry>> = HashMap()

    private fun getOrCreateEntries(key: String): MutableList<Entry> {
        return entryMap.computeIfAbsent(key) { z: String? -> ArrayList() }
    }

    @Throws(ClassNotFoundException::class, LanguageAdapterException::class)
    fun addDeprecated(modContainer: ModContainerImpl, adapter: String, value: String) {
        debug(
            LogCategory.ENTRYPOINT,
            "Registering 0.3.x old-style initializer %s for mod %s",
            value,
            modContainer.metadata.id
        )
        val oe = OldEntry(modContainer, adapter, value)
        getOrCreateEntries("main").add(oe)
        getOrCreateEntries("client").add(oe)
        getOrCreateEntries("server").add(oe)
    }

    @Throws(Exception::class)
    fun add(
        modContainer: ModContainerImpl,
        key: String,
        metadata: EntrypointMetadata,
        adapterMap: Map<String?, LanguageAdapter?>
    ) {
        if (!adapterMap.containsKey(metadata.adapter)) {
            throw Exception("Could not find adapter '" + metadata.adapter + "' (mod " + modContainer.metadata.id + "!)")
        }

        debug(
            LogCategory.ENTRYPOINT,
            "Registering new-style initializer %s for mod %s (key %s)",
            metadata.value,
            modContainer.metadata.id,
            key
        )
        getOrCreateEntries(key).add(
            NewEntry(
                modContainer, adapterMap[metadata.adapter], metadata.value
            )
        )
    }

    fun hasEntrypoints(key: String): Boolean {
        return entryMap.containsKey(key)
    }

    @Suppress("deprecation")
    fun <T> getEntrypoints(key: String, type: Class<T>): List<T> {
        val entries = entryMap[key]
            ?: return emptyList()

        var exception: EntrypointException? = null
        val results: MutableList<T> = ArrayList(entries.size)

        for (entry in entries) {
            try {
                val result = entry.getOrCreate(type)

                if (result != null) {
                    results.add(result)
                }
            } catch (t: Throwable) {
                if (exception == null) {
                    exception = EntrypointException(key, entry.modContainer.metadata.id!!, t)
                } else {
                    exception.addSuppressed(t)
                }
            }
        }

        if (exception != null) {
            throw exception
        }

        return results
    }

    @Suppress("deprecation")
    fun <T> getEntrypointContainers(key: String, type: Class<T>): List<EntrypointContainer<T>> {
        val entries = entryMap[key]
            ?: return emptyList()

        val results: MutableList<EntrypointContainer<T>> = ArrayList(entries.size)
        var exc: EntrypointException? = null

        for (entry in entries) {
            val container: EntrypointContainerImpl<T>

            if (entry.isOptional) {
                try {
                    val instance = entry.getOrCreate(type) ?: continue

                    container = EntrypointContainerImpl(entry, instance)
                } catch (t: Throwable) {
                    if (exc == null) {
                        exc = EntrypointException(key, entry.modContainer.metadata.id!!, t)
                    } else {
                        exc.addSuppressed(t)
                    }

                    continue
                }
            } else {
                container = EntrypointContainerImpl(key, type, entry)
            }

            results.add(container)
        }

        if (exc != null) throw exc

        return results
    }

    companion object {
        fun <E : Throwable?> sneakyThrows(ex: Throwable): RuntimeException {
            throw ex
        }
    }
}
