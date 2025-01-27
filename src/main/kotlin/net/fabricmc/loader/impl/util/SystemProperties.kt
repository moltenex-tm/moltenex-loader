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
package net.fabricmc.loader.impl.util

object SystemProperties {
    // whether fabric loader is running in a development environment / mode, affects class path mod discovery, remapping, logging, ...
    const val DEVELOPMENT: String = "fabric.development"
    const val SIDE: String = "fabric.side"

    // skips the embedded MC game provider, letting ServiceLoader-provided ones take over
    const val SKIP_MC_PROVIDER: String = "fabric.skipMcProvider"

    // game jar paths for common/client/server, replaces lookup from class path if present, env specific takes precedence
    const val GAME_JAR_PATH: String = "fabric.gameJarPath"
    const val GAME_JAR_PATH_CLIENT: String = "fabric.gameJarPath.client"
    const val GAME_JAR_PATH_SERVER: String = "fabric.gameJarPath.server"

    // set the game version for the builtin game mod/dependencies, bypassing auto-detection
    const val GAME_VERSION: String = "fabric.gameVersion"

    // fallback log file for the builtin log handler (dumped on exit if not replaced with another handler)
    const val LOG_FILE: String = "fabric.log.file"

    // minimum log level for builtin log handler
    const val LOG_LEVEL: String = "fabric.log.level"

    // a path to a directory to replace the default mod search directory
    const val MODS_FOLDER: String = "fabric.modsFolder"

    // additional mods to load (path separator separated paths, @ prefix for meta-file with each line referencing an actual file)
    const val ADD_MODS: String = "fabric.addMods"

    // a comma-separated list of mod ids to disable, even if they're discovered. mostly useful for unit testing.
    const val DISABLE_MOD_IDS: String = "fabric.debug.disableModIds"

    // file containing the class path for in-dev runtime mod remapping
    const val REMAP_CLASSPATH_FILE: String = "fabric.remapClasspathFile"

    // class path groups to map multiple class path entries to a mod (paths separated by path separator, groups by double path separator)
    const val PATH_GROUPS: String = "fabric.classPathGroups"

    // enable the fixing of package access errors in the game jar(s)
    const val FIX_PACKAGE_ACCESS: String = "fabric.fixPackageAccess"

    // system level libraries, matching code sources will not be assumed to be part of the game or mods and remain on the system class path (paths separated by path separator)
    const val SYSTEM_LIBRARIES: String = "fabric.systemLibraries"

    // throw exceptions from entrypoints, discovery etc. directly instead of gathering and attaching as suppressed
    const val DEBUG_THROW_DIRECTLY: String = "fabric.debug.throwDirectly"

    // logs library classification activity
    const val DEBUG_LOG_LIB_CLASSIFICATION: String = "fabric.debug.logLibClassification"

    // logs class loading
    const val DEBUG_LOG_CLASS_LOAD: String = "fabric.debug.logClassLoad"

    // logs class loading errors to uncover caught exceptions without adequate logging
    const val DEBUG_LOG_CLASS_LOAD_ERRORS: String = "fabric.debug.logClassLoadErrors"

    // logs class transformation errors to uncover caught exceptions without adequate logging
    const val DEBUG_LOG_TRANSFORM_ERRORS: String = "fabric.debug.logTransformErrors"

    // disables system class path isolation, allowing bogus lib accesses (too early, transient jars)
    const val DEBUG_DISABLE_CLASS_PATH_ISOLATION: String = "fabric.debug.disableClassPathIsolation"

    // disables mod load order shuffling to be the same in-dev as in production
    const val DEBUG_DISABLE_MOD_SHUFFLE: String = "fabric.debug.disableModShuffle"

    // workaround for bad load order dependencies
    const val DEBUG_LOAD_LATE: String = "fabric.debug.loadLate"

    // override the mod discovery timeout, unit in seconds, <= 0 to disable
    const val DEBUG_DISCOVERY_TIMEOUT: String = "fabric.debug.discoveryTimeout"

    // override the mod resolution timeout, unit in seconds, <= 0 to disable
    const val DEBUG_RESOLUTION_TIMEOUT: String = "fabric.debug.resolutionTimeout"

    // replace mod versions (modA:versionA,modB:versionB,...)
    const val DEBUG_REPLACE_VERSION: String = "fabric.debug.replaceVersion"

    // deobfuscate the game jar with the classpath
    const val DEBUG_DEOBFUSCATE_WITH_CLASSPATH: String = "fabric.debug.deobfuscateWithClasspath"

    // whether fabric loader is running in a unit test, this affects logging classpath setup
    const val UNIT_TEST: String = "fabric.unitTest"
}
