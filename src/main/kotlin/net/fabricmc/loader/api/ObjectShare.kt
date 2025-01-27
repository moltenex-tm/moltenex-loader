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
package net.fabricmc.loader.api

import java.util.function.BiConsumer

/**
 * Object share for inter-mod communication, obtainable through [MoltenexLoader.getObjectShare].
 *
 *
 * The share allows mods to exchange data without directly referencing each other. This makes simple interaction
 * easier by eliminating any compile- or run-time dependencies if the shared value type is independent of the mod
 * (only Java/game/Fabric types like collections, primitives, String, Consumer, Function, ...).
 *
 *
 * Active interaction is possible as well since the shared values can be arbitrary Java objects. For example
 * exposing a `Runnable` or `Function` allows the "API" user to directly invoke some program logic.
 *
 *
 * It is required to prefix the share key with the mod id like `mymod:someProperty`. Mods should not
 * modify entries by other mods. The share is thread safe.
 */
interface ObjectShare {
    /**
     * Get the value for a specific key.
     *
     *
     * Java 16 introduced a convenient syntax for type safe queries that combines null check, type check and cast:
     * <pre>
     * if (MoltenexLoader.getInstance().getObjectShare().get("someMod:someValue") instanceof String value) {
     * // use value here
     * }
    </pre> *
     *
     *
     * A generic type still needs a second unchecked cast due to erasure:
     * <pre>
     * if (MoltenexLoader.getInstance().getObjectShare().get("mymod:fuel") instanceof Consumer{@code} c) {
     * ((Consumer{@code<ItemStack>}) c).accept(someStack);
     * }
    </ItemStack></pre> *
     *
     *
     * Consider using [.whenAvailable] instead if the value may not be available yet. The mod load order is
     * undefined, so entries that are added during the same load phase should be queried in a later phase or be handled
     * through [whenAvailable].
     *
     * @param key key to query, format `modid:subkey`
     * @return value associated with the key or null if none
     */
    fun get(key: String?): Any?

    /**
     * Request being notified when a key/value becomes available.
     *
     *
     * This is primarily intended to resolve load order issues, when there is no good time to call [get].
     *
     *
     * If there is already a value associated with the `key`, the consumer will be invoked directly, otherwise
     * when one of the `put` methods adds a value for the key. The invocation happens on the thread calling
     * [.whenAvailable] or on whichever thread calls `put` with the same `key`.
     *
     *
     * The request will only act once, not if the value changes again.
     *
     *
     * Example use:
     * <pre>
     * MoltenexLoader.getInstance().getObjectShare().whenAvailable("someMod:someValue", (k, v) -> {
     * if (v instanceof String value) {
     * // use value
     * }
     * });
    </pre> *
     *
     * @param key key to react upon, format `modid:subkey`
     * @paran consumer consumer receiving the key/value pair: key first, value second
     */
    fun whenAvailable(key: String?, consumer: BiConsumer<String?, Any?>?)

    /**
     * Set the value for a specific key.
     *
     * @param key key to add a value for, format `modid:subkey`
     * @param value value to add, must not be null
     * @return previous value associated with the key, null if none
     */
    fun put(key: String?, value: Any?): Any?

    /**
     * Set the value for a specific key if there isn't one yet.
     *
     *
     * This is an atomic operation, thus thread safe contrary to using get+put.
     *
     * @param key key to add a value for, format `modid:subkey`
     * @param value value to add, must not be null
     * @return previous value associated with the key, null if none and thus the entry changed
     */
    fun putIfAbsent(key: String?, value: Any?): Any?

    /**
     * Remove the value for a specific key.
     *
     * @param key key to remove the value for, format `modid:subkey`
     * @return previous value associated with the key, null if none
     */
    fun remove(key: String?): Any?
}
