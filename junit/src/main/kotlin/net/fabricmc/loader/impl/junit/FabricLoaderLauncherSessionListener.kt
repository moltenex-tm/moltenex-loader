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
package net.fabricmc.loader.impl.junit

import net.fabricmc.api.EnvType
import net.fabricmc.loader.impl.launch.knot.Knot
import net.fabricmc.loader.impl.util.SystemProperties
import org.junit.platform.launcher.LauncherSession
import org.junit.platform.launcher.LauncherSessionListener

class FabricLoaderLauncherSessionListener : LauncherSessionListener {
    private var knot: Knot? = null
    private var classLoader: ClassLoader? = null

    private var launcherSessionClassLoader: ClassLoader? = null

    init {
        val currentThread = Thread.currentThread()
        val originalClassLoader = currentThread.contextClassLoader

        // parse the test environment type, defaults to client
        val envType = EnvType.valueOf(System.getProperty(SystemProperties.SIDE, EnvType.CLIENT.name).uppercase())
        try {
            knot = Knot(envType)
            classLoader = knot!!.init(arrayOf())
        } finally {
            // Knot.init sets the context class loader, revert it back for now.
            currentThread.contextClassLoader = originalClassLoader
        }
    }

    override fun launcherSessionOpened(session: LauncherSession) {
        val currentThread = Thread.currentThread()
        launcherSessionClassLoader = currentThread.contextClassLoader
        currentThread.contextClassLoader = classLoader
    }

    override fun launcherSessionClosed(session: LauncherSession) {
        val currentThread = Thread.currentThread()
        currentThread.contextClassLoader = launcherSessionClassLoader
    }

    companion object {
        init {
            System.setProperty(SystemProperties.DEVELOPMENT, "true")
            System.setProperty(SystemProperties.UNIT_TEST, "true")
        }
    }
}
