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

import net.fabricmc.loader.api.EntrypointException
import net.fabricmc.loader.api.ModContainer
import net.fabricmc.loader.api.entrypoint.EntrypointContainer
import java.util.*

class EntrypointContainerImpl<T> : EntrypointContainer<T> {
    private val key: String?
    private val type: Class<T>?
    private val entry: EntrypointStorage.Entry
    private var instance: T? = null

    /**
     * Create EntrypointContainer with lazy init.
     */
    constructor(key: String?, type: Class<T>?, entry: EntrypointStorage.Entry) {
        this.key = key
        this.type = type
        this.entry = entry
    }

    /**
     * Create EntrypointContainer without lazy init.
     */
    constructor(entry: EntrypointStorage.Entry, instance: T) {
        this.key = null
        this.type = null
        this.entry = entry
        this.instance = instance
    }

    @get:Synchronized
    @get:Suppress("deprecation")
    override val entrypoint: T
        get() {
            if (instance == null) {
                try {
                    instance = entry.getOrCreate(type!!)
                    checkNotNull(instance)
                } catch (ex: Exception) {
                    throw EntrypointException(
                        key!!,
                        Objects.requireNonNull(provider).metadata!!.id!!,
                        ex
                    )
                }
            }

            return instance!!
        }

    override val provider: ModContainer
        get() = entry.modContainer

    override val definition: String
        get() = entry.definition!!
}
