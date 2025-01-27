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
package net.fabricmc.loader.impl.util

import java.io.IOException
import java.io.UncheckedIOException
import java.net.URL
import java.nio.file.Files
import java.nio.file.Path
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap
import java.util.stream.Collectors

object LoaderUtil {
    private val pathNormalizationCache: ConcurrentMap<Path, Path> = ConcurrentHashMap()
    private const val MOLTENEX_LOADER_CLASS = "com/moltenex/loader/api/MoltenexLoader.class"
    private const val ASM_CLASS = "org/objectweb/asm/ClassReader.class"

    @JvmStatic
	fun getClassFileName(className: String?): String {
        return className!!.replace('.', '/') + ".class"
    }

    @JvmStatic
	fun normalizePath(path: Path): Path {
        return if (Files.exists(path)) {
            normalizeExistingPath(path)
        } else {
            path.toAbsolutePath().normalize()
        }
    }

    fun normalizeExistingPath(path: Path): Path {
        return pathNormalizationCache.computeIfAbsent(path) { normalizeExistingPath0(it) }
    }


    private fun normalizeExistingPath0(path: Path): Path {
        try {
            return path.toRealPath()
        } catch (e: IOException) {
            throw UncheckedIOException(e)
        }
    }

    fun verifyNotInTargetCl(cls: Class<*>) {
        check(cls.classLoader.javaClass.name != "net.fabricmc.loader.impl.launch.knot.KnotClassLoader") { "trying to load " + cls.name + " from target class loader" }
    }

    @JvmStatic
	fun verifyClasspath() {
        try {
            var resources: List<URL> = Collections.list(
                LoaderUtil::class.java.classLoader.getResources(
                    MOLTENEX_LOADER_CLASS
                )
            )

            check(resources.size <= 1) {
                "duplicate fabric loader classes found on classpath: " + resources.stream()
                    .map { obj: URL -> obj.toString() }.collect(
                        Collectors.joining(", ")
                    )
            }
            if (resources.isEmpty()) {
                throw AssertionError("$MOLTENEX_LOADER_CLASS not detected on the classpath?! (perhaps it was renamed?)")
            }

            resources = Collections.list(LoaderUtil::class.java.classLoader.getResources(ASM_CLASS))

            check(resources.size <= 1) {
                "duplicate ASM classes found on classpath: " + resources.stream().map { obj: URL -> obj.toString() }
                    .collect(
                        Collectors.joining(", ")
                    )
            }
            check(resources.isNotEmpty()) { "ASM not detected on the classpath (or perhaps $ASM_CLASS was renamed?)" }
        } catch (e: IOException) {
            throw UncheckedIOException("Failed to get resources", e)
        }
    }

    fun hasMacOs(): Boolean {
        return System.getProperty("os.name").lowercase().contains("mac")
    }

    fun hasAwtSupport(): Boolean {
        if (hasMacOs()) {
            // check for JAVA_STARTED_ON_FIRST_THREAD_<pid> which is set if -XstartOnFirstThread is used
            // -XstartOnFirstThread is incompatible with AWT (force enables embedded mode)
            for (key in System.getenv().keys) {
                if (key.startsWith("JAVA_STARTED_ON_FIRST_THREAD_")) return false
            }
        }

        return true
    }
}
