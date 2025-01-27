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
package net.fabricmc.loader.impl.util.log

class LogCategory private constructor(val context: String, names: Array<out String>) {
	val name: String
    var data: Any? = null

    init {
        this.name = java.lang.String.join(SEPARATOR, *names)
    }

    override fun toString(): String {
        return if (name.isEmpty()) context else context + SEPARATOR + name
    }

    companion object {
        val DISCOVERY: LogCategory = create("Discovery")
		val ENTRYPOINT: LogCategory = create("Entrypoint")
		val GAME_PATCH: LogCategory = create("GamePatch")
		val GAME_PROVIDER: LogCategory = create("GameProvider")
		val GAME_REMAP: LogCategory = create("GameRemap")
		val GENERAL: LogCategory = create()
		val KNOT: LogCategory = create("Knot")
		val LIB_CLASSIFICATION: LogCategory = create("LibClassify")
        val LOG: LogCategory = create("Log")
		val MAPPINGS: LogCategory = create("Mappings")
		val METADATA: LogCategory = create("Metadata")
		val MOD_REMAP: LogCategory = create("ModRemap")
		val MIXIN: LogCategory = create("Mixin")
		val RESOLUTION: LogCategory = create("Resolution")
		val TEST: LogCategory = create("Test")

        const val SEPARATOR: String = "/"

		fun create(vararg names: String): LogCategory {
            return LogCategory(Log.NAME, names)
        }

        /**
         * Create a log category for external uses, no API guarantees!
         */
        fun createCustom(context: String, vararg names: String): LogCategory {
            return LogCategory(context, names)
        }
    }
}
