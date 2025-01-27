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
package com.moltenex.loader.impl.launch

import com.moltenex.loader.api.MoltenexLoader
import com.moltenex.loader.impl.MoltenexLoaderImpl
import net.fabricmc.api.EnvType
import java.io.IOException
import java.io.InputStream
import java.nio.file.Path
import java.util.jar.Manifest

interface MoltenexLauncher {
    companion object {
    /**
     * Returns the public-facing Fabric Loader instance.
     */
    fun getInstance(): MoltenexLoader {
        val ret: MoltenexLoader = MoltenexLoaderImpl.INSTANCE!!
        return ret
    }
    }

	val mappingConfiguration: MappingConfiguration?

    fun addToClassPath(path: Path?, vararg allowedPrefixes: String?)
    fun setAllowedPrefixes(path: Path?, vararg prefixes: String?)
    fun setValidParentClassPath(paths: Collection<Path?>?)

	val environmentType: EnvType?

    fun isClassLoaded(name: String?): Boolean

    /**
     * Load a class into the game's class loader even if its bytes are only available from the parent class loader.
     */
    @Throws(ClassNotFoundException::class)
    fun loadIntoTarget(name: String?): Class<*>?

    fun getResourceAsStream(name: String?): InputStream?

	val targetClassLoader: ClassLoader?

    /**
     * Gets the byte array for a particular class.
     *
     * @param name The name of the class to retrieve
     * @param runTransformers Whether to run all transformers *except mixin* on the class
     */
    @Throws(IOException::class)
    fun getClassByteArray(name: String?, runTransformers: Boolean): ByteArray?

    fun getManifest(originPath: Path?): Manifest?

	val isDevelopment: Boolean

	val entrypoint: String?

	val targetNamespace: String?

	val classPath: List<Path>?
}
