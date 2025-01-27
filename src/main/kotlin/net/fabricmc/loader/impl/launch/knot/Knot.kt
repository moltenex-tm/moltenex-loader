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

import com.moltenex.loader.impl.launch.MoltenexLauncherBase
import com.moltenex.loader.impl.launch.MoltenexMixinBootstrap
import net.fabricmc.api.EnvType
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint
import net.fabricmc.loader.impl.FormattedException
import net.fabricmc.loader.impl.FormattedException.Companion.ofLocalized
import com.moltenex.loader.impl.MoltenexLoaderImpl
import net.fabricmc.loader.impl.game.GameProvider
import net.fabricmc.loader.impl.launch.knot.KnotClassLoaderInterface.Companion.create
import net.fabricmc.loader.impl.util.LoaderUtil.normalizeExistingPath
import net.fabricmc.loader.impl.util.LoaderUtil.verifyClasspath
import net.fabricmc.loader.impl.util.LoaderUtil.verifyNotInTargetCl
import net.fabricmc.loader.impl.util.SystemProperties
import net.fabricmc.loader.impl.util.UrlUtil.getCodeSource
import net.fabricmc.loader.impl.util.log.Log.debug
import net.fabricmc.loader.impl.util.log.Log.error
import net.fabricmc.loader.impl.util.log.Log.finishBuiltinConfig
import net.fabricmc.loader.impl.util.log.Log.info
import net.fabricmc.loader.impl.util.log.Log.warn
import net.fabricmc.loader.impl.util.log.LogCategory
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*
import java.util.jar.Manifest
import java.util.stream.Collectors
import java.util.zip.ZipFile

class Knot(override var environmentType: EnvType?) : MoltenexLauncherBase() {
    private var classLoader: KnotClassLoaderInterface? = null
    override val classPath: MutableList<Path> = ArrayList()
    lateinit var provider: GameProvider
    private var unlocked = false

    fun init(args: Array<String>): ClassLoader? {
        setProperties(properties)

        // configure fabric vars
        if (environmentType == null) {
            val side = System.getProperty(SystemProperties.SIDE)
                ?: throw RuntimeException("Please specify side or use a dedicated Knot!")

            environmentType = when (side.lowercase()) {
                "client" -> EnvType.CLIENT
                "server" -> EnvType.SERVER
                else -> throw RuntimeException("Invalid side provided: must be \"client\" or \"server\"!")
            }
        }

        classPath.clear()

        var missing: MutableList<String?>? = null
        var unsupported: MutableList<String?>? = null

        for (cpEntry in System.getProperty("java.class.path").split(File.pathSeparator.toRegex())
            .dropLastWhile { it.isEmpty() }.toTypedArray()) {
            if (cpEntry == "*" || cpEntry.endsWith(File.separator + "*")) {
                if (unsupported == null) unsupported = ArrayList()
                unsupported.add(cpEntry)
                continue
            }

            val path = Paths.get(cpEntry)

            if (!Files.exists(path)) {
                if (missing == null) missing = ArrayList()
                missing.add(cpEntry)
                continue
            }

            classPath.add(normalizeExistingPath(path))
        }

        if (unsupported != null) warn(
            LogCategory.KNOT,
            "Knot does not support wildcard class path entries: %s - the game may not load properly!",
            java.lang.String.join(", ", unsupported)
        )
        if (missing != null) warn(
            LogCategory.KNOT,
            "Class path entries reference missing files: %s - the game may not load properly!",
            java.lang.String.join(", ", missing)
        )

        provider = createGameProvider(args)
        finishBuiltinConfig()
        info(
            LogCategory.GAME_PROVIDER,
            "Loading %s %s with Fabric Loader %s",
            provider.getGameName(),
            provider.getRawGameVersion(),
            MoltenexLoaderImpl.VERSION
        )

        // Setup classloader
        // TODO: Provide KnotCompatibilityClassLoader in non-exclusive-Fabric pre-1.13 environments?
        val useCompatibility = provider.requiresUrlClassLoader() || System.getProperty(
            "fabric.loader.useCompatibilityClassLoader",
            "false"
        ).toBoolean()
        classLoader = create(useCompatibility, isDevelopment, environmentType, provider)
        val cl = classLoader!!.classLoader

        provider.initialize(this)

        Thread.currentThread().contextClassLoader = cl

        val loader = MoltenexLoaderImpl.INSTANCE
        loader!!.gameProvider = provider
        loader.load()
        loader.freeze()

        MoltenexLoaderImpl.INSTANCE.loadAccessWideners()

        MoltenexMixinBootstrap.init(environmentType, loader)
        finishMixinBootstrapping()

        classLoader!!.initializeTransformers()

        provider.unlockClassPath(this)
        unlocked = true

        try {
            loader.invokeEntrypoints(
                "preLaunch",
                PreLaunchEntrypoint::class.java
            ) { obj: PreLaunchEntrypoint -> obj.onPreLaunch() }
        } catch (e: RuntimeException) {
            throw ofLocalized("exception.initializerFailure", e)
        }

        return cl
    }

    private fun createGameProvider(args: Array<String>): GameProvider {
        // fast path with direct lookup

        val embeddedGameProvider = findEmbedddedGameProvider()

        if (embeddedGameProvider != null && embeddedGameProvider.isEnabled()
            && embeddedGameProvider.locateGame(this, args)
        ) {
            return embeddedGameProvider
        }

        // slow path with service loader
        val failedProviders: MutableList<GameProvider> = ArrayList()

        for (provider in ServiceLoader.load(GameProvider::class.java)) {
            if (!provider.isEnabled()) continue  // don't attempt disabled providers and don't include them in the error report


            if (provider !== embeddedGameProvider // don't retry already failed provider
                && provider.locateGame(this, args)
            ) {
                return provider
            }

            failedProviders.add(provider)
        }

        // nothing found
        val msg = if (failedProviders.isEmpty()) {
            "No game providers present on the class path!"
        } else if (failedProviders.size == 1) {
            String.format(
                ("%s game provider couldn't locate the game! "
                        + "The game may be absent from the class path, lacks some expected files, suffers from jar "
                        + "corruption or is of an unsupported variety/version."),
                failedProviders[0].getGameName()
            )
        } else {
            String.format(
                "None of the game providers (%s) were able to locate their game!",
                failedProviders.stream().map { obj: GameProvider -> obj.getGameName() }
                    .collect(Collectors.joining(", ")))
        }

        error(LogCategory.GAME_PROVIDER, msg)

        throw RuntimeException(msg)
    }

    override val targetNamespace: String
        get() =// TODO: Won't work outside of Yarn
            if (isDevelopment) "named" else "intermediary"

    override fun addToClassPath(path: Path?, vararg allowedPrefixes: String?) {
        debug(LogCategory.KNOT, "Adding $path to classpath.")

        classLoader!!.setAllowedPrefixes(path, *allowedPrefixes)
        classLoader!!.addCodeSource(path)
    }

    override fun setAllowedPrefixes(path: Path?, vararg prefixes: String?) {
        classLoader!!.setAllowedPrefixes(path, *prefixes)
    }

    override fun setValidParentClassPath(paths: Collection<Path?>?) {
        classLoader!!.setValidParentClassPath(paths)
    }

    override fun isClassLoaded(name: String?): Boolean {
        return classLoader!!.isClassLoaded(name)
    }

    @Throws(ClassNotFoundException::class)
    override fun loadIntoTarget(name: String?): Class<*>? {
        return classLoader!!.loadIntoTarget(name)
    }

    override fun getResourceAsStream(name: String?): InputStream? {
        return classLoader!!.classLoader!!.getResourceAsStream(name)
    }

    override val targetClassLoader: ClassLoader?
        get() {
            val classLoader = this.classLoader

            return classLoader?.classLoader
        }

    @Throws(IOException::class)
    override fun getClassByteArray(name: String?, runTransformers: Boolean): ByteArray? {
        check(unlocked) { "early getClassByteArray access" }

        return if (runTransformers) {
            classLoader!!.getPreMixinClassBytes(name)
        } else {
            classLoader!!.getRawClassBytes(name)
        }
    }

    override fun getManifest(originPath: Path?): Manifest? {
        return classLoader!!.getManifest(originPath)
    }

    override val isDevelopment: Boolean = System.getProperty(SystemProperties.DEVELOPMENT, "false").toBoolean()
        get() = field

    override val entrypoint: String
        get() = provider.getEntrypoint()


    companion object {
        private var properties: Map<String, Any> = HashMap()
        fun setProperties(map: Map<String, Any>) {
            properties = map
        }

        fun launch(args: Array<String>, type: EnvType) {
            setupUncaughtExceptionHandler()

            try {
                val knot = Knot(type)
                val cl = knot.init(args)

                checkNotNull(knot.provider) { "Game provider was not initialized! (Knot#init(String[]))" }

                knot.provider.launch(cl!!)
            } catch (e: FormattedException) {
                handleFormattedException(e)
            }
        }

        /**
         * Find game provider embedded into the Fabric Loader jar, best effort.
         *
         *
         * This is faster than going through service loader because it only looks at a single jar.
         */
        private fun findEmbedddedGameProvider(): GameProvider? {
            try {
                val flPath = getCodeSource(Knot::class.java)
                if (flPath == null || !flPath.fileName.toString().endsWith(".jar")) return null // not a jar


                ZipFile(flPath.toFile()).use { zf ->
                    val entry = zf.getEntry("META-INF/services/net.fabricmc.loader.impl.game.GameProvider")
                        ?: return null // same file as used by service loader
                    zf.getInputStream(entry).use { `is` ->
                        var buffer = ByteArray(100)
                        var offset = 0
                        var len: Int

                        while ((`is`.read(buffer, offset, buffer.size - offset).also { len = it }) >= 0) {
                            offset += len
                            if (offset == buffer.size) buffer = buffer.copyOf(buffer.size * 2)
                        }

                        var content = String(buffer, 0, offset, StandardCharsets.UTF_8).trim { it <= ' ' }
                        if (content.indexOf('\n') >= 0) return null // potentially more than one entry -> bail out


                        val pos = content.indexOf('#')
                        if (pos >= 0) content = content.substring(0, pos).trim { it <= ' ' }
                        if (content.isNotEmpty()) {
                            return Class.forName(content).getConstructor().newInstance() as GameProvider
                        }
                    }
                }
                return null
            } catch (e: IOException) {
                throw RuntimeException(e)
            } catch (e: ReflectiveOperationException) {
                throw RuntimeException(e)
            }
        }


        fun main(args: Array<String>) {
            Knot(null).init(args)
        }

        init {
            verifyNotInTargetCl(Knot::class.java)
            verifyClasspath()
        }
    }
}
