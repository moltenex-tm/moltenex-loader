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

import net.fabricmc.api.Environment
import net.fabricmc.api.EnvironmentInterface
import net.fabricmc.api.EnvironmentInterfaces
import org.objectweb.asm.*

/**
 * Scans a class for Environment and EnvironmentInterface annotations to figure out what needs to be stripped.
 */
class EnvironmentStrippingData(api: Int, private val envType: String) : ClassVisitor(api) {
    private var stripEntireClass = false
    private val stripInterfaces: MutableCollection<String> = HashSet()
    private val stripFields: MutableCollection<String> = HashSet()
    private val stripMethods: MutableCollection<String> = HashSet()

    private inner class EnvironmentAnnotationVisitor(api: Int, private val onEnvMismatch: Runnable) :
        AnnotationVisitor(api) {
        override fun visitEnum(name: String, descriptor: String, value: String) {
            if ("value" == name && envType != value) {
                onEnvMismatch.run()
            }
        }
    }

    private inner class EnvironmentInterfaceAnnotationVisitor(api: Int) : AnnotationVisitor(api) {
        private var envMismatch = false
        private var itf: Type? = null

        override fun visitEnum(name: String, descriptor: String, value: String) {
            if ("value" == name && envType != value) {
                envMismatch = true
            }
        }

        override fun visit(name: String, value: Any) {
            if ("itf" == name) {
                itf = value as Type
            }
        }

        override fun visitEnd() {
            if (envMismatch) {
                stripInterfaces.add(itf!!.internalName)
            }
        }
    }

    private fun visitMemberAnnotation(
        descriptor: String,
        visible: Boolean,
        onEnvMismatch: Runnable
    ): AnnotationVisitor? {
        if (ENVIRONMENT_DESCRIPTOR == descriptor) {
            return EnvironmentAnnotationVisitor(api, onEnvMismatch)
        }

        return null
    }

    override fun visitAnnotation(descriptor: String, visible: Boolean): AnnotationVisitor? {
        if (ENVIRONMENT_DESCRIPTOR == descriptor) {
            return EnvironmentAnnotationVisitor(api) { stripEntireClass = true }
        } else if (ENVIRONMENT_INTERFACE_DESCRIPTOR == descriptor) {
            return EnvironmentInterfaceAnnotationVisitor(api)
        } else if (ENVIRONMENT_INTERFACES_DESCRIPTOR == descriptor) {
            return object : AnnotationVisitor(api) {
                override fun visitArray(name: String): AnnotationVisitor? {
                    if ("value" == name) {
                        return object : AnnotationVisitor(api) {
                            override fun visitAnnotation(name: String, descriptor: String): AnnotationVisitor {
                                return EnvironmentInterfaceAnnotationVisitor(api)
                            }
                        }
                    }

                    return null
                }
            }
        }

        return null
    }

    override fun visitField(
        access: Int,
        name: String,
        descriptor: String,
        signature: String,
        value: Any
    ): FieldVisitor {
        return object : FieldVisitor(api) {
            override fun visitAnnotation(annotationDescriptor: String, visible: Boolean): AnnotationVisitor {
                return visitMemberAnnotation(annotationDescriptor, visible) { stripFields.add(name + descriptor) }!!
            }
        }
    }

    override fun visitMethod(
        access: Int,
        name: String,
        descriptor: String,
        signature: String,
        exceptions: Array<String>
    ): MethodVisitor {
        val methodId = name + descriptor
        return object : MethodVisitor(api) {
            override fun visitAnnotation(descriptor: String, visible: Boolean): AnnotationVisitor {
                return visitMemberAnnotation(descriptor, visible) { stripMethods.add(methodId) }!!
            }
        }
    }

    fun stripEntireClass(): Boolean {
        return stripEntireClass
    }

    fun getStripInterfaces(): Collection<String> {
        return stripInterfaces
    }

    fun getStripFields(): Collection<String> {
        return stripFields
    }

    fun getStripMethods(): Collection<String> {
        return stripMethods
    }

    val isEmpty: Boolean
        get() = stripInterfaces.isEmpty() && stripFields.isEmpty() && stripMethods.isEmpty()

    companion object {
        private val ENVIRONMENT_DESCRIPTOR: String = Type.getDescriptor(Environment::class.java)
        private val ENVIRONMENT_INTERFACE_DESCRIPTOR: String = Type.getDescriptor(
            EnvironmentInterface::class.java
        )
        private val ENVIRONMENT_INTERFACES_DESCRIPTOR: String = Type.getDescriptor(
            EnvironmentInterfaces::class.java
        )
    }
}
