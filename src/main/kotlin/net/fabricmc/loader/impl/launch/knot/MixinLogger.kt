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
package net.fabricmc.loader.impl.launch.knot

import net.fabricmc.loader.impl.util.log.Log.log
import net.fabricmc.loader.impl.util.log.Log.shouldLog
import net.fabricmc.loader.impl.util.log.LogCategory
import net.fabricmc.loader.impl.util.log.LogLevel
import org.spongepowered.asm.logging.ILogger
import org.spongepowered.asm.logging.Level
import org.spongepowered.asm.logging.LoggerAdapterAbstract
import java.util.*
import java.util.concurrent.ConcurrentHashMap

internal class MixinLogger(name: String) : LoggerAdapterAbstract(name) {
    private val logCategory =
        LogCategory.create(name.replace("mixin", LogCategory.MIXIN.name).replace(".", LogCategory.SEPARATOR))

    override fun getType(): String {
        return "Fabric Mixin Logger"
    }

    override fun catching(level: Level, t: Throwable) {
        log(level, "Catching $t", t)
    }

    override fun log(level: Level, message: String?, vararg params: Any) {
        var message = message
        val fabricLevel = translateLevel(level)
        if (!shouldLog(fabricLevel, logCategory)) return

        var exc: Throwable? = null

        if (params.isNotEmpty()) {
            if (message == null) {
                if (params[0] is Throwable) exc = params[0] as Throwable
            } else {
                // emulate Log4J's {} tokens and \ escapes
                val sb = StringBuilder(message.length + 20)
                var paramIdx = 0
                var escaped = false

                var i = 0
                val max = message.length
                while (i < max) {
                    val c = message[i]

                    if (escaped) {
                        sb.append(c)
                        escaped = false
                    } else if (c == '\\' && i + 1 < max) {
                        escaped = true
                    } else if (c == '{' && i + 1 < max && message[i + 1] == '}' && paramIdx < params.size) { // unescaped {} with matching param idx
                        val param = params[paramIdx++]

                        if (param.javaClass.isArray) {
                            val `val` = arrayOf(param).contentDeepToString()
                            sb.append(`val`, 1, `val`.length - 1)
                        } else {
                            sb.append(param)
                        }

                        i++ // skip over }
                    } else {
                        sb.append(c)
                    }
                    i++
                }

                message = sb.toString()

                if (paramIdx < params.size && params[params.size - 1] is Throwable) {
                    exc = params[params.size - 1] as Throwable
                }
            }
        }

        log(fabricLevel, logCategory, message!!, exc)
    }

    override fun log(level: Level, message: String, t: Throwable) {
        log(translateLevel(level), logCategory, message, t)
    }

    override fun <T : Throwable> throwing(t: T): T {
        log(Level.ERROR, "Throwing $t", t)

        return t
    }

    companion object {
        private val LOGGER_MAP: MutableMap<String, ILogger> = ConcurrentHashMap()
        private val LEVEL_MAP = createLevelMap()

        fun get(name: String): ILogger {
            return LOGGER_MAP.computeIfAbsent(name) { name: String -> MixinLogger(name) }
        }

        private fun translateLevel(level: Level): LogLevel {
            return LEVEL_MAP.getOrDefault(level, LogLevel.INFO)
        }

        private fun createLevelMap(): Map<Level, LogLevel> {
            val ret: MutableMap<Level, LogLevel> = EnumMap(
                Level::class.java
            )

            ret[Level.FATAL] = LogLevel.ERROR
            ret[Level.ERROR] = LogLevel.ERROR
            ret[Level.WARN] = LogLevel.WARN
            ret[Level.INFO] = LogLevel.INFO
            ret[Level.DEBUG] = LogLevel.DEBUG
            ret[Level.TRACE] = LogLevel.TRACE

            return ret
        }
    }
}
