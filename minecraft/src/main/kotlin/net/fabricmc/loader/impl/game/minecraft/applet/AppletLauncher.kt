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
package net.fabricmc.loader.impl.game.minecraft.applet

import com.moltenex.loader.impl.launch.MoltenexLauncherBase
import net.fabricmc.loader.impl.game.minecraft.Hooks
import java.applet.Applet
import java.applet.AppletStub
import java.awt.BorderLayout
import java.awt.Dimension
import java.io.File
import java.lang.reflect.InvocationTargetException
import java.net.MalformedURLException
import java.net.URL

/**
 * PLEASE NOTE:
 *
 *
 * This class is originally copyrighted under Apache License 2.0
 * by the MCUpdater project (https://github.com/MCUpdater/MCU-Launcher/).
 *
 *
 * It has been adapted here for the purposes of the Fabric loader.
 */
open class AppletLauncher(
    gameDir: File,
	username: String,
	sessionid: String,
	host: String,
	port: String,
	doConnect: Boolean,
	fullscreen: Boolean,
	demo: Boolean
) :
    Applet(), AppletStub {
    private val params: MutableMap<String, String> = HashMap()
    private var mcApplet: Applet? = null
    private var active = false

    init {
        getGameDir = gameDir
        params["username"] = username
        params["sessionid"] = sessionid
        params["stand-alone"] = "true"

        if (doConnect) {
            params["server"] = host
            params["port"] = port
        }

        params["fullscreen"] = fullscreen.toString() //Required param for vanilla. Forge handles the absence gracefully.
        params["demo"] = demo.toString()

        try {
            mcApplet = MoltenexLauncherBase.launcher
                ?.targetClassLoader
                ?.loadClass(Hooks.appletMainClass)
                ?.getDeclaredConstructor()
                ?.newInstance() as Applet?

            if (mcApplet == null) {
                throw RuntimeException("Could not instantiate MinecraftApplet - is null?")
            }

            this.add(mcApplet, "Center")
        } catch (e: InstantiationException) {
            throw RuntimeException(e)
        } catch (e: InvocationTargetException) {
            throw RuntimeException(e)
        } catch (e: IllegalAccessException) {
            throw RuntimeException(e)
        } catch (e: NoSuchMethodException) {
            throw RuntimeException(e)
        } catch (e: ClassNotFoundException) {
            throw RuntimeException(e)
        }
    }

    fun getParams(): Map<String, String> {
        return params
    }

    // 1.3 ~ 1.5 FML
    fun replace(applet: Applet?) {
        this.mcApplet = applet
        init()

        if (active) {
            start()
            validate()
        }
    }

    override fun appletResize(width: Int, height: Int) {
        mcApplet!!.resize(width, height)
    }

    override fun resize(width: Int, height: Int) {
        mcApplet!!.resize(width, height)
    }

    override fun resize(dim: Dimension) {
        mcApplet!!.resize(dim)
    }

    override fun getParameter(name: String): String? {
        val value = params[name]
        if (value != null) return value

        try {
            return super.getParameter(name)
        } catch (ignored: Exception) {
            // ignored
        }

        return null
    }

    override fun isActive(): Boolean {
        return this.active
    }

    override fun init() {
        mcApplet!!.setStub(this)
        mcApplet!!.setSize(width, height)
        layout = BorderLayout()
        this.add(mcApplet, "Center")
        mcApplet!!.init()
    }

    override fun start() {
        mcApplet!!.start()
        active = true
    }

    override fun stop() {
        mcApplet!!.stop()
        active = false
    }

    private val minecraftHostingUrl: URL?
        /**
         * Minecraft 0.30 checks for "minecraft.net" or "www.minecraft.net" being
         * the applet hosting location, as an anti-rehosting measure. Of course,
         * being ran stand-alone, it's not actually "hosted" anywhere.
         *
         *
         * The side effect of not providing the correct URL here is all levels,
         * loaded or generated, being set to null.
         */
        get() {
            try {
                return URL("http://www.minecraft.net/game")
            } catch (e: MalformedURLException) {
                e.printStackTrace()
            }

            return null
        }

    override fun getCodeBase(): URL? {
        return minecraftHostingUrl
    }

    override fun getDocumentBase(): URL? {
        return minecraftHostingUrl
    }

    override fun setVisible(flag: Boolean) {
        super.setVisible(flag)
        mcApplet!!.isVisible = flag
    }
    companion object{
        var getGameDir: File? = null
    }

}
