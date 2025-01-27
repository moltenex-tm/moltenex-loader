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

import net.fabricmc.loader.impl.util.LoaderUtil
import net.fabricmc.loader.impl.util.SystemProperties
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardOpenOption

/**
 * Default LogHandler until Log is initialized.
 *
 *
 * The log handler has the following properties:
 * - log to stdout for anything but LogLevel.ERROR
 * - log to stderr for LogLevel.ERROR
 * - option to relay previous log output to another log handler if requested through Log.init
 * - dumps previous log output to a log file if not closed/relayed yet
 */
internal class BuiltinLogHandler : ConsoleLogHandler() {
    private var configured = false
    private var enableOutput = false
    private var buffer: MutableList<ReplayEntry>? = ArrayList()
    private val shutdownHook: Thread

    init {
        shutdownHook = ShutdownHook()
        Runtime.getRuntime().addShutdownHook(shutdownHook)
    }

    override fun log(
        time: Long,
        level: LogLevel?,
        category: LogCategory?,
        msg: String?,
        exc: Throwable?,
        fromReplay: Boolean,
        wasSuppressed: Boolean
    ) {
        val output: Boolean

        synchronized(this) {
            if (enableOutput) {
                output = true
            } else if (level!!.isLessThan(LogLevel.ERROR)) {
                output = false
            } else {
                startOutput()
                output = true
            }
            if (buffer != null) {
                buffer!!.add(ReplayEntry(time, level!!, category!!, msg!!, exc!!))
            }
        }

        if (output) super.log(time, level, category, msg, exc, fromReplay, wasSuppressed)
    }

    private fun startOutput() {
        if (enableOutput) return

        if (buffer != null) {
            for (i in buffer!!.indices) { // index based loop to tolerate replay producing log output by itself
                val entry = buffer!![i]
                super.log(entry.time, entry.level, entry.category, entry.msg, entry.exc, true, true)
            }
        }

        enableOutput = true
    }

    override fun close() {
        val shutdownHook = this.shutdownHook

        try {
            Runtime.getRuntime().removeShutdownHook(shutdownHook)
        } catch (e: IllegalStateException) {
            // ignore
        }
    }

    @Synchronized
    fun configure(buffer: Boolean, output: Boolean) {
        require(!(!buffer && !output)) { "can't both disable buffering and the output" }

        if (output) {
            startOutput()
        } else {
            enableOutput = false
        }

        if (buffer) {
            if (this.buffer == null) this.buffer = ArrayList()
        } else {
            this.buffer = null
        }

        configured = true
    }

    @Synchronized
    fun finishConfig() {
        if (!configured) configure(false, true)
    }

    @Synchronized
    fun replay(target: LogHandler): Boolean {
        if (buffer == null || buffer!!.isEmpty()) return false

        for (i in buffer!!.indices) { // index based loop to tolerate replay producing log output by itself
            val entry = buffer!![i]
            target.log(entry.time, entry.level, entry.category, entry.msg, entry.exc, true, !enableOutput)
        }

        return true
    }

    private class ReplayEntry(
        val time: Long,
        val level: LogLevel,
        val category: LogCategory,
        val msg: String,
        val exc: Throwable
    )

    private inner class ShutdownHook : Thread("BuiltinLogHandler shutdown hook") {
        override fun run() {
            synchronized(this@BuiltinLogHandler) {
                if (buffer == null || buffer!!.isEmpty()) return
                if (!enableOutput) {
                    enableOutput = true

                    for (i in buffer!!.indices) { // index based loop to tolerate replay producing log output by itself
                        val entry = buffer!![i]
                        super@BuiltinLogHandler.log(
                            entry.time,
                            entry.level,
                            entry.category,
                            entry.msg,
                            entry.exc,
                            true,
                            true
                        )
                    }
                }

                val fileName = System.getProperty(SystemProperties.LOG_FILE, DEFAULT_LOG_FILE)
                if (fileName.isEmpty()) return
                try {
                    val file = LoaderUtil.normalizePath(Paths.get(fileName))
                    Files.createDirectories(file.parent)

                    Files.newBufferedWriter(
                        file,
                        StandardOpenOption.WRITE,
                        StandardOpenOption.TRUNCATE_EXISTING,
                        StandardOpenOption.CREATE
                    ).use { writer ->
                        for (i in buffer!!.indices) { // index based loop to tolerate replay producing log output by itself
                            val entry = buffer!![i]
                            writer.write(formatLog(entry.time, entry.level, entry.category, entry.msg, entry.exc))
                        }
                    }
                } catch (e: IOException) {
                    System.err.printf("Error saving log: %s", e)
                }
            }
        }
    }

    companion object {
        private const val DEFAULT_LOG_FILE = "fabricloader.log"
    }
}
