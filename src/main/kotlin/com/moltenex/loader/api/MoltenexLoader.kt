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
package com.moltenex.loader.api

import net.fabricmc.api.EnvType
import net.fabricmc.loader.api.MappingResolver
import net.fabricmc.loader.api.ModContainer
import net.fabricmc.loader.api.ObjectShare
import net.fabricmc.loader.api.entrypoint.EntrypointContainer
import com.moltenex.loader.impl.MoltenexLoaderImpl
import java.io.File
import java.nio.file.Path
import java.util.*
import java.util.function.Consumer

/**
 * The public-facing MoltenexLoader instance.
 *
 *
 * To obtain a working instance, call [.getInstance].
 *
 * @since 0.4.0
 */
interface MoltenexLoader {
    /**
     * Returns all entrypoints declared under a `key`, assuming they are of a specific type.
     *
     * @param key  the key in entrypoint declaration in `fabric.mod.json`
     * @param type the type of entrypoints
     * @param <T>  the type of entrypoints
     * @return the obtained entrypoints
     * @see .getEntrypointContainers
    </T> */
    fun <T> getEntrypoints(key: String?, type: Class<T>?): List<T>?

    /**
     * Returns all entrypoints declared under a `key`, assuming they are of a specific type.
     *
     *
     * The entrypoint is declared in the `fabric.mod.json` as following:
     * <pre><blockquote>
     * "entrypoints": {
     * "&lt;a key&gt;": [
     * &lt;a list of entrypoint declarations&gt;
     * ]
     * }
    </blockquote></pre> *
     * Multiple keys can be present in the `entrypoints` section.
     *
     *
     * An entrypoint declaration indicates that an arbitrary notation is sent
     * to a [LanguageAdapter] to offer an instance of entrypoint. It is
     * either a string, or an object. An object declaration
     * is of this form:<pre><blockquote>
     * {
     * "adapter": "&lt;a custom adatper&gt;"
     * "value": "&lt;an arbitrary notation&gt;"
     * }
    </blockquote></pre> *
     * A string declaration `<an arbitrary notation>` is equivalent to
     * <pre><blockquote>
     * {
     * "adapter": "default"
     * "value": "&lt;an arbitrary notation&gt;"
     * }
    </blockquote></pre> *
     * where the `default` adapter is the [adapter][LanguageAdapter]
     * offered by Fabric Loader.
     *
     * @param key  the key in entrypoint declaration in `fabric.mod.json`
     * @param type the type of entrypoints
     * @param <T>  the type of entrypoints
     * @return the entrypoint containers related to this key
     * @throws EntrypointException if a problem arises during entrypoint creation
     * @see LanguageAdapter
    </T> */
    fun <T> getEntrypointContainers(key: String?, type: Class<T>?): List<EntrypointContainer<T>?>?

    /**
     * Invokes an action on all entrypoints that would be returned by [.getEntrypointContainers] for the given
     * `key` and `type`.
     *
     *
     * The action is invoked by applying the given `invoker` to each entrypoint instance.
     *
     *
     * Exceptions thrown by `invoker` will be collected and thrown after all entrypoints have been invoked.
     *
     * @param key     the key in entrypoint declaration in `fabric.mod.json`
     * @param type    the type of entrypoints
     * @param invoker applied to each entrypoint to invoke the desired action
     * @param <T>     the type of entrypoints
     * @see .getEntrypointContainers
    </T> */
    fun <T> invokeEntrypoints(key: String?, type: Class<T>?, invoker: Consumer<in T>?)

    /**
     * Get the object share for inter-mod communication.
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
     *
     * @return the global object share instance
     */
    val objectShare: ObjectShare?

    /**
     * Get the current mapping resolver.
     *
     *
     * When performing reflection, a mod should always query the mapping resolver for
     * the remapped names of members than relying on other heuristics.
     *
     * @return the current mapping resolver instance
     */
    val mappingResolver: MappingResolver?

    /**
     * Gets the container for a given mod.
     *
     * @param id the ID of the mod
     * @return the mod container, if present
     */
    fun getModContainer(id: String?): Optional<ModContainer>?

    /**
     * Gets all mod containers.
     *
     * @return a collection of all loaded mod containers
     */
    val allMods: Collection<ModContainer?>?

    /**
     * Checks if a mod with a given ID is loaded.
     *
     * @param id the ID of the mod, as defined in `fabric.mod.json`
     * @return whether or not the mod is present in this Fabric Loader instance
     */
    fun isModLoaded(id: String?): Boolean

    /**
     * Checks if Moltenex Loader is currently running in a "development"
     * environment. Can be used for enabling debug mode or additional checks.
     *
     * This should not be used to make assumptions on certain features,
     * such as mappings, but as a toggle for certain functionalities.
     *
     * @return whether Loader is currently in a "development"
     * environment
     */
    fun isDevelopmentEnvironment(): Boolean

    /**
     * Get the current environment type.
     *
     * @return the current environment type
     */
    val environmentType: EnvType?

    @get:Deprecated("This method is experimental and its use is discouraged.")
    val gameInstance: Any?

    /**
     * Get the current game working directory.
     *
     * @return the working directory
     */
    val gameDir: Path?

    @get:Deprecated("")
    val gameDirectory: File?

    /**
     * Get the current directory for game configuration files.
     *
     * @return the configuration directory
     */
    val configDir: Path?

    @get:Deprecated("")
    val configDirectory: File?

    /**
     * Gets the command line arguments used to launch the game.
     *
     *
     * The implementation will try to strip or obscure sensitive data like authentication tokens if `sanitize`
     * is set to true. Callers are highly encouraged to enable sanitization as compromising the information can easily
     * happen with logging, exceptions, serialization or other causes.
     *
     *
     * There is no guarantee that `sanitize` covers everything, so the launch arguments should still not be
     * logged or otherwise exposed routinely even if the parameter is set to `true`. In particular it won't
     * necessarily strip all information that can be used to identify the user.
     *
     * @param sanitize Whether to try to remove or obscure sensitive information
     * @return the launch arguments for the game
     */
    fun getLaunchArguments(sanitize: Boolean): Array<String>?

    companion object {
        fun isDevelopmentEnvironment(): Boolean {
            return isDevelopmentEnvironment()
        }
        @get:JvmStatic
        val instance: MoltenexLoader
            /**
             * Returns the public-facing Fabric Loader instance.
             */
            get() {
                val ret = MoltenexLoaderImpl.INSTANCE
                    ?: throw RuntimeException("Accessed MoltenexLoader too early!")

                return ret
            }
    }
}
