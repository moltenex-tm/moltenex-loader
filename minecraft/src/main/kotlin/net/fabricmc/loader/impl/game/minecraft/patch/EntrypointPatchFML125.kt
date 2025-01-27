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
package net.fabricmc.loader.impl.game.minecraft.patch

import com.moltenex.loader.impl.launch.MoltenexLauncher
import net.fabricmc.loader.impl.game.patch.GamePatch
import net.fabricmc.loader.impl.launch.knot.Knot
import net.fabricmc.loader.impl.util.LoaderUtil.getClassFileName
import net.fabricmc.loader.impl.util.log.Log.debug
import net.fabricmc.loader.impl.util.log.LogCategory
import org.objectweb.asm.ClassReader
import org.objectweb.asm.commons.ClassRemapper
import org.objectweb.asm.commons.Remapper
import org.objectweb.asm.tree.ClassNode
import java.io.IOException
import java.util.function.Consumer
import java.util.function.Function

class EntrypointPatchFML125 : GamePatch() {
    override fun process(
        launcher: MoltenexLauncher?,
        classSource: (String?) -> ClassNode?,
        classEmitter: Consumer<ClassNode?>?
    ) {
        // Check if the class is present and FMLRelauncher is missing
        val modClassLoaderClass = classSource(TO)
        val fmlRelauncherClass = classSource("cpw.mods.fml.relauncher.FMLRelauncher")

        if (modClassLoaderClass != null && fmlRelauncherClass == null) {
            // Ensure we are dealing with the Knot launcher
            if (launcher !is Knot) {
                throw RuntimeException("1.2.5 FML patch only supported on Knot!")
            }

            debug(LogCategory.GAME_PATCH, "Detected 1.2.5 FML - Knotifying ModClassLoader...")

            // Create a class node to store the patched class
            val patchedClassLoader = ClassNode()

            // Try loading the class from the launcher resources
            try {
                launcher.getResourceAsStream(getClassFileName(FROM)).use { stream ->
                    stream?.let {
                        val patchedClassLoaderReader = ClassReader(it)
                        patchedClassLoaderReader.accept(patchedClassLoader, 0)
                    } ?: throw IOException("Could not find class $FROM in the launcher classpath while transforming ModClassLoader")
                }
            } catch (e: IOException) {
                throw RuntimeException("An error occurred while reading class $FROM while transforming ModClassLoader", e)
            }

            // Remap the class
            val remappedClassLoader = ClassNode()
            patchedClassLoader.accept(ClassRemapper(remappedClassLoader, object : Remapper() {
                override fun map(internalName: String): String {
                    return if (FROM_INTERNAL == internalName) TO_INTERNAL else internalName
                }
            }))

            // Emit the remapped class
            classEmitter!!.accept(remappedClassLoader)
        }
    }

    companion object {
        // Class details for remapping
        private val FROM: String = ModClassLoader_125_FML::class.java.name
        private const val TO = "cpw.mods.fml.common.ModClassLoader"
        private val FROM_INTERNAL = FROM.replace('.', '/')
        private const val TO_INTERNAL = "cpw/mods/fml/common/ModClassLoader"
    }
}
