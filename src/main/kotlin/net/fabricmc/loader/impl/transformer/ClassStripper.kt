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

/**
 * Strips the specified interfaces, fields and methods from a class.
 */
class ClassStripper(
    api: Int,
    classVisitor: ClassVisitor?,
    private val stripInterfaces: Collection<String>,
    private val stripFields: Collection<String>,
    private val stripMethods: Collection<String>
) :
    ClassVisitor(api, classVisitor) {
    override fun visit(
        version: Int,
        access: Int,
        name: String,
        signature: String,
        superName: String,
        interfaces: Array<String>
    ) {
        var interfaces = interfaces
        if (!stripInterfaces.isEmpty()) {
            val interfacesList: MutableList<String> = ArrayList()

            for (itf in interfaces) {
                if (!stripInterfaces.contains(itf)) {
                    interfacesList.add(itf)
                }
            }

            interfaces = interfacesList.toTypedArray<String>()
        }

        super.visit(version, access, name, signature, superName, interfaces)
    }

    override fun visitField(
        access: Int,
        name: String,
        descriptor: String,
        signature: String,
        value: Any
    ): FieldVisitor? {
        if (stripFields.contains(name + descriptor)) return null
        return super.visitField(access, name, descriptor, signature, value)
    }

    override fun visitMethod(
        access: Int,
        name: String,
        descriptor: String,
        signature: String,
        exceptions: Array<String>
    ): MethodVisitor? {
        if (stripMethods.contains(name + descriptor)) return null
        return super.visitMethod(access, name, descriptor, signature, exceptions)
    }
}
