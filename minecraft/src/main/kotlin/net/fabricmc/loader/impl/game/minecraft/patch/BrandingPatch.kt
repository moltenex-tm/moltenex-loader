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
import net.fabricmc.loader.impl.game.minecraft.Hooks
import net.fabricmc.loader.impl.game.patch.GamePatch
import net.fabricmc.loader.impl.util.log.Log
import net.fabricmc.loader.impl.util.log.LogCategory
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.AbstractInsnNode
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.MethodInsnNode
import java.util.function.Consumer
import java.util.function.Function

class BrandingPatch : GamePatch() {
    override fun process(
        launcher: MoltenexLauncher?,
        classSource: (String?) -> ClassNode?,
        classEmitter: Consumer<ClassNode?>?
    ) {
        arrayOf(
            "net.minecraft.client.ClientBrandRetriever",
            "net.minecraft.server.MinecraftServer"
        ).mapNotNull { brandClassName ->
            classSource(brandClassName)?.takeIf { applyBrandingPatch(it) }
        }.forEach { patchedClassNode ->
            classEmitter?.accept(patchedClassNode)
        }
    }


    private fun applyBrandingPatch(classNode: ClassNode): Boolean {
        var applied = false

        for (node in classNode.methods) {
            if (node.name.equals("getClientModName") || node.name.equals("getServerModName") && node.desc.endsWith(")Ljava/lang/String;")) {
                Log.debug(LogCategory.GAME_PATCH, "Applying brand name hook to %s::%s", classNode.name, node.name)

                val it: MutableListIterator<AbstractInsnNode> = node.instructions.iterator()

                while (it.hasNext()) {
                    if (it.next().opcode == Opcodes.ARETURN) {
                        it.previous()
                        it.add(
                            MethodInsnNode(
                                Opcodes.INVOKESTATIC,
                                Hooks.INTERNAL_NAME,
                                "insertBranding",
                                "(Ljava/lang/String;)Ljava/lang/String;",
                                false
                            )
                        )
                        it.next()
                    }
                }

                applied = true
            }
        }

        return applied
    }
}
