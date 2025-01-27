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

import java.io.File
import java.net.JarURLConnection
import java.net.MalformedURLException
import java.net.URISyntaxException
import java.net.URL
import java.nio.file.Path
import java.nio.file.Paths

object UrlUtil {
	val LOADER_CODE_SOURCE: Path? = getCodeSource(UrlUtil::class.java)

	@Throws(UrlConversionException::class)
    fun getCodeSource(url: URL, localPath: String): Path {
        try {
            val connection = url.openConnection()

            return if (connection is JarURLConnection) {
                asPath(connection.jarFileURL)
            } else {
                val path = url.path

                if (path.endsWith(localPath)) {
                    asPath(
                        URL(
                            url.protocol,
                            url.host,
                            url.port,
                            path.substring(0, path.length - localPath.length)
                        )
                    )
                } else {
                    throw UrlConversionException("Could not figure out code source for file '$localPath' in URL '$url'!")
                }
            }
        } catch (e: Exception) {
            throw UrlConversionException(e)
        }
    }

    @JvmStatic
	fun asPath(url: URL): Path {
        try {
            return Paths.get(url.toURI())
        } catch (e: URISyntaxException) {
            throw ExceptionUtil.wrap(e)
        }
    }

    @JvmStatic
	@Throws(MalformedURLException::class)
    fun asUrl(file: File): URL {
        return file.toURI().toURL()
    }

    @JvmStatic
	@Throws(MalformedURLException::class)
    fun asUrl(path: Path): URL {
        return path.toUri().toURL()
    }

    fun getCodeSource(cls: Class<*>): Path? {
        val cs = cls.protectionDomain.codeSource ?: return null

        return asPath(cs.location)
    }
}
