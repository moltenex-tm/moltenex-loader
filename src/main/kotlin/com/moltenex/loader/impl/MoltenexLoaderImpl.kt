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
package com.moltenex.loader.impl

import com.moltenex.loader.api.MoltenexLoader
import com.moltenex.loader.impl.launch.MoltenexLauncherBase
import net.fabricmc.accesswidener.AccessWidener
import net.fabricmc.accesswidener.AccessWidenerReader
import net.fabricmc.api.EnvType
import net.fabricmc.loader.api.*
import net.fabricmc.loader.api.entrypoint.EntrypointContainer
import net.fabricmc.loader.impl.*
import net.fabricmc.loader.impl.FormattedException.Companion.ofLocalized
import net.fabricmc.loader.impl.discovery.*
import net.fabricmc.loader.impl.discovery.ModResolutionException
import net.fabricmc.loader.impl.entrypoint.EntrypointStorage
import net.fabricmc.loader.impl.game.GameProvider
import net.fabricmc.loader.impl.launch.knot.Knot
import net.fabricmc.loader.impl.metadata.DependencyOverrides
import net.fabricmc.loader.impl.metadata.LoaderModMetadata
import net.fabricmc.loader.impl.metadata.VersionOverrides
import net.fabricmc.loader.impl.util.DefaultLanguageAdapter
import net.fabricmc.loader.impl.util.ExceptionUtil.gatherExceptions
import net.fabricmc.loader.impl.util.LoaderUtil
import net.fabricmc.loader.impl.util.SystemProperties
import net.fabricmc.loader.impl.util.log.Log
import net.fabricmc.loader.impl.util.log.LogCategory
import org.objectweb.asm.Opcodes
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*
import java.util.function.Consumer
import java.util.stream.Collectors

class MoltenexLoaderImpl private constructor() : MoltenexLoader {
    protected val modMap: MutableMap<String?, ModContainerImpl> = HashMap()
    private var modCandidates: MutableList<ModCandidateImpl?>? = null
    protected var mods: MutableList<ModContainerImpl> = ArrayList()

    private val adapterMap: MutableMap<String?, LanguageAdapter?> = HashMap()
    private val entrypointStorage = EntrypointStorage()
    val accessWidener: AccessWidener = AccessWidener()

    override val objectShare: ObjectShare = ObjectShareImpl()

    private var frozen = false

    override var gameInstance: Any? = null
        set(value) {
            if (environmentType != EnvType.SERVER) {
                throw UnsupportedOperationException("Cannot set game instance on a client!")
            }

            if (this.gameInstance != null) {
                throw UnsupportedOperationException("Cannot overwrite current game instance!")
            }

            field = value
        }

    override var mappingResolver: MappingResolver? = null
        get() {
            if (field == null) {
                val targetNamespace: String = MoltenexLauncherBase.launcher!!.targetNamespace!!

                field = LazyMappingResolver({
                    MappingResolverImpl(
                        MoltenexLauncherBase.launcher!!.mappingConfiguration?.getMappings()!!,
                        targetNamespace
                    )
                }, targetNamespace)
            }

            return field
        }
        private set

    private var provider: GameProvider? = null
    override var gameDir: Path? = null
        set(value) {
            field = value
            this.configDir = value!!.resolve("config")
        }
        get() {
            checkNotNull(field) { "invoked too early?" }
            return field!!
        }

    override var configDir: Path? = null
        get() {
            if (!Files.exists(field!!)) {
                try {
                    Files.createDirectories(field!!)
                } catch (e: IOException) {
                    throw RuntimeException("Creating config directory", e)
                }
            }

            return field
        }

    /**
     * Freeze the FabricLoader, preventing additional mods from being loaded.
     */
    fun freeze() {
        check(!frozen) { "Already frozen!" }

        frozen = true
        finishModLoading()
    }

    var gameProvider: GameProvider
        get() {
            checkNotNull(provider) { "game provider not set (yet)" }

            return this.provider!!
        }
        set(provider) {
            this.provider = provider

            gameDir = provider.getLaunchDirectory()
        }

    fun tryGetGameProvider(): GameProvider? {
        return provider
    }

    override val environmentType: EnvType
        get() = MoltenexLauncherBase.launcher!!.environmentType!!

    @get:Deprecated("")
    override val gameDirectory: File
        get() = gameDir!!.toFile()

    @get:Deprecated("")
    override val configDirectory: File
        get() = configDir!!.toFile()

    fun load() {
        checkNotNull(provider) { "game provider not set" }
        check(!frozen) { "Frozen - cannot load additional mods!" }

        try {
            setup()
        } catch (exception: ModResolutionException) {
            if (exception.cause == null) {
                throw ofLocalized("exception.incompatible", exception.message)
            } else {
                throw ofLocalized("exception.incompatible", exception)
            }
        }
    }

    @Throws(ModResolutionException::class)
    private fun setup() {
        val remapRegularMods = isDevelopmentEnvironment()
        val versionOverrides = VersionOverrides()
        val depOverrides = DependencyOverrides(configDir!!)

        // discover mods
        val discoverer = ModDiscoverer(versionOverrides, depOverrides)
        discoverer.addCandidateFinder(ClasspathModCandidateFinder())
        discoverer.addCandidateFinder(DirectoryModCandidateFinder(modsDirectory0, remapRegularMods))
        discoverer.addCandidateFinder(ArgumentModCandidateFinder(remapRegularMods))

        val envDisabledMods: MutableMap<String, MutableSet<ModCandidateImpl>> = mutableMapOf()
        var modCandidates = discoverer.discoverMods(this, envDisabledMods)

        // dump version and dependency overrides info
        if (versionOverrides.affectedModIds.isNotEmpty()) {
            Log.info(LogCategory.GENERAL, "Versions overridden for %s", versionOverrides.affectedModIds.joinToString(", "))
        }

        if (depOverrides.affectedModIds.isNotEmpty()) {
            Log.info(LogCategory.GENERAL, "Dependencies overridden for %s", depOverrides.affectedModIds.joinToString(", "))
        }

        // resolve mods
        modCandidates = ModResolver.resolve(modCandidates, environmentType, envDisabledMods).toMutableList()

        dumpModList(modCandidates)
        dumpNonFabricMods(discoverer.nonFabricMods)

        val cacheDir = gameDir?.resolve(CACHE_DIR_NAME)
        val outputdir = cacheDir?.resolve(PROCESSED_MODS_DIR_NAME)

        // runtime mod remapping
        if (remapRegularMods) {
            if (System.getProperty(SystemProperties.REMAP_CLASSPATH_FILE) == null) {
                Log.warn(LogCategory.MOD_REMAP, "Runtime mod remapping disabled due to no fabric.remapClasspathFile being specified. You may need to update loom.")
            } else {
                RuntimeModRemapper.remap(modCandidates, cacheDir!!.resolve(TMP_DIR_NAME), outputdir!!)
            }
        }

        // shuffle mods in-dev to reduce the risk of false order reliance, apply late load requests
        if (isDevelopmentEnvironment() && System.getProperty(SystemProperties.DEBUG_DISABLE_MOD_SHUFFLE) == null) {
            Collections.shuffle(modCandidates)
        }

        val modsToLoadLate = System.getProperty(SystemProperties.DEBUG_LOAD_LATE)

        if (modsToLoadLate != null) {
            for (modId in modsToLoadLate.split(",")) {
                for (mod in modCandidates) {
                    if (mod.id == modId) {
                        modCandidates.remove(mod)
                        modCandidates.add(mod)
                        break
                    }
                }
            }
        }

        // add mods
        for (mod in modCandidates) {
            if (!mod.hasPath() && !mod.isBuiltin) {
                try {
                    mod.paths = listOf(mod.copyToDir(outputdir!!, false))
                } catch (e: IOException) {
                    throw RuntimeException("Error extracting mod $mod", e)
                }
            }
            addMod(mod)
        }
    }

//TODO check this method might delete it
    fun dumpNonFabricMods(nonFabricMods: List<Path>) {
        if (nonFabricMods.isEmpty()) return
        val outputText = StringBuilder()

        for (nonFabricMod in nonFabricMods) {
            outputText.append("\n\t- ").append(nonFabricMod.fileName)
        }

        val modsCount = nonFabricMods.size
        Log.warn(
            LogCategory.GENERAL,
            "Found %d non-fabric mod%s:%s",
            modsCount,
            if (modsCount != 1) "s" else "",
            outputText
        )
    }

    private fun dumpModList(mods: List<ModCandidateImpl?>?) {
        val modListText = StringBuilder()

        val lastItemOfNestLevel = BooleanArray(mods!!.size)
        val topLevelMods = mods.stream()
            .filter { mod: ModCandidateImpl? -> mod!!.getParentMods().isEmpty() }
            .collect(Collectors.toList())
        val topLevelModsCount = topLevelMods.size

        for (i in 0..<topLevelModsCount) {
            val lastItem = i == topLevelModsCount - 1

            if (lastItem) lastItemOfNestLevel[0] = true

            dumpModList0(topLevelMods[i], modListText, 0, lastItemOfNestLevel)
        }

        val modsCount = mods.size
        Log.info(LogCategory.GENERAL, "Loading %d mod%s:%n%s", modsCount, if (modsCount != 1) "s" else "", modListText)
    }

    private fun dumpModList0(
        mod: ModCandidateImpl?,
        log: StringBuilder,
        nestLevel: Int,
        lastItemOfNestLevel: BooleanArray
    ) {
        if (log.length > 0) log.append('\n')

        for (depth in 0..<nestLevel) {
            log.append(if (depth == 0) "\t" else if (lastItemOfNestLevel[depth]) "     " else "   | ")
        }

        log.append(if (nestLevel == 0) "\t" else "  ")
        log.append(if (nestLevel == 0) "-" else if (lastItemOfNestLevel[nestLevel]) " \\--" else " |--")
        log.append(' ')
        log.append(mod!!.id)
        log.append(' ')
        log.append(mod.version!!.friendlyString)

        val nestedMods: ArrayList<ModCandidateImpl?> = ArrayList<ModCandidateImpl?>(mod.nestedMods)
        nestedMods.sortWith(Comparator.comparing { nestedMod -> nestedMod?.metadata?.id!! })

        if (!nestedMods.isEmpty()) {
            val iterator = nestedMods.iterator()
            var nestedMod: ModCandidateImpl
            var lastItem: Boolean

            while (iterator.hasNext()) {
                nestedMod = iterator.next()!!
                lastItem = !iterator.hasNext()

                if (lastItem) lastItemOfNestLevel[nestLevel + 1] = true

                dumpModList0(nestedMod, log, nestLevel + 1, lastItemOfNestLevel)

                if (lastItem) lastItemOfNestLevel[nestLevel + 1] = false
            }
        }
    }

    private fun finishModLoading() {
        // add mods to classpath
        // TODO: This can probably be made safer, but that's a long-term goal
        for (mod in mods) {
            if (mod.metadata.id != MOD_ID && mod.metadata.type != "builtin") {
                for (path in mod.codeSourcePaths!!) {
                    MoltenexLauncherBase.launcher!!.addToClassPath(path)
                }
            }
        }

        setupLanguageAdapters()
        setupMods()
    }

    fun hasEntrypoints(key: String?): Boolean {
        return entrypointStorage.hasEntrypoints(key!!)
    }

    override fun <T> getEntrypoints(key: String?, type: Class<T>?): List<T> {
        return entrypointStorage.getEntrypoints(key!!, type!!)
    }

    override fun <T> getEntrypointContainers(key: String?, type: Class<T>?): List<EntrypointContainer<T>> {
        return entrypointStorage.getEntrypointContainers(key!!, type!!)
    }

    override fun <T> invokeEntrypoints(key: String?, type: Class<T>?, invoker: Consumer<in T>?) {
        if (!hasEntrypoints(key)) {
            Log.debug(LogCategory.ENTRYPOINT, "No subscribers for entrypoint '%s'", key)
            return
        }

        var exception: RuntimeException? = null
        val entrypoints: Collection<EntrypointContainer<T>> = INSTANCE!!.getEntrypointContainers(key, type)

        Log.debug(LogCategory.ENTRYPOINT, "Iterating over entrypoint '%s'", key)

        for (container in entrypoints) {
            try {
                invoker!!.accept(container.entrypoint)
            } catch (t: Throwable) {
                exception = gatherExceptions(
                    t,
                    exception
                ) { exc: Throwable? ->
                    RuntimeException(
                        String.format(
                            "Could not execute entrypoint stage '%s' due to errors, provided by '%s' at '%s'!",
                            key, container.provider!!.metadata!!.id, container.definition
                        ),
                        exc
                    )
                }
            }
        }

        if (exception != null) {
            throw exception
        }
    }

    fun getModCandidate(id: String): ModCandidateImpl? {
        if (modCandidates == null) return null

        for (mod in modCandidates!!) {
            if (mod!!.id == id) return mod
        }

        return null
    }

    override fun getModContainer(id: String?): Optional<ModContainer> {
        return Optional.ofNullable(modMap[id])
    }

    override val allMods: Collection<ModContainer>
        get() = Collections.unmodifiableList<ModContainer>(mods)

    val modsInternal: List<ModContainerImpl>
        get() = mods

    override fun isModLoaded(id: String?): Boolean {
        return modMap.containsKey(id)
    }

    override fun isDevelopmentEnvironment(): Boolean {
        return MoltenexLauncherBase.launcher!!.isDevelopment
    }

    @Throws(ModResolutionException::class)
    private fun addMod(candidate: ModCandidateImpl?) {
        val container = ModContainerImpl(candidate!!)
        mods.add(container)
        modMap[candidate.id] = container

        for (provides in candidate.provides!!) {
            modMap[provides] = container
        }
    }

    private fun setupLanguageAdapters() {
        adapterMap["default"] = DefaultLanguageAdapter.INSTANCE

        for (mod in mods) {
            // add language adapters
            for ((key, value) in mod.getInfo().languageAdapterDefinitions?.entries!!) {
                if (adapterMap.containsKey(key)) {
                    throw RuntimeException("Duplicate language adapter key: " + key + "! (" + value + ", " + adapterMap[key]!!.javaClass.name + ")")
                }

                try {
                    adapterMap[key.toString()] =
                        Class.forName(value, true, MoltenexLauncherBase.launcher!!.targetClassLoader)
                            .getDeclaredConstructor().newInstance() as LanguageAdapter
                } catch (e: Exception) {
                    throw RuntimeException("Failed to instantiate language adapter: $key", e)
                }
            }
        }
    }

    private fun setupMods() {
        for (mod in mods) {
            try {
                for (`in` in mod.getInfo().oldInitializers!!) {

                    entrypointStorage.addDeprecated(mod, adapterMap.toString(), `in`!!)
                }

                for (key in mod.getInfo().entrypointKeys!!) {
                    for (`in` in mod.getInfo().getEntrypoints(key)!!) {
                        entrypointStorage.add(mod, key!!, `in`!!, adapterMap)
                    }
                }
            } catch (e: Exception) {
                throw RuntimeException(
                    java.lang.String.format(
                        "Failed to setup mod %s (%s)",
                        mod.getInfo().name,
                        mod.origin
                    ), e
                )
            }
        }
    }

    fun loadAccessWideners() {
        val accessWidenerReader = AccessWidenerReader(accessWidener)

        for (modContainer in allMods) {
            val modMetadata = modContainer.metadata as LoaderModMetadata?
            val accessWidener = modMetadata!!.accessWidener ?: continue

            val path = modContainer.findPath(accessWidener).orElse(null)
                ?: throw RuntimeException(
                    String.format(
                        "Missing accessWidener file %s from mod %s",
                        accessWidener,
                        modContainer.metadata!!.id
                    )
                )

            try {
                Files.newBufferedReader(path).use { reader ->
                    accessWidenerReader.read(reader, MoltenexLauncherBase.launcher!!.targetNamespace)
                }
            } catch (e: Exception) {
                throw RuntimeException("Failed to read accessWidener file from mod " + modMetadata.id, e)
            }
        }
    }

    fun prepareModInit(newRunDir: Path, gameInstance: Any?) {
        if (!frozen) {
            throw RuntimeException("Cannot instantiate mods when not frozen!")
        }

        if (gameInstance != null && MoltenexLauncherBase.launcher!! is Knot) {
            var gameClassLoader = gameInstance.javaClass.classLoader
            val targetClassLoader: ClassLoader = MoltenexLauncherBase.launcher!!.targetClassLoader!!
            val matchesKnot = (gameClassLoader === targetClassLoader)
            var containsKnot = false

            if (matchesKnot) {
                containsKnot = true
            } else {
                gameClassLoader = gameClassLoader!!.parent

                while (gameClassLoader != null && gameClassLoader.parent !== gameClassLoader) {
                    if (gameClassLoader === targetClassLoader) {
                        containsKnot = true
                    }

                    gameClassLoader = gameClassLoader.parent
                }
            }

            if (!matchesKnot) {
                if (containsKnot) {
                    Log.info(LogCategory.KNOT, "Environment: Target class loader is parent of game class loader.")
                } else {
                    Log.warn(
                        LogCategory.KNOT, ("""

* CLASS LOADER MISMATCH! THIS IS VERY BAD AND WILL PROBABLY CAUSE WEIRD ISSUES! *
 - Expected game class loader: %s
 - Actual game class loader: %s
Could not find the expected class loader in game class loader parents!
"""),
                        MoltenexLauncherBase.launcher!!.targetClassLoader, gameClassLoader
                    )
                }
            }
        }

        this.gameInstance = gameInstance

        if (gameDir != null) {
            try {
                if (gameDir!!.toRealPath() != newRunDir.toRealPath()) {
                    Log.warn(
                        LogCategory.GENERAL,
                        "Inconsistent game execution directories: engine says %s, while initializer says %s...",
                        newRunDir.toRealPath(),
                        gameDir!!.toRealPath()
                    )
                    gameDir = newRunDir
                }
            } catch (e: IOException) {
                Log.warn(LogCategory.GENERAL, "Exception while checking game execution directory consistency!", e)
            }
        } else {
            gameDir = newRunDir
        }
    }

    override fun getLaunchArguments(sanitize: Boolean): Array<String> {
        return gameProvider.getLaunchArguments(sanitize)
    }

    protected val modsDirectory0: Path
        get() {
            val directory = System.getProperty(SystemProperties.MODS_FOLDER)

            return if (directory != null) Paths.get(directory) else gameDir!!.resolve("mods")
        }

    /**
     * Provides singleton for static init assignment regardless of load order.
     */
    object InitHelper {
        private var instance: MoltenexLoaderImpl? = null

        fun get(): MoltenexLoaderImpl? {
            if (instance == null) instance = MoltenexLoaderImpl()

            return instance
        }
    }

    companion object {
        val INSTANCE: MoltenexLoaderImpl? = InitHelper.get()

        const val ASM_VERSION: Int = Opcodes.ASM9

        const val VERSION: String = "0.0.1-BETA"
        const val MOD_ID: String = "moltenex-loader"

        const val CACHE_DIR_NAME: String = ".moltenex" // relative to game dir
        private const val PROCESSED_MODS_DIR_NAME = "processedMods" // relative to cache dir
        const val REMAPPED_JARS_DIR_NAME: String = "remappedJars" // relative to cache dir
        private const val TMP_DIR_NAME = "tmp" // relative to cache dir

        init {
            LoaderUtil.verifyNotInTargetCl(MoltenexLoaderImpl::class.java)
        }
    }
}
