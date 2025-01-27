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

import com.moltenex.loader.api.MoltenexLoader
import com.moltenex.loader.impl.launch.MoltenexLauncher
import net.fabricmc.api.EnvType
import net.fabricmc.loader.impl.game.patch.GamePatch
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.*
import java.util.function.Consumer
import java.util.function.Function

/**
 * Patch the TinyFileDialogs.tinyfd_openFileDialog call to use a trusted string in MoreOptionsDialog.
 *
 *
 * This patch applies to Minecraft versions 20w21a -> 23w04a inclusive
 */
class TinyFDPatch : GamePatch() {
    override fun process(
        launcher: MoltenexLauncher?,
        classSource: (String?) -> ClassNode?,
        classEmitter: Consumer<ClassNode?>?
    ) {
        // Check for null launcher and ensure it is of type CLIENT
        if (launcher?.environmentType != EnvType.CLIENT) {
            return // Fix should only be applied to clients
        }

        var className = MORE_OPTIONS_DIALOG_CLASS_NAME

        // Only remap the classname when needed to prevent loading the mappings when not required in prod
        if (launcher.mappingConfiguration?.targetNamespace != "intermediary" &&
            MoltenexLoader.instance.mappingResolver?.namespaces?.contains("intermediary") == true) {
            // Ensure mapping resolver is not null before attempting to map the class name
            val mappedClassName = MoltenexLoader.instance.mappingResolver?.mapClassName(
                "intermediary", MORE_OPTIONS_DIALOG_CLASS_NAME
            )
            className = mappedClassName.toString()
        }

        // Ensure classSource is not null and retrieve the classNode
        val classNode = classSource(className)

        // Proceed with patching the classNode if it's not null
        classNode?.let {
            patchMoreOptionsDialog(it)
            classEmitter?.accept(it)
        }
    }

    private fun patchMoreOptionsDialog(classNode: ClassNode) {
        for (method in classNode.methods) {
            val iterator = findTargetMethodNode(method) ?: continue

            while (iterator.hasPrevious()) {
                val insnNode = iterator.previous()

                // Find the Text.getString() instruction
                // or find the TranslatableText.getString() instruction present in older versions (e.g 1.16.5)
                if (insnNode.opcode == Opcodes.INVOKEINTERFACE
                    || insnNode.opcode == Opcodes.INVOKEVIRTUAL
                ) {
                    val insnList = InsnList()
                    // Drop the possibly malicious value
                    insnList.add(InsnNode(Opcodes.POP))
                    // And replace it with something we trust
                    insnList.add(LdcInsnNode(DIALOG_TITLE))

                    method.instructions.insert(insnNode, insnList)
                    return
                }
            }

            throw IllegalStateException("Failed to patch MoreOptionsDialog")
        }

        // At this point we failed to find a valid target method.
        // 20w20a and 20w20b have the class but do not use tinyfd
    }

    private fun findTargetMethodNode(methodNode: MethodNode): ListIterator<AbstractInsnNode>? {
        if ((methodNode.access and Opcodes.ACC_SYNTHETIC) == 0) {
            // We know it's in a synthetic method
            return null
        }

        // Visit all the instructions until we find the TinyFileDialogs.tinyfd_openFileDialog call
        val iterator: ListIterator<AbstractInsnNode> = methodNode.instructions.iterator()

        while (iterator.hasNext()) {
            val instruction = iterator.next()

            if (instruction.opcode != Opcodes.INVOKESTATIC) {
                continue
            }

            if (instruction !is MethodInsnNode) {
                continue
            }

            if (instruction.name == TINYFD_METHOD_NAME) {
                return iterator
            }
        }

        return null
    }

    companion object {
        private const val MORE_OPTIONS_DIALOG_CLASS_NAME = "net.minecraft.class_5292"
        private const val TINYFD_METHOD_NAME = "tinyfd_openFileDialog"

        // This is the en_us value of selectWorld.import_worldgen_settings.select_file
        private const val DIALOG_TITLE = "Select settings file (.json)"
    }
}
