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
package net.fabricmc.loader.impl.util

import java.util.*

class Arguments {
    private val values: MutableMap<String, String> =
        LinkedHashMap()
    private val extraArgs: MutableList<String> = ArrayList()

    fun keys(): Collection<String> {
        return values.keys
    }

    fun getExtraArgs(): List<String> {
        return Collections.unmodifiableList(extraArgs)
    }

    fun containsKey(key: String): Boolean {
        return values.containsKey(key)
    }

    fun get(key: String): String? {
        return values[key]
    }

    fun getOrDefault(key: String, value: String): String {
        return values.getOrDefault(key, value)
    }

    fun put(key: String, value: String) {
        values[key] = value
    }

    fun addExtraArg(value: String) {
        extraArgs.add(value)
    }

    fun parse(args: Array<String>) {
        parse(mutableListOf(*args))
    }

    fun parse(args: MutableList<String>) {
        var i = 0
        while (i < args.size) {
            val arg = args[i]
            if (arg.startsWith("--") && i < args.size - 1) {
                var value = args[i + 1]

                if (value.startsWith("--")) {
                    // Give arguments that have no value an empty string.
                    value = ""
                } else {
                    i += 1
                }
                values[arg.substring(2)] = value.toString()
            } else {
                extraArgs.add(arg)
            }
            i++
        }
    }

    fun toArray(): Array<String> {
        // Create a new array of the correct size
        val newArgs = Array(values.size * 2 + extraArgs.size) { "" }
        var i = 0

        // Populate the array with key-value pairs from `values`
        for (s in values.keys) {
            newArgs[i++] = "--$s"
            newArgs[i++] = values[s]!!
        }

        // Add extra arguments to the array
        for (s in extraArgs) {
            newArgs[i++] = s
        }

        return newArgs
    }


    fun remove(s: String): String? {
        return values.remove(s)
    }

    companion object {
        // set the game version for the builtin game mod/dependencies, bypassing auto-detection
        const val GAME_VERSION: String = SystemProperties.GAME_VERSION

        // additional mods to load (path separator separated paths, @ prefix for meta-file with each line referencing an actual file)
        const val ADD_MODS: String = SystemProperties.ADD_MODS
    }
}
