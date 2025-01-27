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

import java.io.IOException
import java.net.URL
import java.util.*

internal class DummyClassLoader : ClassLoader() {
    @Throws(ClassNotFoundException::class)
    override fun loadClass(name: String, resolve: Boolean): Class<*> {
        throw ClassNotFoundException(name)
    }

    override fun getResource(name: String): URL? {
        return null
    }

    @Throws(IOException::class)
    override fun getResources(var1: String): Enumeration<URL?> {
        return NULL_ENUMERATION
    }

    companion object {
        private val NULL_ENUMERATION: Enumeration<URL?> = object : Enumeration<URL?> {
            override fun hasMoreElements(): Boolean {
                return false
            }

            override fun nextElement(): URL? {
                return null
            }
        }

        init {
            registerAsParallelCapable()
        }
    }
}
