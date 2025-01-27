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

import net.fabricmc.loader.impl.util.UrlUtil.asPath
import net.fabricmc.loader.impl.util.UrlUtil.asUrl
import java.io.IOException
import java.net.JarURLConnection
import java.net.MalformedURLException
import java.net.URISyntaxException
import java.net.URL
import java.nio.file.Files
import java.nio.file.Path
import java.util.*
import java.util.jar.Attributes
import java.util.jar.Manifest

object ManifestUtil {
    @Throws(IOException::class, URISyntaxException::class)
    fun readManifest(cls: Class<*>): Manifest? {
        val cs = cls.protectionDomain.codeSource ?: return null

        val url = cs.location ?: return null

        return readManifest(url)
    }

    @Throws(IOException::class, URISyntaxException::class)
    private fun readManifest(codeSourceUrl: URL): Manifest? {
        val path = asPath(codeSourceUrl)

        if (Files.isDirectory(path)) {
            return readManifestFromBasePath(path)
        } else {
            val connection = URL("jar:$codeSourceUrl!/").openConnection()

            if (connection is JarURLConnection) {
                return connection.manifest
            }

            FileSystemUtil.getJarFileSystem(path, false).use { jarFs ->
                return readManifestFromBasePath(jarFs.get()!!.rootDirectories.iterator().next())
            }
        }
    }

    @JvmStatic
	@Throws(IOException::class)
    fun readManifest(codeSource: Path): Manifest? {
        if (Files.isDirectory(codeSource)) {
            return readManifestFromBasePath(codeSource)
        } else {
            FileSystemUtil.getJarFileSystem(codeSource, false).use { jarFs ->
                return readManifestFromBasePath(jarFs.get()!!.rootDirectories.iterator().next())
            }
        }
    }

    @JvmStatic
	@Throws(IOException::class)
    fun readManifestFromBasePath(basePath: Path): Manifest? {
        val path = basePath.resolve("META-INF").resolve("MANIFEST.MF")
        if (!Files.exists(path)) return null

        Files.newInputStream(path).use { stream ->
            return Manifest(stream)
        }
    }

    @JvmStatic
	fun getManifestValue(manifest: Manifest, name: Attributes.Name?): String {
        return manifest.mainAttributes.getValue(name)
    }

    @JvmStatic
	@Throws(MalformedURLException::class)
    fun getClassPath(manifest: Manifest, baseDir: Path): List<URL>? {
        val cp = getManifestValue(
            manifest,
            Attributes.Name.CLASS_PATH
        )
            ?: return null

        val tokenizer = StringTokenizer(cp)
        val ret: MutableList<URL> = ArrayList()
        val context = asUrl(baseDir)

        while (tokenizer.hasMoreElements()) {
            ret.add(URL(context, tokenizer.nextToken()))
        }

        return ret
    }
}
