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
package net.fabricmc.loader.impl.game.minecraft.patch

import com.moltenex.loader.impl.MoltenexLoaderImpl
import com.moltenex.loader.impl.launch.MoltenexLauncherBase
import net.fabricmc.loader.impl.game.minecraft.MinecraftGameProvider
import net.fabricmc.loader.impl.util.UrlUtil.asPath
import net.fabricmc.loader.impl.util.UrlUtil.asUrl
import java.io.File
import java.io.IOException
import java.net.MalformedURLException
import java.net.URL
import java.net.URLClassLoader
import java.util.*

/**
 * Wrapper class replacing pre-1.3 FML's ModClassLoader (which relies on
 * URLClassLoader implementation details - no longer applicable in Java 9+)
 * with an implementation effectively wrapping Knot.
 */
class ModClassLoader_125_FML : URLClassLoader(arrayOfNulls<URL>(0), MoltenexLauncherBase.launcher!!.targetClassLoader) {
    private var localUrls: Array<URL>

    override fun addURL(url: URL) {
        // Ensure the launcher is not null before proceeding
        MoltenexLauncherBase.launcher!!.addToClassPath(asPath(url))

        // Safely create a new array with the extra space and cast it to non-nullable URL
        val newLocalUrls = localUrls.copyOf(localUrls.size + 1) as Array<URL>

        // Add the new URL to the last position
        newLocalUrls[localUrls.size] = url

        // Reassign the localUrls array with the updated one
        localUrls = newLocalUrls
    }


    override fun getURLs(): Array<URL> {
        return localUrls
    }

    override fun findResource(name: String): URL? {
        return parent.getResource(name)
    }

    @Throws(IOException::class)
    override fun findResources(name: String): Enumeration<URL> {
        return parent.getResources(name)
    }

    /**
     * This is used to add mods to the classpath.
     * @param file The mod file.
     * @throws MalformedURLException If the File->URL transformation fails.
     */
    @Throws(MalformedURLException::class)
    fun addFile(file: File) {
        try {
            addURL(asUrl(file))
        } catch (e: MalformedURLException) {
            throw MalformedURLException(e.message)
        }
    }

    val parentSource: File
        /**
         * This is used to find the Minecraft .JAR location.
         *
         * @return The "parent source" file.
         */
        get() = (MoltenexLoaderImpl.INSTANCE!!.gameProvider as MinecraftGameProvider).gameJar.toFile()

    val parentSources: Array<File>
        /**
         * @return The "parent source" files array.
         */
        get() = arrayOf(parentSource)

    init {
        localUrls = emptyArray()
    }

    companion object {
        init {
            registerAsParallelCapable()
        }
    }
}
