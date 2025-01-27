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

import java.io.IOException
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.text.MessageFormat
import java.util.*

object Localization {
    val BUNDLE: ResourceBundle = createBundle("net.fabricmc.loader.Messages", Locale.getDefault())
    val ROOT_LOCALE_BUNDLE: ResourceBundle = createBundle("net.fabricmc.loader.Messages", Locale.ROOT)

    @JvmStatic
	fun format(key: String, vararg args: Any?): String {
        val pattern = BUNDLE.getString(key)

        return if (args.size == 0) {
            pattern
        } else {
            MessageFormat.format(pattern, *args)
        }
    }

    @JvmStatic
	fun formatRoot(key: String, vararg args: Any?): String {
        val pattern = ROOT_LOCALE_BUNDLE.getString(key)

        return if (args.size == 0) {
            pattern
        } else {
            MessageFormat.format(pattern, *args)
        }
    }

    private fun createBundle(name: String, locale: Locale): ResourceBundle {
        if (System.getProperty("java.version", "").startsWith("1.")) { // below java 9
            return ResourceBundle.getBundle(name, locale, object : ResourceBundle.Control() {
                @Throws(
                    IllegalAccessException::class,
                    InstantiationException::class,
                    IOException::class
                )
                override fun newBundle(
                    baseName: String,
                    locale: Locale,
                    format: String,
                    loader: ClassLoader,
                    reload: Boolean
                ): ResourceBundle {
                    if (format == "java.properties") {
                        val `is` =
                            loader.getResourceAsStream(toResourceName(toBundleName(baseName, locale), "properties"))

                        if (`is` != null) {
                            InputStreamReader(`is`, StandardCharsets.UTF_8).use { reader ->
                                return PropertyResourceBundle(reader)
                            }
                        }
                    }

                    return super.newBundle(baseName, locale, format, loader, reload)
                }
            })
        } else { // java 9 and later
            return ResourceBundle.getBundle(name, locale)
        }
    }
}
