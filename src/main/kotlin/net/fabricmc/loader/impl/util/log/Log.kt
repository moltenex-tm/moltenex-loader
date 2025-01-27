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

import java.util.*
import kotlin.math.max

object Log {
    const val NAME: String = "MoltenexLoader"
    private const val CHECK_FOR_BRACKETS = true

    private var handler: LogHandler = BuiltinLogHandler()

    @JvmStatic
	fun init(handler: LogHandler?) {
        if (handler == null) throw NullPointerException("null log handler")

        val oldHandler = Log.handler

        if (oldHandler is BuiltinLogHandler) {
            oldHandler.replay(handler)
        }

        Log.handler = handler
        oldHandler.close()
    }

    /**
     * Configure builtin log handler.
     *
     * @param buffer whether to buffer log messages for later replaying
     * @param output whether to output log messages directly
     */
    fun configureBuiltin(buffer: Boolean, output: Boolean) {
        val handler = handler

        if (handler is BuiltinLogHandler) {
            handler.configure(buffer, output)
        }
    }

    /**
     * Finish configuring builtin log handler, using defaults if unconfigured.
     */
	@JvmStatic
	fun finishBuiltinConfig() {
        val handler = handler

        if (handler is BuiltinLogHandler) {
            handler.finishConfig()
        }
    }

    fun error(category: LogCategory?, format: String, vararg args: Any?) {
        logFormat(LogLevel.ERROR, category, format, *args)
    }

    @JvmStatic
	fun error(category: LogCategory?, msg: String) {
        log(LogLevel.ERROR, category, msg)
    }

    fun error(category: LogCategory?, msg: String, exc: Throwable?) {
        log(LogLevel.ERROR, category, msg, exc)
    }

    @JvmStatic
	fun warn(category: LogCategory?, format: String, vararg args: Any?) {
        logFormat(LogLevel.WARN, category, format, *args)
    }

    @JvmStatic
	fun warn(category: LogCategory?, msg: String) {
        log(LogLevel.WARN, category, msg)
    }

    @JvmStatic
	fun warn(category: LogCategory?, msg: String, exc: Throwable?) {
        log(LogLevel.WARN, category, msg, exc)
    }

    @JvmStatic
	fun info(category: LogCategory?, format: String, vararg args: Any?) {
        logFormat(LogLevel.INFO, category, format, *args)
    }

    @JvmStatic
	fun info(category: LogCategory?, msg: String) {
        log(LogLevel.INFO, category, msg)
    }

    fun info(category: LogCategory?, msg: String, exc: Throwable?) {
        log(LogLevel.INFO, category, msg, exc)
    }

    @JvmStatic
	fun debug(category: LogCategory?, format: String, vararg args: Any?) {
        logFormat(LogLevel.DEBUG, category, format, *args)
    }

    @JvmStatic
	fun debug(category: LogCategory?, msg: String) {
        log(LogLevel.DEBUG, category, msg)
    }

    @JvmStatic
	fun debug(category: LogCategory?, msg: String, exc: Throwable?) {
        log(LogLevel.DEBUG, category, msg, exc)
    }

    fun trace(category: LogCategory?, format: String, vararg args: Any?) {
        logFormat(LogLevel.TRACE, category, format, *args)
    }

    fun trace(category: LogCategory?, msg: String) {
        log(LogLevel.TRACE, category, msg)
    }

    fun trace(category: LogCategory?, msg: String, exc: Throwable?) {
        log(LogLevel.TRACE, category, msg, exc)
    }

    fun log(level: LogLevel?, category: LogCategory?, msg: String) {
        val handler = handler
        if (handler.shouldLog(level, category)) log(handler, level, category, msg, null)
    }

    @JvmStatic
	fun log(level: LogLevel?, category: LogCategory?, msg: String, exc: Throwable?) {
        val handler = handler
        if (handler.shouldLog(level, category)) log(handler, level, category, msg, exc)
    }

    fun logFormat(level: LogLevel?, category: LogCategory?, format: String, vararg args: Any?) {
        val handler = handler
        if (!handler.shouldLog(level, category)) return

        var msg: String
        val exc: Throwable?

        if (args.isEmpty()) {
            //assert getRequiredArgs(format.toString()) == 0;

            msg = format
            exc = null
        } else {
            if (CHECK_FOR_BRACKETS) {
                require(!format.contains("{}")) { "log message containing {}: $format" }
            }

            val lastArg = args[args.size - 1]
            val newArgs: Array<Any?>

            if (lastArg is Throwable && getRequiredArgs(format) < args.size) {
                exc = lastArg
                newArgs = args.dropLast(1).toTypedArray()
            } else {
                exc = null
                newArgs = arrayOf(args)
            }

            assert(getRequiredArgs(format) == newArgs.size)

            try {
                msg = String.format(format, *newArgs)
            } catch (e: IllegalFormatException) {
                msg = "Format error: fmt=[" + format + "] args=" + args.contentToString()
                warn(LogCategory.LOG, "Invalid format string.", e)
            }
        }

        log(handler, level, category, msg, exc)
    }

    private fun getRequiredArgs(format: String): Int {
        var ret = 0
        var minRet = 0
        var wasPct = false

        var i = 0
        val max = format.length
        while (i < max) {
            var c = format[i]

            if (c == '%') {
                wasPct = !wasPct
            } else if (wasPct) {
                wasPct = false

                if (c == 'n' || c == '<') { // not %n or %<x
                    i++
                    continue
                }

                if (c in '0'..'9') { // abs indexing %12$
                    val start = i

                    while (i + 1 < format.length && (format[i + 1].also { c = it }) >= '0' && c <= '9') {
                        i++
                    }

                    if (i + 1 < format.length && format[i + 1] == '$') {
                        i++
                        minRet = max(minRet.toDouble(), (format.substring(start, i).toInt() + 1).toDouble()).toInt()
                        i++
                        continue
                    } else {
                        i = start
                    }
                }

                ret++
            }
            i++
        }

        return max(ret.toDouble(), minRet.toDouble()).toInt()
    }

    private fun log(handler: LogHandler, level: LogLevel?, category: LogCategory?, msg: String, exc: Throwable?) {
        handler.log(System.currentTimeMillis(), level, category, msg.trim { it <= ' ' }, exc, false, false)
    }

    @JvmStatic
	fun shouldLog(level: LogLevel?, category: LogCategory?): Boolean {
        return handler.shouldLog(level, category)
    }
}
