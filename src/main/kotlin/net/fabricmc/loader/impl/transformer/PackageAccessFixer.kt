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
package net.fabricmc.loader.impl.transformer

import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.FieldVisitor
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes

/**
 * Changes package-private and protected access flags to public.
 * In a development environment, Minecraft classes may be mapped into a package structure with invalid access across
 * packages. The class verifier will complain unless we simply change package-private and protected to public.
 */
class PackageAccessFixer(api: Int, classVisitor: ClassVisitor?) :
    ClassVisitor(api, classVisitor) {
    override fun visit(
        version: Int,
        access: Int,
        name: String,
        signature: String,
        superName: String,
        interfaces: Array<String>
    ) {
        super.visit(version, modAccess(access), name, signature, superName, interfaces)
    }

    override fun visitInnerClass(name: String, outerName: String, innerName: String, access: Int) {
        super.visitInnerClass(name, outerName, innerName, modAccess(access))
    }

    override fun visitField(
        access: Int,
        name: String,
        descriptor: String,
        signature: String,
        value: Any
    ): FieldVisitor {
        return super.visitField(modAccess(access), name, descriptor, signature, value)
    }

    override fun visitMethod(
        access: Int,
        name: String,
        descriptor: String,
        signature: String,
        exceptions: Array<String>
    ): MethodVisitor {
        return super.visitMethod(modAccess(access), name, descriptor, signature, exceptions)
    }

    companion object {
        private fun modAccess(access: Int): Int {
            return if ((access and 0x7) != Opcodes.ACC_PRIVATE) {
                (access and (0x7.inv())) or Opcodes.ACC_PUBLIC
            } else {
                access
            }
        }
    }
}
