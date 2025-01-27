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
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.AbstractInsnNode
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.FieldNode
import org.objectweb.asm.tree.MethodNode
import java.util.function.Consumer
import java.util.function.Predicate
import java.util.stream.Collectors

abstract class GamePatch {
    protected fun findField(node: ClassNode, predicate: Predicate<FieldNode?>?): FieldNode? {
        return node.fields.stream().filter(predicate).findAny().orElse(null)
    }

    protected fun findFields(node: ClassNode, predicate: Predicate<FieldNode?>?): List<FieldNode> {
        return node.fields.stream().filter(predicate).collect(Collectors.toList())
    }

    protected fun findMethod(node: ClassNode, predicate: Predicate<MethodNode?>?): MethodNode? {
        return node.methods.stream().filter(predicate).findAny().orElse(null)
    }

    protected fun findInsn(
        node: MethodNode,
        predicate: Predicate<AbstractInsnNode>,
        last: Boolean
    ): AbstractInsnNode? {
        if (last) {
            for (i in node.instructions.size() - 1 downTo 0) {
                val insn = node.instructions[i]

                if (predicate.test(insn)) {
                    return insn
                }
            }
        } else {
            for (i in 0..<node.instructions.size()) {
                val insn = node.instructions[i]

                if (predicate.test(insn)) {
                    return insn
                }
            }
        }

        return null
    }

    protected fun moveAfter(it: ListIterator<AbstractInsnNode>, opcode: Int) {
        while (it.hasNext()) {
            val node = it.next()

            if (node.opcode == opcode) {
                break
            }
        }
    }

    protected fun moveBefore(it: ListIterator<AbstractInsnNode>, opcode: Int) {
        moveAfter(it, opcode)
        it.previous()
    }

    protected fun moveAfter(it: ListIterator<AbstractInsnNode>, targetNode: AbstractInsnNode) {
        while (it.hasNext()) {
            val node = it.next()

            if (node === targetNode) {
                break
            }
        }
    }

    protected fun moveBefore(it: ListIterator<AbstractInsnNode>, targetNode: AbstractInsnNode) {
        moveAfter(it, targetNode)
        it.previous()
    }

    protected fun moveBeforeType(it: ListIterator<AbstractInsnNode>, nodeType: Int) {
        while (it.hasPrevious()) {
            val node = it.previous()

            if (node.type == nodeType) {
                break
            }
        }
    }

    protected fun isStatic(access: Int): Boolean {
        return ((access and Opcodes.ACC_STATIC) != 0)
    }

    protected fun isPublicStatic(access: Int): Boolean {
        return ((access and 0x0F) == (Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC))
    }

    protected fun isPublicInstance(access: Int): Boolean {
        return ((access and 0x0F) == (Opcodes.ACC_PUBLIC or 0 /* non-static */))
    }

    abstract fun process(
        launcher: MoltenexLauncher?,
        classSource: (String?) -> ClassNode?,
        classEmitter: Consumer<ClassNode?>?
    )
}
