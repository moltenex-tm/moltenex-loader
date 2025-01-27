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
package com.moltenex.loader.impl.launch.server

import net.fabricmc.loader.impl.launch.knot.KnotServer
import net.fabricmc.loader.impl.util.LoaderUtil
import net.fabricmc.loader.impl.util.SystemProperties
import okio.buffer
import okio.source
import okio.sink
import java.io.IOException
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType
import java.net.URL
import java.nio.file.Path
import java.nio.file.Paths
import java.util.Properties

object ServerLauncher {
    private val parentLoader: ClassLoader = ServerLauncher::class.java.classLoader
    private var mainClass: String = KnotServer::class.java.name

    @JvmStatic
    fun main(args: Array<String>) {
        val propUrl: URL? = parentLoader.getResource("fabric-server-launch.properties")

        if (propUrl != null) {
            val properties = Properties()
            try {
                propUrl.openStream().source().buffer().use { source ->
                    properties.load(source.inputStream())
                }
            } catch (e: IOException) {
                e.printStackTrace()
            }

            if (properties.containsKey("launch.mainClass")) {
                mainClass = properties.getProperty("launch.mainClass")
            }
        }

        val dev = System.getProperty(SystemProperties.DEVELOPMENT, "false").toBoolean()

        if (!dev) {
            try {
                setup(*args)
            } catch (e: Exception) {
                throw RuntimeException("Failed to setup Fabric server environment!", e)
            }
        }

        try {
            val clazz = Class.forName(mainClass)
            MethodHandles.lookup()
                .findStatic(clazz, "main", MethodType.methodType(Void.TYPE, Array<String>::class.java))
                .invokeExact(args)
        } catch (e: Throwable) {
            throw RuntimeException("An exception occurred when launching the server!", e)
        }
    }

    @Throws(IOException::class)
    private fun setup(vararg runArguments: String) {
        if (System.getProperty(SystemProperties.GAME_JAR_PATH) == null) {
            System.setProperty(SystemProperties.GAME_JAR_PATH, getServerJarPath())
        }

        val serverJar: Path = LoaderUtil.normalizePath(Paths.get(System.getProperty(SystemProperties.GAME_JAR_PATH)))

        if (!serverJar.toFile().exists()) {
            System.err.println("The Minecraft server .JAR is missing ($serverJar)!")
            System.err.println()
            System.err.println("Fabric's server-side launcher expects the server .JAR to be provided.")
            System.err.println("You can edit its location in fabric-server-launcher.properties.")
            System.err.println()
            System.err.println("Without the official Minecraft server .JAR, Fabric Loader cannot launch.")
            throw RuntimeException("Missing game jar at $serverJar")
        }
    }

    @Throws(IOException::class)
    private fun getServerJarPath(): String {
        val propertiesFile = Paths.get("fabric-server-launcher.properties")
        val properties = Properties()

        if (propertiesFile.toFile().exists()) {
            propertiesFile.source().buffer().use { source ->
                properties.load(source.inputStream())
            }
        }

        if (!properties.containsKey("serverJar")) {
            properties["serverJar"] = "server.jar"

            propertiesFile.sink().buffer().use { sink ->
                properties.store(sink.outputStream(), null)
            }
        }

        return properties["serverJar"] as String
    }
}
