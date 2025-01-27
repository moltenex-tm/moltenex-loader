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

import com.moltenex.loader.api.util.version.Version
import net.fabricmc.loader.api.metadata.ModDependency
import net.fabricmc.loader.impl.game.GameProvider.BuiltinMod
import net.fabricmc.loader.impl.metadata.AbstractModMetadata
import net.fabricmc.loader.impl.metadata.DependencyOverrides
import net.fabricmc.loader.impl.metadata.LoaderModMetadata
import net.fabricmc.loader.impl.metadata.VersionOverrides
import java.io.ByteArrayInputStream
import java.io.IOException
import java.lang.ref.SoftReference
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.regex.Pattern
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream

class ModCandidateImpl private constructor(
    val originPaths: List<Path>?,
    val localPath: String,
    val hash: Long,
    val metadata: LoaderModMetadata,
    val requiresRemap: Boolean,
    val nestedMods: MutableCollection<ModCandidateImpl>
) :
    DomainObject.Mod {
    private var dataRef: SoftReference<ByteBuffer?>? = null
    var paths: List<Path>? = null
        get() = field ?: throw Exception("no path set")
        set(value) {
            if (value == null) throw NullPointerException("null paths")
            field = value
            clearCachedData()
         }
    val parentMods: MutableList<ModCandidateImpl>
    var minNestLevel: Int
    init {
        paths = originPaths
        parentMods = if (paths == null) ArrayList() else emptyList<ModCandidateImpl>().toMutableList()
        minNestLevel = if (paths != null) 0 else Int.MAX_VALUE
    }
    fun hasPath(): Boolean {
        return paths != null
    }

    override val id: String?
        get() = metadata.id

    override val version: Version?
        get() = metadata.version

    val provides: Collection<String?>?
        get() = metadata.provides

    val isBuiltin: Boolean
        get() = metadata.type == AbstractModMetadata.TYPE_BUILTIN

    val loadCondition: ModLoadCondition
        get() = if (minNestLevel == 0) ModLoadCondition.ALWAYS else ModLoadCondition.IF_POSSIBLE

    val dependencies: Collection<ModDependency?>
        get() = metadata.dependencies

    fun getParentMods(): MutableCollection<ModCandidateImpl> {
        return parentMods
    }

    fun addParent(parent: ModCandidateImpl): Boolean {
        if (minNestLevel == 0) return false
        if (parentMods.contains(parent)) return false

        parentMods.add(parent)
        updateMinNestLevel(parent)

        return true
    }

    fun resetMinNestLevel(): Boolean {
        if (minNestLevel > 0) {
            minNestLevel = Int.MAX_VALUE
            return true
        } else {
            return false
        }
    }

    fun updateMinNestLevel(parent: ModCandidateImpl): Boolean {
        if (minNestLevel <= parent.minNestLevel) return false

        this.minNestLevel = parent.minNestLevel + 1

        return true
    }

    val isRoot: Boolean
        get() = minNestLevel == 0

    fun clearCachedData() {
        this.dataRef = null
    }

    @Throws(IOException::class)
    fun copyToDir(outputDir: Path, temp: Boolean): Path {
        Files.createDirectories(outputDir)
        var ret: Path? = null

        try {
            if (temp) {
                ret = Files.createTempFile(outputDir, id, ".jar")
            } else {
                ret = outputDir.resolve(defaultFileName)

                if (Files.exists(ret)) {
                    if (Files.size(ret) == getSize(hash)) {
                        return ret
                    } else {
                        Files.deleteIfExists(ret)
                    }
                }
            }

            copyToFile(ret)
        } catch (t: Throwable) {
            if (ret != null) Files.deleteIfExists(ret)

            throw t
        }

        return ret
    }

    val defaultFileName: String
        get() {
            var ret = String.format(
                "%s-%s-%s.jar",
                id,
                FILE_NAME_SANITIZING_PATTERN.matcher(version!!.friendlyString).replaceAll("_"),
                java.lang.Long.toHexString(mixHash(hash))
            )

            if (ret.length > 64) {
                ret = ret.substring(0, 32) + ret.substring(ret.length - 32)
            }

            return ret
        }

    @Throws(IOException::class)
    private fun copyToFile(out: Path) {
        val dataRef = this.dataRef

        if (dataRef != null) {
            val data = dataRef.get()

            if (data != null) {
                Files.copy(
                    ByteArrayInputStream(
                        data.array(),
                        data.arrayOffset() + data.position(),
                        data.arrayOffset() + data.limit()
                    ), out, StandardCopyOption.REPLACE_EXISTING
                )
                return
            }
        }

        if (paths != null) {
            if (paths!!.size != 1) throw UnsupportedOperationException("multiple paths for $this")

            Files.copy(paths!![0], out)

            return
        }

        val parent = bestSourcingParent

        if (parent!!.paths != null) {
            if (parent.paths!!.size != 1) throw UnsupportedOperationException("multiple parent paths for $this")

            ZipFile(parent.paths!![0].toFile()).use { zf ->
                val entry = zf.getEntry(localPath)
                    ?: throw IOException(
                        String.format(
                            "can't find nested mod %s in its parent mod %s",
                            this,
                            parent
                        )
                    )
                Files.copy(zf.getInputStream(entry), out)
            }
        } else {
            val data = parent.data!!

            ZipInputStream(
                ByteArrayInputStream(
                    data.array(),
                    data.arrayOffset() + data.position(),
                    data.arrayOffset() + data.limit()
                )
            ).use { zis ->
                var entry: ZipEntry? = null
                while ((zis.nextEntry.also { entry = it }) != null) {
                    if (entry!!.name == localPath) {
                        Files.copy(zis, out)
                        return
                    }
                }
            }
            throw IOException(String.format("can't find nested mod %s in its parent mod %s", this, parent))
        }
    }

    @get:Throws(IOException::class)
    var data: ByteBuffer?
        get() {
            val dataRef = this.dataRef

            if (dataRef != null) {
                val ret = dataRef.get()
                if (ret != null) return ret
            }

            var ret: ByteBuffer?

            if (paths != null) {
                if (paths!!.size != 1) throw UnsupportedOperationException("multiple paths for $this")

                ret = ByteBuffer.wrap(Files.readAllBytes(paths!![0]))
            } else {
                val parent = bestSourcingParent

                if (parent!!.paths != null) {
                    if (parent.paths!!.size != 1) throw UnsupportedOperationException("multiple parent paths for $this")

                    ZipFile(parent.paths!![0].toFile()).use { zf ->
                        val entry = zf.getEntry(localPath)
                            ?: throw IOException(
                                String.format(
                                    "can't find nested mod %s in its parent mod %s",
                                    this,
                                    parent
                                )
                            )
                        ret = ModDiscoverer.readMod(zf.getInputStream(entry))
                    }
                } else {
                    val data: ByteBuffer = parent.data!!
                    ret = null

                    ZipInputStream(
                        ByteArrayInputStream(
                            data.array(),
                            data.arrayOffset() + data.position(),
                            data.arrayOffset() + data.limit()
                        )
                    ).use { zis ->
                        var entry: ZipEntry? = null
                        while ((zis.nextEntry.also { entry = it }) != null) {
                            if (entry!!.name == localPath) {
                                ret = ModDiscoverer.readMod(zis)
                                break
                            }
                        }
                    }
                    if (ret == null) throw IOException(
                        String.format(
                            "can't find nested mod %s in its parent mods %s",
                            this,
                            parent
                        )
                    )
                }
            }

            this.dataRef = SoftReference(ret)

            return ret
        }
        set(data) {
            this.dataRef = SoftReference(data)
        }

    private val bestSourcingParent: ModCandidateImpl?
        get() {
            if (parentMods.isEmpty()) return null

            var ret: ModCandidateImpl? = null

            for (parent in parentMods) {
                if (parent.minNestLevel >= minNestLevel) continue

                if (parent.paths != null && parent.paths!!.size == 1
                    || parent.dataRef != null && parent.dataRef!!.get() != null
                ) {
                    return parent
                }

                if (ret == null) ret = parent
            }

            checkNotNull(ret) { "invalid nesting?" }

            return ret
        }

    override fun toString(): String {
        return String.format("%s %s", id, version)
    }

    companion object {
        val ID_VERSION_COMPARATOR: Comparator<ModCandidateImpl> =
            Comparator { a, b ->
                val cmp = a.id!!.compareTo(b.id!!)
                if (cmp != 0) cmp else a.version!!.compareTo(b.version)
            }

		fun createBuiltin(
            mod: BuiltinMod,
            versionOverrides: VersionOverrides,
            depOverrides: DependencyOverrides
        ): ModCandidateImpl {
            val metadata: LoaderModMetadata = BuiltinMetadataWrapper(mod.metadata)
            versionOverrides.apply(metadata)
            depOverrides.apply(metadata)
            return ModCandidateImpl(mod.paths, null.toString(), -1, metadata, false, emptyList<ModCandidateImpl>().toMutableList())
        }

        fun createPlain(
            paths: List<Path>?,
            metadata: LoaderModMetadata,
            requiresRemap: Boolean,
            nestedMods: Collection<ModCandidateImpl>
        ): ModCandidateImpl {
            return ModCandidateImpl(paths!!, null.toString(), -1, metadata, requiresRemap, nestedMods.toMutableList())
        }

        fun createNested(
            localPath: String?,
            hash: Long,
            metadata: LoaderModMetadata,
            requiresRemap: Boolean,
            nestedMods: Collection<ModCandidateImpl>
        ): ModCandidateImpl {
            return ModCandidateImpl(emptyList(), localPath!!, hash, metadata, requiresRemap, nestedMods.toMutableList())
        }

        @JvmStatic
		fun hash(entry: ZipEntry): Long {
            require(!(entry.size < 0 || entry.crc < 0)) { "uninitialized entry: $entry" }

            return entry.crc shl 32 or entry.size
        }

        private fun getSize(hash: Long): Long {
            return hash and 0xffffffffL
        }

        private fun mixHash(hash: Long): Long {
            var hash = hash
            hash = hash xor (hash ushr 33)
            hash *= -0xae502812aa7333L
            hash = hash xor (hash ushr 33)
            hash *= -0x3b314601e57a13adL
            hash = hash xor (hash ushr 33)

            return hash
        }

        private val FILE_NAME_SANITIZING_PATTERN: Pattern = Pattern.compile("[^\\w\\.\\-\\+]+")
    }
}
