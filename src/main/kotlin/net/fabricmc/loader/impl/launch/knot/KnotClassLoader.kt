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
package net.fabricmc.loader.impl.launch.knot

import net.fabricmc.api.EnvType
import net.fabricmc.loader.impl.game.GameProvider
import net.fabricmc.loader.impl.mrj.AbstractSecureClassLoader
import java.io.IOException
import java.io.InputStream
import java.net.URL
import java.net.URLClassLoader
import java.security.CodeSource
import java.util.*

// class name referenced by string constant in net.fabricmc.loader.impl.util.LoaderUtil.verifyNotInTargetCl
internal class KnotClassLoader(isDevelopment: Boolean, envType: EnvType?, provider: GameProvider?) :
    AbstractSecureClassLoader("knot", DynamicURLClassLoader(arrayOfNulls(0))),
    KnotClassDelegate.ClassLoaderAccess {
    private class DynamicURLClassLoader(urls: Array<URL?>) : URLClassLoader(urls, DummyClassLoader()) {
        public override fun addURL(url: URL) {
            super.addURL(url)
        }

        companion object {
            init {
                registerAsParallelCapable()
            }
        }
    }

    private val urlLoader = parent as DynamicURLClassLoader

    private val originalLoader: ClassLoader = javaClass.classLoader
    val delegate =
        KnotClassDelegate(isDevelopment, envType!!, this, originalLoader, provider!!)

    override fun getResource(name: String): URL {
        Objects.requireNonNull(name)

        var url = urlLoader.getResource(name)

        if (url == null) {
            url = originalLoader.getResource(name)
        }

        return url!!
    }

    public override fun findResource(name: String): URL {
        Objects.requireNonNull(name)

        return urlLoader.findResource(name)
    }

    @Throws(IOException::class)
    public override fun findResources(name: String): Enumeration<URL> {
        Objects.requireNonNull(name)

        return urlLoader.findResources(name)
    }

    override fun getResourceAsStream(name: String): InputStream {
        Objects.requireNonNull(name)

        var inputStream = urlLoader.getResourceAsStream(name)

        if (inputStream == null) {
            inputStream = originalLoader.getResourceAsStream(name)
        }

        return inputStream!!
    }

    @Throws(IOException::class)
    override fun getResources(name: String): Enumeration<URL> {
        Objects.requireNonNull(name)

        val resources = urlLoader.getResources(name)

        if (!resources.hasMoreElements()) {
            return originalLoader.getResources(name)
        }

        return resources
    }

    @Throws(ClassNotFoundException::class)
    override fun loadClass(name: String, resolve: Boolean): Class<*>? {
        return delegate.loadClass(name, resolve)
    }

    @Throws(ClassNotFoundException::class)
    override fun findClass(name: String): Class<*>? {
        return delegate.tryLoadClass(name, false)
    }

    override fun addUrlFwd(url: URL?) {
        if (url != null) {
            urlLoader.addURL(url)
        }
    }

    override fun findResourceFwd(name: String?): URL? {
        return urlLoader.findResource(name)
    }

    override fun getPackageFwd(name: String?): Package? {
        return super.getPackage(name)
    }

    @Throws(IllegalArgumentException::class)
    override fun definePackageFwd(
        name: String?,
        specTitle: String?,
        specVersion: String?,
        specVendor: String?,
        implTitle: String?,
        implVersion: String?,
        implVendor: String?,
        sealBase: URL?
    ): Package? {
        return super.definePackage(
            name,
            specTitle,
            specVersion,
            specVendor,
            implTitle,
            implVersion,
            implVendor,
            sealBase
        )
    }

    override fun getClassLoadingLockFwd(name: String?): Any? {
        return super.getClassLoadingLock(name)
    }

    override fun findLoadedClassFwd(name: String?): Class<*>? {
        return super.findLoadedClass(name)
    }

    override fun defineClassFwd(name: String?, b: ByteArray?, off: Int, len: Int, cs: CodeSource?): Class<*>? {
        return super.defineClass(name, b, off, len, cs)
    }

    override fun resolveClassFwd(cls: Class<*>?) {
        super.resolveClass(cls)
    }

    companion object {
        init {
            registerAsParallelCapable()
        }
    }
}
