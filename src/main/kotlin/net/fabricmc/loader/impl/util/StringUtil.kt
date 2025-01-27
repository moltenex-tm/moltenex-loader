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

object StringUtil {
    @JvmStatic
	fun capitalize(s: String): String {
        if (s.isEmpty()) return s

        var pos = 0
        while (pos < s.length) {
            if (Character.isLetterOrDigit(s.codePointAt(pos))) {
                break
            }
            pos++
        }

        if (pos == s.length) return s

        val cp = s.codePointAt(pos)
        val cpUpper: Int = cp.toChar().uppercaseChar().code
        if (cpUpper == cp) return s

        val ret = StringBuilder(s.length)
        ret.append(s, 0, pos)
        ret.appendCodePoint(cpUpper)
        ret.append(s, pos + Character.charCount(cp), s.length)

        return ret.toString()
    }

    fun splitNamespaced(s: String, defaultNamespace: String): Array<String> {
        val i = s.indexOf(':')

        return if (i >= 0) {
            arrayOf(s.substring(0, i), s.substring(i + 1))
        } else {
            arrayOf(defaultNamespace, s)
        }
    }

    @JvmStatic
	fun wrapLines(str: String, limit: Int): String {
        if (str.length < limit) return str

        val sb = StringBuilder(str.length + 20)
        var lastSpace = -1
        var len = 0

        var i = 0
        val max = str.length
        while (i <= max) {
            val c = if (i < max) str[i] else ' '

            if (c == '\r') {
                // ignore
            } else if (c == '\n') {
                lastSpace = sb.length
                sb.append(c)
                len = 0
            } else if (Character.isWhitespace(c)) {
                if (len > limit && lastSpace >= 0) {
                    sb.setCharAt(lastSpace, '\n')
                    len = sb.length - lastSpace - 1
                }

                if (i == max) break

                if (len >= limit) {
                    lastSpace = -1
                    sb.append('\n')
                    len = 0
                } else {
                    lastSpace = sb.length
                    sb.append(c)
                    len++
                }
            } else if (c == '"' || c == '\'') {
                var next = str.indexOf(c, i + 1) + 1
                if (next <= 0) next = str.length
                sb.append(str, i, next)
                len += next - i
                i = next - 1
            } else {
                sb.append(c)
                len++
            }
            i++
        }

        return sb.toString()
    }
}
