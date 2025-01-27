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
package net.fabricmc.loader.impl.game.minecraft

import com.moltenex.loader.impl.MoltenexLoaderImpl
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.api.DedicatedServerModInitializer
import net.fabricmc.api.ModInitializer
import net.fabricmc.loader.impl.util.log.Log.warn
import net.fabricmc.loader.impl.util.log.LogCategory
import java.io.File

object Hooks {
    @JvmField
	val INTERNAL_NAME: String = Hooks::class.java.name.replace('.', '/')

    @JvmField
	var appletMainClass: String? = null

    const val FABRIC: String = "fabric"
    const val VANILLA: String = "vanilla"

    fun insertBranding(brand: String?): String {
        if (brand == null || brand.isEmpty()) {
            warn(LogCategory.GAME_PROVIDER, "Null or empty branding found!", IllegalStateException())
            return FABRIC
        }

        return if (VANILLA == brand) FABRIC else brand + ',' + FABRIC
    }

    fun startClient(runDir: File?, gameInstance: Any?) {
        var runDir = runDir
        if (runDir == null) {
            runDir = File(".")
        }

        val loader = MoltenexLoaderImpl.INSTANCE
        loader!!.prepareModInit(runDir.toPath(), gameInstance)
        loader.invokeEntrypoints(
            "main",
            ModInitializer::class.java
        ) { obj: ModInitializer -> obj.onInitialize() }
        loader.invokeEntrypoints(
            "client",
            ClientModInitializer::class.java
        ) { obj: ClientModInitializer -> obj.onInitializeClient() }
    }

    fun startServer(runDir: File?, gameInstance: Any?) {
        var runDir = runDir
        if (runDir == null) {
            runDir = File(".")
        }

        val loader = MoltenexLoaderImpl.INSTANCE
        loader!!.prepareModInit(runDir.toPath(), gameInstance)
        loader.invokeEntrypoints(
            "main",
            ModInitializer::class.java
        ) { obj: ModInitializer -> obj.onInitialize() }
        loader.invokeEntrypoints(
            "server",
            DedicatedServerModInitializer::class.java
        ) { obj: DedicatedServerModInitializer -> obj.onInitializeServer() }
    }

    fun setGameInstance(gameInstance: Any?) {
        MoltenexLoaderImpl.INSTANCE!!.gameInstance = gameInstance
    }
}
