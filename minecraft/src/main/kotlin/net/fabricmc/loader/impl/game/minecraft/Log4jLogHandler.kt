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
package net.fabricmc.loader.impl.game.minecraft

import com.moltenex.loader.api.util.version.Version
import net.fabricmc.loader.api.VersionParsingException
import net.fabricmc.loader.impl.util.ManifestUtil
import net.fabricmc.loader.impl.util.log.Log
import net.fabricmc.loader.impl.util.log.LogCategory
import net.fabricmc.loader.impl.util.log.LogHandler
import net.fabricmc.loader.impl.util.log.LogLevel
import okio.IOException
import org.apache.logging.log4j.Level
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import java.beans.PropertyChangeEvent
import java.beans.PropertyChangeListener
import java.net.URISyntaxException
import java.util.jar.Attributes
import java.util.jar.Manifest

class Log4jLogHandler : LogHandler {
    override fun shouldLog(level: LogLevel?, category: LogCategory?): Boolean {
        return getLogger(category!!)!!.isEnabled(translateLogLevel(level))
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
        // TODO: suppress console log output if wasSuppressed is false to avoid duplicate output
        getLogger(category!!)!!.log(translateLogLevel(level), msg, exc)
    }

    override fun close() {}

    companion object {
        private fun getLogger(category: LogCategory): Logger? {
            var ret: Logger? = category.data as Logger?

            if (ret == null) {
                ret = LogManager.getLogger(category.toString())
                category.data = ret
            }

            return ret
        }

        private fun translateLogLevel(level: LogLevel?): Level {
            // can't use enum due to it generating a nested class, which would have to be on the same class loader as Log4jLogHandler
            if (level == LogLevel.ERROR) return Level.ERROR
            if (level == LogLevel.WARN) return Level.WARN
            if (level == LogLevel.INFO) return Level.INFO
            if (level == LogLevel.DEBUG) return Level.DEBUG
            if (level == LogLevel.TRACE) return Level.TRACE

            throw IllegalArgumentException("unknown log level: $level")
        }

        init {
            if (needsLookupRemoval()) {
                patchJndi()
            } else {
                Log.debug(LogCategory.GAME_PROVIDER, "Log4J2 JNDI removal is unnecessary")
            }
        }

        private fun needsLookupRemoval(): Boolean {
            val manifest: Manifest?

            try {
                manifest = ManifestUtil.readManifest(LogManager::class.java)
            } catch (e: IOException) {
                Log.warn(LogCategory.GAME_PROVIDER, "Can't read Log4J2 Manifest", e)
                return true
            } catch (e: URISyntaxException) {
                Log.warn(LogCategory.GAME_PROVIDER, "Can't read Log4J2 Manifest", e)
                return true
            }

            if (manifest == null) return true

            val title = ManifestUtil.getManifestValue(manifest, Attributes.Name.IMPLEMENTATION_TITLE)
            if (title == null || !title.lowercase().contains("log4j")) return true

            val version = ManifestUtil.getManifestValue(
                manifest,
                Attributes.Name.IMPLEMENTATION_VERSION
            )
                ?: return true

            try {
                return Version.parse(version)
                    .compareTo(Version.parse("2.16")) < 0 // 2.15+ doesn't lookup by default, but we patch anything up to 2.16 just in case
            } catch (e: VersionParsingException) {
                Log.warn(LogCategory.GAME_PROVIDER, "Can't parse Log4J2 Manifest version %s", version, e)
                return true
            }
        }

        private fun patchJndi() {
            val context = LogManager.getContext(false)

            try {
                context.javaClass.getMethod("addPropertyChangeListener", PropertyChangeListener::class.java)
                    .invoke(context, object : PropertyChangeListener {
                        override fun propertyChange(evt: PropertyChangeEvent?) {
                            if (evt!!.propertyName == "config") {
                                removeSubstitutionLookups(true)
                            }
                        }
                    })
            } catch (e: Exception) {
                Log.warn(LogCategory.GAME_PROVIDER, "Can't register Log4J2 PropertyChangeListener: %s", e.toString())
            }

            removeSubstitutionLookups(false)
        }

        private fun removeSubstitutionLookups(ignoreMissing: Boolean) {
            // strip the jndi lookup and then all over lookups from the active org.apache.logging.log4j.core.lookup.Interpolator instance's lookups map

            try {
                val context = LogManager.getContext(false)
                if (context.javaClass.name == "org.apache.logging.log4j.simple.SimpleLoggerContext") return  // -> no log4j core


                val config = context.javaClass.getMethod("getConfiguration").invoke(context)
                val substitutor = config.javaClass.getMethod("getStrSubstitutor").invoke(config)
                val varResolver = substitutor.javaClass.getMethod("getVariableResolver").invoke(substitutor) ?: return

                var removed = false

                for (field in varResolver.javaClass.declaredFields) {
                    if (MutableMap::class.java.isAssignableFrom(field.type)) {
                        field.isAccessible = true
                        val map = field[varResolver] as MutableMap<String, *>

                        if (map.remove("jndi") != null) {
                            map.clear()
                            removed = true
                            break
                        }
                    }
                }

                if (!removed) {
                    if (ignoreMissing) return
                    throw RuntimeException("couldn't find JNDI lookup entry")
                }

                Log.debug(LogCategory.GAME_PROVIDER, "Removed Log4J2 substitution lookups")
            } catch (e: Exception) {
                Log.warn(LogCategory.GAME_PROVIDER, "Can't remove Log4J2 JNDI substitution Lookup: %s", e.toString())
            }
        }
    }
}
