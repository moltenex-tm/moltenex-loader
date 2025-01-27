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
package net.fabricmc.loader.impl

import net.fabricmc.loader.impl.util.Localization.format
import net.fabricmc.loader.impl.util.Localization.formatRoot

class FormattedException : RuntimeException {
	val mainText: String
    private var translatedText: String? = null

    constructor(mainText: String, message: String?) : super(message) {
        this.mainText = mainText
    }

    constructor(mainText: String, format: String, vararg args: Any?) : super(String.format(format, *args)) {
        this.mainText = mainText
    }

    constructor(mainText: String, message: String?, cause: Throwable?) : super(message, cause) {
        this.mainText = mainText
    }

    constructor(mainText: String, cause: Throwable?) : super(cause) {
        this.mainText = mainText
    }

    val displayedText: String
        get() = if (translatedText == null || translatedText == mainText) mainText else "$translatedText ($mainText)"

    private fun addTranslation(key: String): FormattedException {
        this.translatedText = format(key)
        return this
    }

    companion object {
        fun ofLocalized(key: String, message: String?): FormattedException {
            return FormattedException(formatRoot(key), message).addTranslation(key)
        }

        fun ofLocalized(key: String, format: String, vararg args: Any?): FormattedException {
            return FormattedException(formatRoot(key), format, *args).addTranslation(key)
        }

		fun ofLocalized(key: String, message: String?, cause: Throwable?): FormattedException {
            return FormattedException(formatRoot(key), message, cause).addTranslation(key)
        }

		fun ofLocalized(key: String, cause: Throwable?): FormattedException {
            return FormattedException(formatRoot(key), cause).addTranslation(key)
        }
    }
}
