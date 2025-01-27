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
package net.fabricmc.loader.impl

import net.fabricmc.loader.api.ObjectShare
import java.util.*
import java.util.function.BiConsumer

internal class ObjectShareImpl : ObjectShare {
    private val values: MutableMap<String?, Any?> = HashMap()
    private val pendingMap: MutableMap<String?, MutableList<BiConsumer<String?, Any?>>> = HashMap()

    @Synchronized
    override fun get(key: String?): Any? {
        validateKey(key)

        return values[key]
    }

    override fun put(key: String?, value: Any?): Any? {
        validateKey(key)
        Objects.requireNonNull(value, "null value")

        val pending: List<BiConsumer<String?, Any?>>?

        synchronized(this) {
            val prev = values.put(key, value)
            if (prev != null) return prev // no new entry -> can't have pending entries for it
            pending = pendingMap.remove(key)
        }

        if (pending != null) invokePending(key, value, pending)

        return null
    }

    override fun putIfAbsent(key: String?, value: Any?): Any? {
        validateKey(key)
        Objects.requireNonNull(value, "null value")

        val pending: List<BiConsumer<String?, Any?>>?

        synchronized(this) {
            val prev = values.putIfAbsent(key, value)
            if (prev != null) return prev // no new entry -> can't have pending entries for it
            pending = pendingMap.remove(key)
        }

        if (pending != null) invokePending(key, value, pending)

        return null
    }

    @Synchronized
    override fun remove(key: String?): Any? {
        validateKey(key)

        return values.remove(key)
    }

    override fun whenAvailable(key: String?, consumer: BiConsumer<String?, Any?>?) {
        validateKey(key)

        val value: Any?

        synchronized(this) {
            value = values[key]
            if (value == null) { // value doesn't exist yet, queue invocation for when it gets added
                if (consumer != null) {
                    pendingMap.computeIfAbsent(key) { ignore: String? -> ArrayList() }.add(consumer)
                }
                return
            }
        }

        // value exists already, invoke directly
        consumer?.accept(key, value)
    }

    companion object {
        private fun validateKey(key: String?) {
            Objects.requireNonNull(key, "null key")

            val pos = key!!.indexOf(':')
            require(!(pos <= 0 || pos >= key.length - 1)) { "invalid key, must be modid:subkey" }
        }

        private fun invokePending(key: String?, value: Any?, pending: List<BiConsumer<String?, Any?>>) {
            for (consumer in pending) {
                consumer.accept(key, value)
            }
        }
    }
}
