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
import com.moltenex.loader.impl.launch.MappingConfiguration
import com.moltenex.loader.impl.launch.MoltenexLauncher
import com.moltenex.loader.impl.metadata.fabric.common.ModDependencyImpl
import net.fabricmc.api.EnvType
import net.fabricmc.loader.api.ObjectShare
import net.fabricmc.loader.api.VersionParsingException
import net.fabricmc.loader.api.metadata.ModDependency
import net.fabricmc.loader.impl.FormattedException
import net.fabricmc.loader.impl.game.GameProvider
import net.fabricmc.loader.impl.game.GameProvider.BuiltinMod
import net.fabricmc.loader.impl.game.GameProviderHelper.commonGameJar
import net.fabricmc.loader.impl.game.GameProviderHelper.deobfuscate
import net.fabricmc.loader.impl.game.GameProviderHelper.getEnvGameJar
import net.fabricmc.loader.impl.game.LibClassifier
import net.fabricmc.loader.impl.game.minecraft.McVersionLookup.getVersion
import net.fabricmc.loader.impl.game.minecraft.patch.BrandingPatch
import net.fabricmc.loader.impl.game.minecraft.patch.EntrypointPatch
import net.fabricmc.loader.impl.game.minecraft.patch.EntrypointPatchFML125
import net.fabricmc.loader.impl.game.minecraft.patch.TinyFDPatch
import net.fabricmc.loader.impl.game.patch.GameTransformer
import net.fabricmc.loader.impl.metadata.BuiltinModMetadata
import net.fabricmc.loader.impl.util.Arguments
import net.fabricmc.loader.impl.util.ExceptionUtil.wrap
import net.fabricmc.loader.impl.util.LoaderUtil.hasMacOs
import net.fabricmc.loader.impl.util.SystemProperties
import net.fabricmc.loader.impl.util.log.Log.configureBuiltin
import net.fabricmc.loader.impl.util.log.Log.init
import net.fabricmc.loader.impl.util.log.Log.warn
import net.fabricmc.loader.impl.util.log.LogCategory
import net.fabricmc.loader.impl.util.log.LogHandler
import java.io.IOException
import java.lang.invoke.MethodHandle
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*

class MinecraftGameProvider : GameProvider {
    private var envType: EnvType? = null
    private var entrypoint: String? = null
    private var arguments: Arguments? = null
    private val gameJars: MutableList<Path> = ArrayList(2) // env game jar and potentially common game jar
    private var realmsJar: Path? = null
    private val logJars: MutableSet<Path?> = HashSet()
    private var log4jAvailable = false
    private var slf4jAvailable = false
    private val miscGameLibraries: MutableList<Path> = ArrayList() // libraries not relevant for loader's uses
    private var validParentClassPath: Collection<Path?>? = null // computed parent class path restriction (loader+deps)
    private var versionData: McVersion? = null
    private var hasModLoader = false

    private val transformer = GameTransformer(
        EntrypointPatch(this),
        BrandingPatch(),
        EntrypointPatchFML125(),
        TinyFDPatch()
    )

    override fun getGameId(): String {
        return "minecraft"
    }

    override fun getGameName(): String {
        return "Minecraft"
    }

    override fun getRawGameVersion(): String {
        return versionData!!.raw
    }

    override fun getNormalizedGameVersion(): String {
        return versionData!!.normalized
    }

    override fun getBuiltinMods(): Collection<BuiltinMod> {
        val metadata = BuiltinModMetadata.Builder(getGameId(), getNormalizedGameVersion())
            .setName(getGameName())

        if (versionData!!.classVersion.isPresent) {
            val version = versionData!!.classVersion.asInt - 44

            try {
                metadata.addDependency(
                    ModDependencyImpl(
                        ModDependency.Kind.DEPENDS,
                        "java",
                        listOf(String.format(Locale.ENGLISH, ">=%d", version))
                    )
                )
            } catch (e: VersionParsingException) {
                throw RuntimeException(e)
            }
        }

        return listOf(BuiltinMod(gameJars.toList(), metadata.build()))
    }

    val gameJar: Path
        get() = gameJars[0]

    override fun getEntrypoint(): String {
        return entrypoint!!
    }

    override fun getLaunchDirectory(): Path {
        if (arguments == null) {
            return Paths.get(".")
        }

        return getLaunchDirectory(arguments!!)
    }

    override fun isObfuscated(): Boolean {
        return true // generally yes...
    }

    override fun requiresUrlClassLoader(): Boolean {
        return hasModLoader
    }

    override fun isEnabled(): Boolean {
        return System.getProperty(SystemProperties.SKIP_MC_PROVIDER) == null
    }

    override fun locateGame(launcher: MoltenexLauncher, args: Array<String>): Boolean {
        this.envType = launcher.environmentType
        this.arguments = Arguments()
        arguments!!.parse(args)

        try {
            val classifier = LibClassifier(
                McLibrary::class.java,
                envType!!, this
            )
            val envGameLib = if (envType == EnvType.CLIENT) McLibrary.MC_CLIENT else McLibrary.MC_SERVER
            var commonGameJar = commonGameJar
            var envGameJar = getEnvGameJar(envType!!)
            val commonGameJarDeclared = commonGameJar != null

            if (commonGameJarDeclared) {
                if (envGameJar != null) {
                    classifier.process(envGameJar, McLibrary.MC_COMMON)
                }

                classifier.process(commonGameJar!!)
            } else if (envGameJar != null) {
                classifier.process(envGameJar)
            }

            classifier.process(launcher.classPath!!)

            if (classifier.has(McLibrary.MC_BUNDLER)) {
                BundlerProcessor.process(classifier)
            }

            envGameJar = classifier.getOrigin(envGameLib)
            if (envGameJar == null) return false

            commonGameJar = classifier.getOrigin(McLibrary.MC_COMMON)

            if (commonGameJarDeclared && commonGameJar == null) {
                warn(
                    LogCategory.GAME_PROVIDER,
                    "The declared common game jar didn't contain any of the expected classes!"
                )
            }

            gameJars.add(envGameJar)

            if (commonGameJar != null && commonGameJar != envGameJar) {
                gameJars.add(commonGameJar)
            }

            val assetsJar = classifier.getOrigin(McLibrary.MC_ASSETS_ROOT)

            if (assetsJar != null && (assetsJar != commonGameJar) && (assetsJar != envGameJar)) {
                gameJars.add(assetsJar)
            }

            entrypoint = classifier.getClassName(envGameLib)
            realmsJar = classifier.getOrigin(McLibrary.REALMS)
            hasModLoader = classifier.has(McLibrary.MODLOADER)
            log4jAvailable = classifier.has(McLibrary.LOG4J_API) && classifier.has(McLibrary.LOG4J_CORE)
            slf4jAvailable = classifier.has(McLibrary.SLF4J_API) && classifier.has(McLibrary.SLF4J_CORE)
            val hasLogLib = log4jAvailable || slf4jAvailable

            configureBuiltin(hasLogLib, !hasLogLib)

            for (lib in McLibrary.LOGGING) {
                val path = classifier.getOrigin(lib)

                if (path != null) {
                    if (hasLogLib) {
                        logJars.add(path)
                    } else if (!gameJars.contains(path)) {
                        miscGameLibraries.add(path)
                    }
                }
            }

            miscGameLibraries.addAll(classifier.unmatchedOrigins)
            validParentClassPath = classifier.getSystemLibraries()
        } catch (e: IOException) {
            throw wrap(e)
        }

        // expose obfuscated jar locations for mods to more easily remap code from obfuscated to intermediary
        val share: ObjectShare = MoltenexLoaderImpl.INSTANCE!!.objectShare
        share.put("fabric-loader:inputGameJar", gameJars[0]) // deprecated
        share.put(
            "fabric-loader:inputGameJars",
            Collections.unmodifiableList(ArrayList(gameJars))
        ) // need to make copy as gameJars is later mutated to hold the remapped jars
        if (realmsJar != null) share.put("fabric-loader:inputRealmsJar", realmsJar)

        var version = arguments!!.remove(Arguments.GAME_VERSION)
        if (version == null) version = System.getProperty(SystemProperties.GAME_VERSION)
        versionData = getVersion(gameJars, entrypoint, version)

        processArgumentMap(arguments!!, envType!!)

        return true
    }

    override fun initialize(launcher: MoltenexLauncher) {
        launcher.setValidParentClassPath(validParentClassPath)

        if (isObfuscated()) {
            var obfJars: MutableMap<String?, Path?> = HashMap(3)
            val names = arrayOfNulls<String>(gameJars.size)

            for (i in gameJars.indices) {
                val name = if (i == 0) {
                    envType!!.name.lowercase()
                } else if (i == 1) {
                    "common"
                } else {
                    String.format(Locale.ENGLISH, "extra-%d", i - 2)
                }

                obfJars[name] = gameJars[i]
                names[i] = name
            }

            if (realmsJar != null) {
                obfJars["realms"] = realmsJar
            }

            var sourceNamespace = "official"

            val mappingConfig: MappingConfiguration? = launcher.mappingConfiguration
            val mappingNamespaces: List<String>? = mappingConfig?.getNamespaces()

            if (mappingNamespaces != null && !mappingNamespaces.contains(sourceNamespace)) {
                sourceNamespace = if (envType == EnvType.CLIENT) "clientOfficial" else "serverOfficial"
            }

            obfJars = deobfuscate(
                obfJars,
                getGameId(), getNormalizedGameVersion(),
                getLaunchDirectory(),
                launcher, sourceNamespace
            )

            for (i in gameJars.indices) {
                val newJar = obfJars[names[i]]
                val oldJar = gameJars.set(i, newJar!!)

                if (logJars.remove(oldJar)) logJars.add(newJar)
            }

            realmsJar = obfJars["realms"]
        }

        // Load the logger libraries on the platform CL when in a unit test
        if (!logJars.isEmpty() && !java.lang.Boolean.getBoolean(SystemProperties.UNIT_TEST)) {
            for (jar in logJars) {
                if (gameJars.contains(jar)) {
                    launcher.addToClassPath(jar, *ALLOWED_EARLY_CLASS_PREFIXES)
                } else {
                    launcher.addToClassPath(jar)
                }
            }
        }

        setupLogHandler(launcher, true)

        transformer.locateEntrypoints(launcher, gameJars)
    }

    private fun setupLogHandler(launcher: MoltenexLauncher, useTargetCl: Boolean) {
        System.setProperty(
            "log4j2.formatMsgNoLookups",
            "true"
        ) // lookups are not used by mc and cause issues with older log4j2 versions

        try {
            val logHandlerClsName = if (log4jAvailable) {
                "net.fabricmc.loader.impl.game.minecraft.Log4jLogHandler"
            } else if (slf4jAvailable) {
                "net.fabricmc.loader.impl.game.minecraft.Slf4jLogHandler"
            } else {
                return
            }

            val prevCl = Thread.currentThread().contextClassLoader
            val logHandlerCls: Class<*>?

            if (useTargetCl) {
                Thread.currentThread().contextClassLoader = launcher.targetClassLoader
                logHandlerCls = launcher.loadIntoTarget(logHandlerClsName)
            } else {
                logHandlerCls = Class.forName(logHandlerClsName)
            }

            init(logHandlerCls!!.getConstructor().newInstance() as LogHandler)
            Thread.currentThread().contextClassLoader = prevCl
        } catch (e: ReflectiveOperationException) {
            throw RuntimeException(e)
        }
    }

    override fun getArguments(): Arguments {
        return arguments!!
    }

    override fun getLaunchArguments(sanitize: Boolean): Array<String> {
        // If arguments is null, return an empty array
        if (arguments == null) return emptyArray()

        // Convert the arguments to a non-nullable Array<String>
        var ret = arguments!!.toArray()

        if (!sanitize) return ret

        var writeIdx = 0
        var i = 0

        // Process the arguments for sanitization
        while (i < ret.size) {
            val arg = ret[i]

            // Check for sensitive arguments
            if (i + 1 < ret.size && arg.startsWith("--") &&
                SENSITIVE_ARGS.contains(arg.substring(2).lowercase())
            ) {
                i++ // Skip the value associated with the sensitive argument
            } else {
                ret[writeIdx++] = arg
            }
            i++
        }

        return ret
    }



    override fun getEntrypointTransformer(): GameTransformer {
        return transformer
    }

    override fun canOpenErrorGui(): Boolean {
        if (arguments == null || envType == EnvType.CLIENT) {
            return true
        }

        val extras = arguments!!.getExtraArgs()
        return !extras.contains("nogui") && !extras.contains("--nogui")
    }

    override fun hasAwtSupport(): Boolean {
        // MC always sets -XstartOnFirstThread for LWJGL
        return !hasMacOs()
    }

    override fun unlockClassPath(launcher: MoltenexLauncher) {
        for (gameJar in gameJars) {
            if (logJars.contains(gameJar)) {
                launcher.setAllowedPrefixes(gameJar)
            } else {
                launcher.addToClassPath(gameJar)
            }
        }

        if (realmsJar != null) launcher.addToClassPath(realmsJar)

        for (lib in miscGameLibraries) {
            launcher.addToClassPath(lib)
        }
    }

    override fun launch(loader: ClassLoader) {
        var targetClass = entrypoint

        if (envType == EnvType.CLIENT && targetClass!!.contains("Applet")) {
            targetClass = "net.fabricmc.loader.impl.game.minecraft.applet.AppletMain"
        }

        val invoker: MethodHandle

        try {
            val c = loader.loadClass(targetClass)
            invoker = MethodHandles.lookup().findStatic(
                c, "main", MethodType.methodType(
                    Void.TYPE,
                    Array<String>::class.java
                )
            )
        } catch (e: NoSuchMethodException) {
            throw FormattedException.ofLocalized("exception.minecraft.invokeFailure", e)
        } catch (e: IllegalAccessException) {
            throw FormattedException.ofLocalized("exception.minecraft.invokeFailure", e)
        } catch (e: ClassNotFoundException) {
            throw FormattedException.ofLocalized("exception.minecraft.invokeFailure", e)
        }

        try {
            invoker.invokeExact(arguments!!.toArray())
        } catch (t: Throwable) {
            throw FormattedException.ofLocalized("exception.minecraft.generic", t)
        }
    }

    companion object {
        private val ALLOWED_EARLY_CLASS_PREFIXES = arrayOf("org.apache.logging.log4j.", "com.mojang.util.")

        private val SENSITIVE_ARGS: Set<String> = HashSet(
            mutableListOf( // all lowercase without --
                "accesstoken",
                "clientid",
                "profileproperties",
                "proxypass",
                "proxyuser",
                "username",
                "userproperties",
                "uuid",
                "xuid"
            )
        )

        private fun processArgumentMap(argMap: Arguments, envType: EnvType) {
            when (envType) {
                EnvType.CLIENT -> {
                    if (!argMap.containsKey("accessToken")) {
                        argMap.put("accessToken", "FabricMC")
                    }

                    if (!argMap.containsKey("version")) {
                        argMap.put("version", "Fabric")
                    }

                    var versionType = ""

                    if (argMap.containsKey("versionType") && !argMap.get("versionType")
                            .equals("release", ignoreCase = true)
                    ) {
                        versionType = argMap.get("versionType") + "/"
                    }

                    argMap.put("versionType", versionType + "Fabric")

                    if (!argMap.containsKey("gameDir")) {
                        argMap.put("gameDir", getLaunchDirectory(argMap).toAbsolutePath().normalize().toString())
                    }
                }

                EnvType.SERVER -> {
                    argMap.remove("version")
                    argMap.remove("gameDir")
                    argMap.remove("assetsDir")
                }
            }
        }

        private fun getLaunchDirectory(argMap: Arguments): Path {
            return Paths.get(argMap.getOrDefault("gameDir", "."))
        }
    }
}
