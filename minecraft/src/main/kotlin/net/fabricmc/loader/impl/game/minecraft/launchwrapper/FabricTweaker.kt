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
package net.fabricmc.loader.impl.game.minecraft.launchwrapper

import com.moltenex.loader.impl.MoltenexLoaderImpl
import com.moltenex.loader.impl.launch.MoltenexLauncherBase
import com.moltenex.loader.impl.launch.MoltenexMixinBootstrap
import org.spongepowered.asm.launch.MixinBootstrap
import org.spongepowered.asm.mixin.MixinEnvironment
import net.fabricmc.api.EnvType
import net.fabricmc.loader.impl.FormattedException
import net.fabricmc.loader.impl.game.GameProvider
import net.fabricmc.loader.impl.game.minecraft.MinecraftGameProvider
import net.fabricmc.loader.impl.launch.knot.Knot.Companion.setProperties
import net.fabricmc.loader.impl.util.*
import net.fabricmc.loader.impl.util.log.Log
import net.fabricmc.loader.impl.util.log.LogCategory
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint
import net.minecraft.launchwrapper.ITweaker
import net.minecraft.launchwrapper.Launch
import net.minecraft.launchwrapper.LaunchClassLoader
import okio.IOException
import org.spongepowered.asm.mixin.transformer.Proxy
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.lang.reflect.Field
import java.net.MalformedURLException
import java.nio.file.Files
import java.nio.file.Path
import java.util.jar.JarEntry
import java.util.jar.JarInputStream
import java.util.jar.Manifest

abstract class FabricTweaker : MoltenexLauncherBase(), ITweaker {
    protected var arguments: Arguments? = null
    private var launchClassLoader: LaunchClassLoader? = null
    override val classPath: MutableList<Path> = ArrayList()
    final override var isDevelopment: Boolean = false
        private set

    private val isPrimaryTweaker = (Launch.blackboard.get("Tweaks") as List<*>).isEmpty()

    override val entrypoint: String
        get() = launchTarget

    override val targetNamespace: String
        get() =// TODO: Won't work outside of Yarn
            if (isDevelopment) "named" else "intermediary"

    override fun acceptOptions(localArgs: MutableList<String>, gameDir: File?, assetsDir: File?, profile: String) {
        arguments = Arguments()
        arguments!!.parse(localArgs)

        if (!arguments!!.containsKey("gameDir") && gameDir != null) {
            arguments!!.put("gameDir", gameDir.absolutePath)
        }

        if (environmentType === EnvType.CLIENT && !arguments!!.containsKey("assetsDir") && assetsDir != null) {
            arguments!!.put("assetsDir", assetsDir.absolutePath)
        }
    }

    override fun injectIntoClassLoader(launchClassLoader: LaunchClassLoader) {
        isDevelopment = System.getProperty(SystemProperties.DEVELOPMENT, "false").toBoolean()
        Launch.blackboard.put(SystemProperties.DEVELOPMENT, isDevelopment)
        setProperties(Launch.blackboard)

        this.launchClassLoader = launchClassLoader
        launchClassLoader.addClassLoaderExclusion("org.objectweb.asm.")
        launchClassLoader.addClassLoaderExclusion("org.spongepowered.asm.")
        launchClassLoader.addClassLoaderExclusion("net.fabricmc.loader.")

        launchClassLoader.addClassLoaderExclusion("net.fabricmc.api.Environment")
        launchClassLoader.addClassLoaderExclusion("net.fabricmc.api.EnvType")
        launchClassLoader.addClassLoaderExclusion("net.fabricmc.api.ModInitializer")
        launchClassLoader.addClassLoaderExclusion("net.fabricmc.api.ClientModInitializer")
        launchClassLoader.addClassLoaderExclusion("net.fabricmc.api.DedicatedServerModInitializer")

        try {
            init()
        } catch (e: FormattedException) {
            handleFormattedException(e)
        }
    }

    private fun init() {
        setupUncaughtExceptionHandler()

        classPath.clear()

        for (url in launchClassLoader!!.getSources()) {
            val path = UrlUtil.asPath(url)
            if (!Files.exists(path)) continue

            classPath.add(LoaderUtil.normalizeExistingPath(path))
        }

        val provider: GameProvider = MinecraftGameProvider()

        if (!provider.isEnabled()
            || !provider.locateGame(this, arguments!!.toArray())
        ) {
            throw RuntimeException("Could not locate Minecraft: provider locate failed")
        }

        Log.finishBuiltinConfig()

        arguments = null

        provider.initialize(this)

        val loader: MoltenexLoaderImpl = MoltenexLoaderImpl.INSTANCE!!
        loader.gameProvider = provider
        loader.load()
        loader.freeze()

        launchClassLoader!!.registerTransformer(FabricClassTransformer::class.java.name)
        MoltenexLoaderImpl.INSTANCE!!.loadAccessWideners()

        // Setup Mixin environment
        MixinBootstrap.init()
        MoltenexMixinBootstrap.init(environmentType, MoltenexLoaderImpl.INSTANCE!!)
        MixinEnvironment.getDefaultEnvironment()
            .setSide(if (environmentType === EnvType.CLIENT) MixinEnvironment.Side.CLIENT else MixinEnvironment.Side.SERVER)

        provider.unlockClassPath(this)

        try {
            loader.invokeEntrypoints(
                "preLaunch",
                PreLaunchEntrypoint::class.java
            ) { obj: PreLaunchEntrypoint -> obj.onPreLaunch() }
        } catch (e: RuntimeException) {
            throw FormattedException.ofLocalized("exception.initializerFailure", e)
        }
    }

    override fun addToClassPath(path: Path?, vararg allowedPrefixes: String?) {
        try {
            launchClassLoader!!.addURL(UrlUtil.asUrl(path!!))
            // allowedPrefixes handling is not implemented (no-op)
        } catch (e: MalformedURLException) {
            throw RuntimeException(e)
        }
    }

    override fun setAllowedPrefixes(path: Path?, vararg prefixes: String?) {
        // not implemented (no-op)
    }

    override fun setValidParentClassPath(paths: Collection<Path?>?) {
        // not implemented (no-op)
    }

    override fun isClassLoaded(name: String?): Boolean {
        throw RuntimeException("TODO isClassLoaded/launchwrapper")
    }

    @Throws(ClassNotFoundException::class)
    override fun loadIntoTarget(name: String?): Class<*> {
        return launchClassLoader!!.loadClass(name) // TODO: implement properly, this may load the class into the system class loader
    }

    override fun getResourceAsStream(name: String?): InputStream? {
        return launchClassLoader!!.getResourceAsStream(name)
    }

    override val targetClassLoader: ClassLoader?
        get() = launchClassLoader

    @Throws(IOException::class)
    override fun getClassByteArray(name: String?, runTransformers: Boolean): ByteArray? {
        val transformedName = name!!.replace('/', '.')
        var classBytes: ByteArray = launchClassLoader!!.getClassBytes(name)

        if (runTransformers) {
            for (transformer in launchClassLoader!!.getTransformers()) {
                if (transformer is Proxy) {
                    continue  // skip mixin as per method contract
                }

                classBytes = transformer.transform(name, transformedName, classBytes)
            }
        }

        return classBytes
    }

    override fun getManifest(originPath: Path?): Manifest? {
        try {
            return ManifestUtil.readManifest(originPath!!)
        } catch (e: IOException) {
            Log.warn(LOG_CATEGORY, "Error reading Manifest", e)
            return null
        }
    }

    // By default the remapped jar will be on the classpath after the obfuscated one.
    // This will lead to us finding and the launching the obfuscated one when we search
    // for the entrypoint.
    // To work around that, we pre-popuplate the LaunchClassLoader's resource cache,
    // which will then cause it to use the one we need it to.
    @Throws(IOException::class)
    private fun preloadRemappedJar(remappedJarFile: Path) {
        var resourceCache: MutableMap<String?, ByteArray?>? = null

        try {
            val f: Field = LaunchClassLoader::class.java.getDeclaredField("resourceCache")
            f.isAccessible = true
            resourceCache = f[launchClassLoader] as MutableMap<String?, ByteArray?>
        } catch (e: Exception) {
            e.printStackTrace()
        }

        if (resourceCache == null) {
            Log.warn(LOG_CATEGORY, "Resource cache not pre-populated - this will probably cause issues...")
            return
        }

        FileInputStream(remappedJarFile.toFile()).use { jarFileStream ->
            JarInputStream(jarFileStream).use { jarStream ->
                var entry: JarEntry
                while ((jarStream.getNextJarEntry().also { entry = it }) != null) {
                    if (entry.name.startsWith("net/minecraft/class_") || !entry.name.endsWith(".class")) {
                        // These will never be in the obfuscated jar, so we can safely skip them
                        continue
                    }

                    var className = entry.name
                    className = className.substring(0, className.length - 6).replace('/', '.')
                    Log.debug(LOG_CATEGORY, "Appending %s to resource cache...", className)
                    resourceCache[className] = toByteArray(jarStream)
                }
            }
        }
    }

    @Throws(IOException::class)
    private fun toByteArray(inputStream: InputStream): ByteArray {
        val estimate = inputStream.available()
        val outputStream = ByteArrayOutputStream(if (estimate < 32) 32768 else estimate)
        val buffer = ByteArray(8192)
        var len: Int

        while ((inputStream.read(buffer).also { len = it }) > 0) {
            outputStream.write(buffer, 0, len)
        }

        return outputStream.toByteArray()
    }

    companion object {
        private val LOG_CATEGORY: LogCategory = LogCategory.create("GameProvider", "Tweaker")
    }
}
