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
import java.net.URI
import java.net.URISyntaxException
import java.nio.file.*
import java.util.*
import java.util.zip.ZipError

object FileSystemUtil {
    private val jfsArgsCreate: Map<String, String?> = Collections.singletonMap("create", "true")
    private val jfsArgsEmpty = emptyMap<String, String>()

    @JvmStatic
	@Throws(IOException::class)
    fun getJarFileSystem(path: Path, create: Boolean): FileSystemDelegate {
        return getJarFileSystem(path.toUri(), create)
    }

    @Throws(IOException::class)
    fun getJarFileSystem(uri: URI, create: Boolean): FileSystemDelegate {
        val jarUri: URI

        try {
            jarUri = URI("jar:" + uri.scheme, uri.host, uri.path, uri.fragment)
        } catch (e: URISyntaxException) {
            throw IOException(e)
        }

        var opened = false
        var ret: FileSystem?

        try {
            ret = FileSystems.getFileSystem(jarUri)
        } catch (ignore: FileSystemNotFoundException) {
            try {
                ret = FileSystems.newFileSystem(jarUri, if (create) jfsArgsCreate else jfsArgsEmpty)
                opened = true
            } catch (ignore2: FileSystemAlreadyExistsException) {
                ret = FileSystems.getFileSystem(jarUri)
            } catch (e: IOException) {
                throw IOException("Error accessing $uri: $e", e)
            } catch (e: ZipError) {
                throw IOException("Error accessing $uri: $e", e)
            }
        }

        return FileSystemDelegate(ret, opened)
    }

    class FileSystemDelegate(private val fileSystem: FileSystem?, private val owner: Boolean) : AutoCloseable {
        fun get(): FileSystem? {
            return fileSystem
        }

        @Throws(IOException::class)
        override fun close() {
            if (owner) {
                fileSystem!!.close()
            }
        }
    }
}
