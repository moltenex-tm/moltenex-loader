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

import java.io.Closeable
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipError
import java.util.zip.ZipFile

class SimpleClassPath(@JvmField val paths: List<Path?>) : Closeable {
    @Throws(IOException::class)
    override fun close() {
        var exc: IOException? = null

        for (i in openJars.indices) {
            val file: Closeable? = openJars[i]

            try {
                file?.close()
            } catch (e: IOException) {
                if (exc == null) {
                    exc = e
                } else {
                    exc.addSuppressed(e)
                }
            }

            openJars[i] = null
        }

        if (exc != null) throw exc
    }

    @Throws(IOException::class)
    fun getEntry(subPath: String): CpEntry? {
        for (i in jarMarkers.indices) {
            if (jarMarkers[i]) {
                var zf = openJars[i]

                if (zf == null) {
                    val path = paths[i]

                    try {
                        zf = ZipFile(path?.toFile()!!)
                        openJars[i] = zf
                    } catch (e: IOException) {
                        throw IOException(String.format("error opening %s: %s", LoaderUtil.normalizePath(path!!), e), e)
                    } catch (e: ZipError) {
                        throw IOException(String.format("error opening %s: %s", LoaderUtil.normalizePath(path!!), e), e)
                    }
                }

                val entry = zf.getEntry(subPath)

                if (entry != null) {
                    return CpEntry(i, subPath, entry)
                }
            } else {
                val file = paths[i]!!.resolve(subPath)

                if (Files.isRegularFile(file)) {
                    return CpEntry(i, subPath, file)
                }
            }
        }

        return null
    }

    @Throws(IOException::class)
    fun getInputStream(subPath: String): InputStream? {
        val entry = getEntry(subPath)

        return entry?.inputStream
    }

    inner class CpEntry(private val idx: Int, val subPath: String, private val instance: Any) {
        val origin: Path
            get() = paths[idx]!!

        @get:Throws(IOException::class)
        val inputStream: InputStream
            get() {
                return if (instance is ZipEntry) {
                    openJars[idx]!!.getInputStream(instance)
                } else {
                    Files.newInputStream(instance as Path)
                }
            }

        override fun toString(): String {
            return String.format("%s:%s", origin, subPath)
        }
    }

    private val jarMarkers =
        BooleanArray(paths.size) // whether the path is a jar (otherwise plain dir)
    private val openJars = arrayOfNulls<ZipFile>(paths.size)

    init {
        for (i in jarMarkers.indices) {
            if (!Files.isDirectory(paths[i])) {
                jarMarkers[i] = true
            }
        }
    }
}
