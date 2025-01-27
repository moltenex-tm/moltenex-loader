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
package net.fabricmc.loader.impl.launch.knot

import com.moltenex.loader.impl.launch.MoltenexLauncherBase.Companion.launcher
import net.fabricmc.loader.impl.util.UrlUtil
import org.objectweb.asm.ClassReader
import org.objectweb.asm.tree.ClassNode
import org.spongepowered.asm.launch.platform.container.ContainerHandleURI
import org.spongepowered.asm.launch.platform.container.IContainerHandle
import org.spongepowered.asm.logging.ILogger
import org.spongepowered.asm.mixin.MixinEnvironment
import org.spongepowered.asm.mixin.transformer.IMixinTransformer
import org.spongepowered.asm.mixin.transformer.IMixinTransformerFactory
import org.spongepowered.asm.service.*
import org.spongepowered.asm.util.ReEntranceLock
import java.io.IOException
import java.io.InputStream
import java.net.URL

class MixinServiceKnot : IMixinService, IClassProvider, IClassBytecodeProvider, ITransformerProvider, IClassTracker {
    private val lock = ReEntranceLock(1)

    @Throws(IOException::class)
    fun getClassBytes(name: String?, transformedName: String?): ByteArray? {
        return launcher!!.getClassByteArray(name, true)
    }

    @Throws(ClassNotFoundException::class, IOException::class)
    fun getClassBytes(name: String?, runTransformers: Boolean): ByteArray {
        val classBytes = launcher!!.getClassByteArray(name, runTransformers)

        if (classBytes != null) {
            return classBytes
        } else {
            throw ClassNotFoundException(name)
        }
    }

    @Throws(ClassNotFoundException::class, IOException::class)
    override fun getClassNode(name: String): ClassNode {
        return getClassNode(name, true)
    }

    @Throws(ClassNotFoundException::class, IOException::class)
    override fun getClassNode(name: String, runTransformers: Boolean): ClassNode {
        return getClassNode(name, runTransformers, 0)
    }

    @Throws(ClassNotFoundException::class, IOException::class)
    override fun getClassNode(name: String, runTransformers: Boolean, readerFlags: Int): ClassNode {
        val reader = ClassReader(getClassBytes(name, runTransformers))
        val node = ClassNode()
        reader.accept(node, readerFlags)
        return node
    }

    override fun getClassPath(): Array<URL?> {
        // Mixin 0.7.x only uses getClassPath() to find itself; we implement CodeSource correctly,
        // so this is unnecessary.
        return arrayOfNulls(0)
    }

    @Throws(ClassNotFoundException::class)
    override fun findClass(name: String): Class<*> {
        return launcher!!.targetClassLoader!!.loadClass(name)
    }

    @Throws(ClassNotFoundException::class)
    override fun findClass(name: String, initialize: Boolean): Class<*> {
        return Class.forName(name, initialize, launcher!!.targetClassLoader)
    }

    @Throws(ClassNotFoundException::class)
    override fun findAgentClass(name: String, initialize: Boolean): Class<*> {
        return Class.forName(name, initialize, Knot::class.java.classLoader)
    }

    override fun getName(): String {
        return if (launcher is Knot) "Knot/Fabric" else "Launchwrapper/Fabric"
    }

    override fun isValid(): Boolean {
        return true
    }

    override fun prepare() {}

    override fun getInitialPhase(): MixinEnvironment.Phase {
        return MixinEnvironment.Phase.PREINIT
    }

    override fun offer(internal: IMixinInternal) {
        if (internal is IMixinTransformerFactory) {
            transformer = internal.createTransformer()
        }
    }

    override fun init() {
    }

    override fun beginPhase() {}

    override fun checkEnv(bootSource: Any) {}

    override fun getReEntranceLock(): ReEntranceLock {
        return lock
    }

    override fun getClassProvider(): IClassProvider {
        return this
    }

    override fun getBytecodeProvider(): IClassBytecodeProvider {
        return this
    }

    override fun getTransformerProvider(): ITransformerProvider {
        return this
    }

    override fun getClassTracker(): IClassTracker {
        return this
    }

    override fun getAuditTrail(): IMixinAuditTrail? {
        return null
    }

    override fun getPlatformAgents(): Collection<String> {
        return listOf("org.spongepowered.asm.launch.platform.MixinPlatformAgentDefault")
    }

    override fun getPrimaryContainer(): IContainerHandle {
        return ContainerHandleURI(UrlUtil.LOADER_CODE_SOURCE!!.toUri())
    }

    override fun getMixinContainers(): Collection<IContainerHandle> {
        return emptyList()
    }

    override fun getResourceAsStream(name: String): InputStream? {
        return launcher!!.getResourceAsStream(name)
    }

    override fun registerInvalidClass(className: String) {}

    override fun isClassLoaded(className: String): Boolean {
        return launcher!!.isClassLoaded(className)
    }

    override fun getClassRestrictions(className: String): String {
        return ""
    }

    override fun getTransformers(): Collection<ITransformer> {
        return emptyList()
    }

    override fun getDelegatedTransformers(): Collection<ITransformer> {
        return emptyList()
    }

    override fun addTransformerExclusion(name: String) {}

    override fun getSideName(): String {
        return launcher!!.environmentType!!.name
    }

    override fun getMinCompatibilityLevel(): MixinEnvironment.CompatibilityLevel {
        return MixinEnvironment.CompatibilityLevel.JAVA_8
    }

    override fun getMaxCompatibilityLevel(): MixinEnvironment.CompatibilityLevel {
        return MixinEnvironment.CompatibilityLevel.JAVA_22
    }

    override fun getLogger(name: String): ILogger {
        return MixinLogger.get(name)
    }

    companion object {
        @JvmField
		var transformer: IMixinTransformer? = null
    }
}
