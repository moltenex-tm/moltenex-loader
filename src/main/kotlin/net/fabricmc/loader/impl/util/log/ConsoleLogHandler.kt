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

import java.io.PrintWriter
import java.io.StringWriter

open class ConsoleLogHandler : LogHandler {
    override fun log(
        time: Long,
        level: LogLevel?,
        category: LogCategory?,
        msg: String?,
        exc: Throwable?,
        fromReplay: Boolean,
        wasSuppressed: Boolean
    ) {
        val formatted = level?.let {
            if (category != null) {
                formatLog(time, it, category, msg, exc)
            }
        }

        if (level != null) {
            if (level.isLessThan(MIN_STDERR_LEVEL)) {
                print(formatted)
            } else {
                System.err.print(formatted)
            }
        }
    }

    override fun shouldLog(level: LogLevel?, category: LogCategory?): Boolean {
        return !level!!.isLessThan(MIN_STDOUT_LEVEL)
    }

    override fun close() {}

    companion object {
        private val MIN_STDERR_LEVEL = LogLevel.ERROR
        private val MIN_STDOUT_LEVEL: LogLevel = LogLevel.default

        @JvmStatic
        protected fun formatLog(
            time: Long,
            level: LogLevel,
            category: LogCategory,
            msg: String?,
            exc: Throwable?
        ): String {
            var ret = String.format("[%tT] [%s] [%s/%s]: %s%n", time, level.name, category.context, category.name, msg)

            if (exc != null) {
                val writer = StringWriter(ret.length + 500)

                PrintWriter(writer, false).use { pw ->
                    pw.print(ret)
                    exc.printStackTrace(pw)
                }
                ret = writer.toString()
            }

            return ret
        }
    }
}
