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
package net.fabricmc.loader.impl.discovery

import com.moltenex.loader.impl.MoltenexLoaderImpl
import net.fabricmc.loader.api.SemanticVersion
import net.fabricmc.loader.impl.FormattedException
import net.fabricmc.loader.impl.discovery.ModCandidateFinder.ModCandidateConsumer
import net.fabricmc.loader.impl.discovery.ModCandidateImpl.Companion.createBuiltin
import net.fabricmc.loader.impl.discovery.ModCandidateImpl.Companion.hash
import net.fabricmc.loader.impl.game.GameProvider.BuiltinMod
import net.fabricmc.loader.impl.metadata.*
import net.fabricmc.loader.impl.metadata.MetadataVerifier.verifyIndev
import net.fabricmc.loader.impl.metadata.ModMetadataParser.parseMetadata
import net.fabricmc.loader.impl.util.ExceptionUtil.gatherExceptions
import net.fabricmc.loader.impl.util.ExceptionUtil.wrap
import net.fabricmc.loader.impl.util.LoaderUtil.normalizeExistingPath
import net.fabricmc.loader.impl.util.SystemProperties
import net.fabricmc.loader.impl.util.log.Log.debug
import net.fabricmc.loader.impl.util.log.Log.info
import net.fabricmc.loader.impl.util.log.Log.warn
import net.fabricmc.loader.impl.util.log.LogCategory
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*
import java.util.concurrent.*
import java.util.stream.Collectors
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import kotlin.math.max
import kotlin.math.min

class ModDiscoverer(private val versionOverrides: VersionOverrides, private val depOverrides: DependencyOverrides) {
    private val candidateFinders: MutableList<ModCandidateFinder> = ArrayList()
    private val envType = MoltenexLoaderImpl.INSTANCE!!.environmentType
    private val jijDedupMap: MutableMap<Long, ModScanTask> = ConcurrentHashMap() // avoids reading the same jar twice
    private val nestedModInitDatas: MutableList<NestedModInitData> =
        Collections.synchronizedList(ArrayList()) // breaks potential cycles from deduplication
    val nonFabricMods: MutableList<Path> = Collections.synchronizedList(ArrayList())
        get() = Collections.unmodifiableList(field)

    fun addCandidateFinder(f: ModCandidateFinder) {
        candidateFinders.add(f)
    }

    @Throws(ModResolutionException::class)
    fun discoverMods(
        loader: MoltenexLoaderImpl,
        envDisabledModsOut: MutableMap<String, MutableSet<ModCandidateImpl>>
    ): MutableList<ModCandidateImpl> {
        val startTime = System.nanoTime()
        val pool = ForkJoinPool()
        val processedPaths: MutableSet<Path> = HashSet() // suppresses duplicate paths
        val futures: MutableList<ForkJoinTask<ModCandidateImpl?>> = ArrayList()

        val taskSubmitter = ModCandidateConsumer { paths: List<Path>, requiresRemap: Boolean ->
            val pendingPaths: MutableList<Path> = ArrayList(paths.size)
            for (path in paths) {
                assert(path == normalizeExistingPath(path))

                if (processedPaths.add(path)) {
                    pendingPaths.add(path)
                }
            }
            if (pendingPaths.isNotEmpty()) {
                futures.add(pool.submit(ModScanTask(pendingPaths, requiresRemap)))
            }
        }

        for (finder in candidateFinders) {
            finder.findCandidates(taskSubmitter)
        }

        val candidates: MutableList<ModCandidateImpl> = ArrayList()

        // add builtin mods
        for (mod in loader.gameProvider.getBuiltinMods()) {
            if (mod.metadata.version !is SemanticVersion) {
                val error = java.lang.String.format(
                    "%s uses the non-semantic version %s, which doesn't support range comparisons and may cause mod dependencies against it to fail unexpectedly. Consider updating Fabric Loader or explicitly specifying the game version with the fabric.gameVersion system property.",
                    mod.metadata.id, mod.metadata.version
                )

                if (loader.isDevelopmentEnvironment()) { // fail hard in-dev
                    throw FormattedException("Invalid game version", error)
                } else {
                    warn(LogCategory.GENERAL, error)
                }
            }

            val candidate = createBuiltin(mod, versionOverrides, depOverrides)
            candidates.add(verifyIndev(candidate, loader.isDevelopmentEnvironment()))
        }

        // Add the current Java version
        candidates.add(verifyIndev(createJavaMod(), loader.isDevelopmentEnvironment()))

        var exception: ModResolutionException? = null

        var timeout = Integer.getInteger(SystemProperties.DEBUG_DISCOVERY_TIMEOUT, 60)
        if (timeout <= 0) timeout = Int.MAX_VALUE

        try {
            pool.shutdown()

            pool.awaitTermination(timeout.toLong(), TimeUnit.SECONDS)

            for (future in futures) {
                if (!future.isDone) {
                    throw TimeoutException()
                }

                try {
                    val candidate = future.get()
                    if (candidate != null) candidates.add(candidate)
                } catch (e: ExecutionException) {
                    exception = gatherExceptions<ModResolutionException>(
                        e,
                        exception
                    ) { exc: Throwable? -> ModResolutionException("Mod discovery failed!", exc) }
                }
            }

            for (data in nestedModInitDatas) {
                for (future in data.futures) {
                    if (!future.isDone) {
                        throw TimeoutException()
                    }

                    try {
                        val candidate = future.get()
                        if (candidate != null) data.target.add(candidate)
                    } catch (e: ExecutionException) {
                        exception = gatherExceptions<ModResolutionException>(
                            e,
                            exception
                        ) { exc: Throwable? -> ModResolutionException("Mod discovery failed!", exc) }
                    }
                }
            }
        } catch (e: TimeoutException) {
            throw FormattedException(
                "Mod discovery took too long!",
                "Analyzing the mod folder contents took longer than %d seconds. This may be caused by unusually slow hardware, pathological antivirus interference or other issues. The timeout can be changed with the system property %s (-D%<s=<desired timeout in seconds>).",
                timeout, SystemProperties.DEBUG_DISCOVERY_TIMEOUT
            )
        } catch (e: InterruptedException) {
            throw FormattedException("Mod discovery interrupted!", e)
        }

        if (exception != null) {
            throw exception
        }

        // get optional set of disabled mod ids
        val disabledModIds = findDisabledModIds()

        // gather all mods (root+nested), initialize parent data
        val ret = Collections.newSetFromMap(IdentityHashMap<ModCandidateImpl, Boolean>(candidates.size * 2))
        val queue: Queue<ModCandidateImpl> = ArrayDeque(candidates)
        var mod: ModCandidateImpl

        while ((queue.poll().also { mod = it }) != null) {
            if (mod.metadata.loadsInEnvironment(envType)) {
                if (disabledModIds.contains(mod.id)) {
                    info(LogCategory.DISCOVERY, "Skipping disabled mod %s", mod.id)
                    continue
                }

                if (!ret.add(mod)) continue

                for (child in mod.nestedMods) {
                    if (child.addParent(mod)) {
                        queue.add(child)
                    }
                }
            } else {
                envDisabledModsOut.computeIfAbsent(mod.id!!) { ignore: String? ->
                    Collections.newSetFromMap(
                        IdentityHashMap()
                    )
                }.add(mod)
            }
        }

        val endTime = System.nanoTime()

        debug(LogCategory.DISCOVERY, "Mod discovery time: %.1f ms", (endTime - startTime) * 1e-6)

        return ArrayList(ret)
    }

    private fun createJavaMod(): ModCandidateImpl {
        val metadata = BuiltinModMetadata.Builder(
            "java",
            System.getProperty("java.specification.version").replaceFirst("^1\\.".toRegex(), "")
        )
            .setName(System.getProperty("java.vm.name"))
            .build()
        val builtinMod = BuiltinMod(listOf(Paths.get(System.getProperty("java.home"))), metadata)

        return createBuiltin(builtinMod, versionOverrides, depOverrides)
    }

    internal inner class ModScanTask private constructor(
        private val paths: List<Path>?,
        localPath: String?,
        private val `is`: RewindableInputStream?,
        private val hash: Long,
        private val requiresRemap: Boolean,
        private val parentPaths: List<String?>
    ) : RecursiveTask<ModCandidateImpl?>() {
        private val localPath = localPath ?: paths?.get(0).toString()

        constructor(paths: List<Path>, requiresRemap: Boolean) : this(
            paths,
            null,
            null,
            -1,
            requiresRemap,
            emptyList<String>()
        )

        override fun compute(): ModCandidateImpl? {
            if (`is` != null) { // nested jar
                try {
                    return computeJarStream()
                } catch (e: ParseMetadataException) { // already contains all context
                    throw wrap(e)
                } catch (t: Throwable) {
                    throw RuntimeException(
                        String.format(
                            "Error analyzing nested jar %s from %s: %s",
                            localPath,
                            parentPaths,
                            t
                        ), t
                    )
                }
            } else { // regular classes-dir or jar
                try {
                    if (paths != null) {
                        for (path in paths) {
                            val candidate = if (Files.isDirectory(path)) {
                                computeDir(path)
                            } else {
                                computeJarFile(path)
                            }

                            if (candidate != null) {
                                return candidate
                            }
                        }
                    }
                } catch (e: ParseMetadataException) { // already contains all context
                    throw wrap(e)
                } catch (t: Throwable) {
                    throw RuntimeException(String.format("Error analyzing %s: %s", paths, t), t)
                }

                return null
            }
        }

        @Throws(IOException::class, ParseMetadataException::class)
        private fun computeDir(path: Path): ModCandidateImpl? {
            val modJson = path.resolve("fabric.mod.json")
            if (!Files.exists(modJson)) return null

            val metadata: LoaderModMetadata

            Files.newInputStream(modJson).use { `is` ->
                metadata = parseMetadata(`is`, path.toString())
            }
            return ModCandidateImpl.createPlain(paths, metadata, requiresRemap, emptyList())
        }

        @Throws(IOException::class, ParseMetadataException::class)
        private fun computeJarFile(path: Path): ModCandidateImpl? {
            ZipFile(path.toFile()).use { zf ->
                val entry = zf.getEntry("fabric.mod.json")
                if (entry == null) {
                    nonFabricMods.add(path)
                    return null
                }

                val metadata: LoaderModMetadata

                zf.getInputStream(entry).use { `is` ->
                    metadata = parseMetadata(`is`, localPath)
                }
                if (!metadata.loadsInEnvironment(envType)) {
                    return ModCandidateImpl.createPlain(paths, metadata, requiresRemap, emptyList())
                }

                val nestedModTasks: List<ModScanTask>

                if (metadata.jars!!.isEmpty()) {
                    nestedModTasks = emptyList()
                } else {
                    val nestedJarPaths: MutableSet<NestedJarEntry?> = HashSet(metadata.jars)

                    nestedModTasks = computeNestedMods(object : ZipEntrySource {
                        @get:Throws(IOException::class)
                        override val nextEntry: ZipEntry?
                            get() {
                                while (jarIt.hasNext()) {
                                    val jar = jarIt.next()
                                    val ret = zf.getEntry(jar!!.file)

                                    if (isValidNestedJarEntry(ret)) {
                                        currentEntry = ret
                                        jarIt.remove()
                                        return ret
                                    }
                                }

                                currentEntry = null
                                return null
                            }

                        @get:Throws(IOException::class)
                        override val inputStream: RewindableInputStream?
                            get() {
                                zf.getInputStream(currentEntry).use { `is` ->
                                    return RewindableInputStream(`is`)
                                }
                            }

                        private val jarIt = nestedJarPaths.iterator()
                        private var currentEntry: ZipEntry? = null
                    })

                    if (!nestedJarPaths.isEmpty() && MoltenexLoaderImpl.INSTANCE!!.isDevelopmentEnvironment()) {
                        warn(
                            LogCategory.METADATA,
                            "Mod %s %s references missing nested jars: %s",
                            metadata.id,
                            metadata.version,
                            nestedJarPaths
                        )
                    }
                }

                val nestedMods: List<ModCandidateImpl>

                if (nestedModTasks.isEmpty()) {
                    nestedMods = emptyList()
                } else {
                    nestedMods = ArrayList()
                    nestedModInitDatas.add(NestedModInitData(nestedModTasks, nestedMods))
                }
                return ModCandidateImpl.createPlain(paths, metadata, requiresRemap, nestedMods)
            }
        }

        @Throws(IOException::class, ParseMetadataException::class)
        private fun computeJarStream(): ModCandidateImpl? {
            var metadata: LoaderModMetadata? = null
            var entry: ZipEntry?

            ZipInputStream(`is`).use { zis ->
                while ((zis.nextEntry.also { entry = it }) != null) {
                    if (entry!!.name == "fabric.mod.json") {
                        metadata = parseMetadata(zis, localPath)
                        break
                    }
                }
            }
            if (metadata == null) return null

            if (!metadata!!.loadsInEnvironment(envType)) {
                return ModCandidateImpl.createNested(localPath, hash, metadata!!, requiresRemap, emptyList())
            }

            val nestedJars = metadata!!.jars
            val nestedModTasks: List<ModScanTask>

            if (nestedJars!!.isEmpty()) {
                nestedModTasks = emptyList()
            } else {
                val nestedJarPaths: MutableSet<String?> = HashSet(nestedJars.size)

                for (nestedJar in nestedJars) {
                    nestedJarPaths.add(nestedJar?.file)
                }

                `is`!!.rewind()

                ZipInputStream(`is`).use { zis ->
                    nestedModTasks = computeNestedMods(object : ZipEntrySource {
                        @get:Throws(IOException::class)
                        override val nextEntry: ZipEntry?
                            get() {
                                if (nestedJarPaths.isEmpty()) return null

                                var ret: ZipEntry

                                while ((zis.nextEntry.also { ret = it }) != null) {
                                    if (isValidNestedJarEntry(ret) && nestedJarPaths.remove(ret.name)) {
                                        inputStream =
                                            RewindableInputStream(zis) // reads the entry, which completes the ZipEntry with any trailing header data
                                        return ret
                                    }
                                }

                                return null
                            }

                        @get:Throws(IOException::class)
                        override var inputStream: RewindableInputStream? = null
                            private set
                    })
                }
                if (!nestedJarPaths.isEmpty() && MoltenexLoaderImpl.INSTANCE!!.isDevelopmentEnvironment()) {
                    warn(
                        LogCategory.METADATA,
                        "Mod %s %s references missing nested jars: %s",
                        metadata!!.id,
                        metadata!!.version,
                        nestedJarPaths
                    )
                }
            }

            val nestedMods: List<ModCandidateImpl>

            if (nestedModTasks.isEmpty()) {
                nestedMods = emptyList()
            } else {
                nestedMods = ArrayList()
                nestedModInitDatas.add(NestedModInitData(nestedModTasks, nestedMods))
            }

            val ret = ModCandidateImpl.createNested(
                localPath, hash,
                metadata!!, requiresRemap, nestedMods
            )
            ret.data = `is`!!.buffer

            return ret
        }

        @Throws(IOException::class)
        private fun computeNestedMods(entrySource: ZipEntrySource): List<ModScanTask> {
            val parentPaths: MutableList<String?> = ArrayList(
                parentPaths.size + 1
            )
            parentPaths.addAll(this.parentPaths)
            parentPaths.add(localPath)

            val tasks: MutableList<ModScanTask> = ArrayList(5)
            var localTask: ModScanTask? = null
            var entry: ZipEntry

            while ((entrySource.nextEntry.also { entry = it!! }) != null) {
                val hash = hash(entry)
                var task = jijDedupMap[hash]

                if (task == null) {
                    task = ModScanTask(null, entry.name, entrySource.inputStream, hash, requiresRemap, parentPaths)
                    val prev = jijDedupMap.putIfAbsent(hash, task)

                    if (prev != null) {
                        task = prev
                    } else if (localTask == null) { // don't fork first task, leave it for this thread
                        localTask = task
                    } else {
                        task.fork()
                    }
                }

                tasks.add(task)
            }

            if (tasks.isEmpty()) return emptyList()

            localTask?.invoke()

            return tasks
        }

        @Throws(ParseMetadataException::class)
        private fun parseMetadata(`is`: InputStream, localPath: String): LoaderModMetadata {
            return parseMetadata(
                `is`,
                localPath,
                parentPaths,
                versionOverrides,
                depOverrides,
                MoltenexLoaderImpl.INSTANCE!!.isDevelopmentEnvironment()
            )
        }
    }

    private interface ZipEntrySource {
        @get:Throws(IOException::class)
        val nextEntry: ZipEntry?

        @get:Throws(IOException::class)
        val inputStream: RewindableInputStream?
    }

    private class RewindableInputStream(parent: InputStream) : InputStream() {
        // no parent.close()
        val buffer: ByteBuffer = readMod(parent)
        private var pos = 0

        init {
            assert(buffer.hasArray() && buffer.arrayOffset() == 0 && buffer.position() == 0)
        }

        fun rewind() {
            pos = 0
        }

        @Throws(IOException::class)
        override fun read(): Int {
            return if (pos >= buffer.limit()) {
                -1
            } else {
                buffer[pos++].toInt() and 0xff
            }
        }

        @Throws(IOException::class)
        override fun read(b: ByteArray, off: Int, len: Int): Int {
            var len = len
            val rem = buffer.limit() - pos

            if (rem <= 0) {
                return -1
            } else {
                len = min(len.toDouble(), rem.toDouble()).toInt()
                System.arraycopy(buffer.array(), pos, b, off, len)
                pos += len

                return len
            }
        }
    }

    private class NestedModInitData(val futures: List<Future<ModCandidateImpl?>>, val target: MutableList<ModCandidateImpl>)
    companion object {
        // retrieve set of disabled mod ids from system property
        private fun findDisabledModIds(): Set<String?> {
            val modIdList = System.getProperty(SystemProperties.DISABLE_MOD_IDS)
                ?: return emptySet<String>()

            val disabledModIds =
                Arrays.stream(modIdList.split(",".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray())
                    .map { obj: String -> obj.trim { it <= ' ' } }
                    .filter { s: String? -> !s!!.isEmpty() }
                    .collect(Collectors.toSet())
            debug(LogCategory.DISCOVERY, "Disabled mod ids: %s", disabledModIds)
            return disabledModIds
        }

        private fun isValidNestedJarEntry(entry: ZipEntry?): Boolean {
            return entry != null && !entry.isDirectory && entry.name.endsWith(".jar")
        }

        @Throws(IOException::class)
        fun readMod(`is`: InputStream): ByteBuffer {
            val available = `is`.available()
            var availableGood = available > 1
            var buffer = ByteArray(if (availableGood) available else 30000)
            var offset = 0
            var len: Int

            while ((`is`.read(buffer, offset, buffer.size - offset).also { len = it }) >= 0) {
                offset += len

                if (offset == buffer.size) {
                    if (availableGood) {
                        val `val` = `is`.read()
                        if (`val` < 0) break

                        availableGood = false
                        buffer = buffer.copyOf(max((buffer.size * 2).toDouble(), 30000.0).toInt())
                        buffer[offset++] = `val`.toByte()
                    } else {
                        buffer = buffer.copyOf(buffer.size * 2)
                    }
                }
            }

            return ByteBuffer.wrap(buffer, 0, offset)
        }
    }
}
