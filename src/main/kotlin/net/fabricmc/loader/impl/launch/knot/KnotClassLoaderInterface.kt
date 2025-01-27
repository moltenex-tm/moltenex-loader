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
import java.io.IOException
import java.nio.file.Path
import java.util.jar.Manifest

internal interface KnotClassLoaderInterface {
    fun initializeTransformers()

    val classLoader: ClassLoader?

    fun addCodeSource(path: Path?)
    fun setAllowedPrefixes(codeSource: Path?, vararg prefixes: String?)
    fun setValidParentClassPath(codeSources: Collection<Path?>?)

    fun getManifest(codeSource: Path?): Manifest?

    fun isClassLoaded(name: String?): Boolean

    @Throws(ClassNotFoundException::class)
    fun loadIntoTarget(name: String?): Class<*>?

    @Throws(IOException::class)
    fun getRawClassBytes(name: String?): ByteArray?
    fun getPreMixinClassBytes(name: String?): ByteArray?

    companion object {
        @JvmStatic
        fun create(
            useCompatibility: Boolean,
            isDevelopment: Boolean,
            envType: EnvType?,
            provider: GameProvider?
        ): KnotClassLoaderInterface {
            return if (useCompatibility) {
                KnotCompatibilityClassLoader(isDevelopment, envType, provider).getDelegate()
            } else {
                KnotClassLoader(isDevelopment, envType, provider).delegate
            }
        }
    }
}
