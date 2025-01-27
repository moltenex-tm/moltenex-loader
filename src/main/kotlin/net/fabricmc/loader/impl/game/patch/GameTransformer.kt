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
package net.fabricmc.loader.impl.game.patch

import com.moltenex.loader.impl.launch.MoltenexLauncher
import net.fabricmc.loader.impl.util.ExceptionUtil
import net.fabricmc.loader.impl.util.LoaderUtil
import net.fabricmc.loader.impl.util.SimpleClassPath
import net.fabricmc.loader.impl.util.log.Log
import net.fabricmc.loader.impl.util.log.LogCategory
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.tree.ClassNode
import java.io.IOException
import java.nio.file.Path
import java.util.zip.ZipError

class GameTransformer(vararg patches: GamePatch) {
    private val patches: List<GamePatch> = patches.toList()
    private var patchedClasses: MutableMap<String, ByteArray> = mutableMapOf()
    private var entrypointsLocated = false

    private fun addPatchedClass(node: ClassNode) {
        val key = node.name.replace('/', '.')

        if (patchedClasses.containsKey(key)) {
            throw RuntimeException("Duplicate addPatchedClasses call: $key")
        }

        val writer = ClassWriter(0)
        node.accept(writer)
        patchedClasses[key] = writer.toByteArray()
    }

    fun locateEntrypoints(launcher: MoltenexLauncher, gameJars: List<Path>) {
        if (entrypointsLocated) return

        patchedClasses = mutableMapOf()

        try {
            SimpleClassPath(gameJars).use { cp ->
                val patchedClassNodes = mutableMapOf<String, ClassNode>()

                val classSource: (String?) -> ClassNode? = { name: String? ->
                    patchedClassNodes[name] ?: readClassNode(cp, name)
                }

                patches.forEach { patch ->
                    patch.process(launcher, classSource) { classNode ->
                        if (classNode != null) {
                            patchedClassNodes[classNode.name] = classNode
                        }
                    }
                }

                patchedClassNodes.values.forEach { patchedClassNode ->
                    addPatchedClass(patchedClassNode)
                }
            }
        } catch (e: IOException) {
            throw ExceptionUtil.wrap(e)
        }

        Log.debug(LogCategory.GAME_PATCH, "Patched ${patchedClasses.size} class${if (patchedClasses.size != 1) "es" else ""}")
        entrypointsLocated = true
    }

    private fun readClassNode(classpath: SimpleClassPath, name: String?): ClassNode? {
        val data = patchedClasses[name]

        if (data != null) {
            return readClass(ClassReader(data))
        }

        try {
            val entry =
                classpath.getEntry(LoaderUtil.getClassFileName(name)) ?: return null

            try {
                entry.inputStream.use { `is` ->
                    return readClass(ClassReader(`is`))
                }
            } catch (e: IOException) {
                throw RuntimeException(
                    String.format(
                        "error reading %s in %s: %s",
                        name,
                        LoaderUtil.normalizePath(entry.origin),
                        e
                    ), e
                )
            } catch (e: ZipError) {
                throw RuntimeException(
                    String.format(
                        "error reading %s in %s: %s",
                        name,
                        LoaderUtil.normalizePath(entry.origin),
                        e
                    ), e
                )
            }
        } catch (e: IOException) {
            throw ExceptionUtil.wrap(e)
        }
    }


    /**
     * This must run first, contractually!
     * @param className The class name
     * @return The transformed class data
     */
    fun transform(className: String): ByteArray? {
        return patchedClasses[className]
    }

    private fun readClass(reader: ClassReader?): ClassNode? {
        if (reader == null) return null

        val node = ClassNode()
        reader.accept(node, 0)
        return node
    }
}
