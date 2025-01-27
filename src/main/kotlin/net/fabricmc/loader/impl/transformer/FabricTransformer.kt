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

import net.fabricmc.accesswidener.AccessWidenerClassVisitor
import net.fabricmc.api.EnvType
import com.moltenex.loader.impl.MoltenexLoaderImpl
import com.moltenex.loader.impl.launch.MoltenexLauncherBase
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.ClassWriter

object FabricTransformer {
    @JvmStatic
	fun transform(isDevelopment: Boolean, envType: EnvType, name: String, bytes: ByteArray): ByteArray {
        val isMinecraftClass =
            name.startsWith("net.minecraft.") || name.startsWith("com.mojang.blaze3d.") || name.indexOf('.') < 0
        val transformAccess =
            isMinecraftClass && MoltenexLauncherBase.launcher!!.mappingConfiguration!!.requiresPackageAccessHack()
        val environmentStrip = !isMinecraftClass || isDevelopment
        val applyAccessWidener = isMinecraftClass && MoltenexLoaderImpl.INSTANCE?.accessWidener?.targets!!.contains(name)

        if (!transformAccess && !environmentStrip && !applyAccessWidener) {
            return bytes
        }

        val classReader = ClassReader(bytes)
        val classWriter = ClassWriter(classReader, 0)
        var visitor: ClassVisitor = classWriter
        var visitorCount = 0

        if (applyAccessWidener) {
            visitor = AccessWidenerClassVisitor.createClassVisitor(
                MoltenexLoaderImpl.ASM_VERSION,
                visitor,
                MoltenexLoaderImpl.INSTANCE?.accessWidener
            )
            visitorCount++
        }

        if (transformAccess) {
            visitor = PackageAccessFixer(MoltenexLoaderImpl.ASM_VERSION, visitor)
            visitorCount++
        }

        if (environmentStrip) {
            val stripData = EnvironmentStrippingData(MoltenexLoaderImpl.ASM_VERSION, envType.toString())
            classReader.accept(stripData, ClassReader.SKIP_CODE or ClassReader.SKIP_FRAMES)

            if (stripData.stripEntireClass()) {
                throw RuntimeException("Cannot load class $name in environment type $envType")
            }

            if (!stripData.isEmpty) {
                visitor = ClassStripper(
                    MoltenexLoaderImpl.ASM_VERSION,
                    visitor,
                    stripData.getStripInterfaces(),
                    stripData.getStripFields(),
                    stripData.getStripMethods()
                )
                visitorCount++
            }
        }

        if (visitorCount <= 0) {
            return bytes
        }

        classReader.accept(visitor, 0)
        return classWriter.toByteArray()
    }
}
