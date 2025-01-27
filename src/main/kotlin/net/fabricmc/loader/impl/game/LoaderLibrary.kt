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
package net.fabricmc.loader.impl.game

import net.fabricmc.accesswidener.AccessWidener
import net.fabricmc.api.EnvType
import net.fabricmc.loader.impl.util.UrlConversionException
import net.fabricmc.loader.impl.util.UrlUtil
import net.fabricmc.mappingio.tree.MappingTree
import net.fabricmc.tinyremapper.TinyRemapper
import org.objectweb.asm.ClassReader
import org.objectweb.asm.commons.Remapper
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.analysis.Analyzer
import org.objectweb.asm.util.CheckClassAdapter
import org.sat4j.pb.SolverFactory
import org.sat4j.specs.ContradictionException
import org.spongepowered.asm.launch.MixinBootstrap
import java.nio.file.Path

internal enum class LoaderLibrary {
    FABRIC_LOADER(UrlUtil.LOADER_CODE_SOURCE!!),
    MAPPING_IO(MappingTree::class.java),
    SPONGE_MIXIN(MixinBootstrap::class.java),
    TINY_REMAPPER(TinyRemapper::class.java),
    ACCESS_WIDENER(AccessWidener::class.java),
    ASM(ClassReader::class.java),
    ASM_ANALYSIS(Analyzer::class.java),
    ASM_COMMONS(Remapper::class.java),
    ASM_TREE(ClassNode::class.java),
    ASM_UTIL(CheckClassAdapter::class.java),
    SAT4J_CORE(ContradictionException::class.java),
    SAT4J_PB(SolverFactory::class.java),
    SERVER_LAUNCH(
        "fabric-server-launch.properties",
        EnvType.SERVER
    ),  // installer generated jar to run setup loader's class path
    SERVER_LAUNCHER(
        "net/fabricmc/installer/ServerLauncher.class",
        EnvType.SERVER
    ),  // installer based launch-through method
    JUNIT_API("org/junit/jupiter/api/Test.class", null),
    JUNIT_PLATFORM_ENGINE("org/junit/platform/engine/TestEngine.class", null),
    JUNIT_PLATFORM_LAUNCHER("org/junit/platform/launcher/core/LauncherFactory.class", null),
    JUNIT_JUPITER("org/junit/jupiter/engine/JupiterTestEngine.class", null),
    FABRIC_LOADER_JUNIT("net/fabricmc/loader/impl/junit/FabricLoaderLauncherSessionListener.class", null),

    // Logging libraries are only loaded from the platform CL when running as a unit test.
    LOG4J_API("org/apache/logging/log4j/LogManager.class", true),
    LOG4J_CORE("META-INF/services/org.apache.logging.log4j.spi.Provider", true),
    LOG4J_CONFIG("log4j2.xml", true),
    LOG4J_PLUGIN_3("net/minecrell/terminalconsole/util/LoggerNamePatternSelector.class", true),
    SLF4J_API("org/slf4j/Logger.class", true);

    @JvmField
	val path: Path?
    val env: EnvType?
    val junitRunOnly: Boolean

    constructor(cls: Class<*>) : this(UrlUtil.getCodeSource(cls)!!)

    constructor(path: Path) {
        this.path = path
        this.env = null
        this.junitRunOnly = false
    }

    constructor(file: String?, env: EnvType?, junitRunOnly: Boolean = false) {
        val url = LoaderLibrary::class.java.classLoader.getResource(file)

        try {
            this.path = if (url != null) UrlUtil.getCodeSource(url, file!!) else null
            this.env = env
        } catch (e: UrlConversionException) {
            throw RuntimeException(e)
        }

        this.junitRunOnly = junitRunOnly
    }

    constructor(path: String?, loggerLibrary: Boolean) : this(path, null, loggerLibrary)

    fun isApplicable(env: EnvType, junitRun: Boolean): Boolean {
        return (this.env == null || this.env == env)
                && (!junitRunOnly || junitRun)
    }
}
