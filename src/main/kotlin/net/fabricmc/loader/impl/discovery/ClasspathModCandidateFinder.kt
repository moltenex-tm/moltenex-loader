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
package net.fabricmc.loader.impl.discovery

import com.moltenex.loader.impl.launch.MoltenexLauncherBase
import net.fabricmc.loader.impl.discovery.ModCandidateFinder.ModCandidateConsumer
import net.fabricmc.loader.impl.util.LoaderUtil.normalizeExistingPath
import net.fabricmc.loader.impl.util.SystemProperties
import net.fabricmc.loader.impl.util.UrlConversionException
import net.fabricmc.loader.impl.util.UrlUtil
import net.fabricmc.loader.impl.util.UrlUtil.getCodeSource
import net.fabricmc.loader.impl.util.log.Log.debug
import net.fabricmc.loader.impl.util.log.LogCategory
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

class ClasspathModCandidateFinder : ModCandidateFinder {
    override fun findCandidates(out: ModCandidateConsumer) {
        if (MoltenexLauncherBase.launcher!!.isDevelopment) {
            val pathGroups =
                pathGroups

            // Search for URLs which point to 'fabric.mod.json' entries, to be considered as mods.
            try {
                val mods = MoltenexLauncherBase.launcher!!.targetClassLoader!!.getResources("fabric.mod.json")

                while (mods.hasMoreElements()) {
                    val url = mods.nextElement()

                    try {
                        val path = normalizeExistingPath(
                            getCodeSource(
                                url,
                                "fabric.mod.json"
                            )
                        ) // code source may not be normalized if from app cl
                        val paths = pathGroups[path]

                        if (paths == null) {
                            out.accept(path, false)
                        } else {
                            out.accept(paths, false)
                        }
                    } catch (e: UrlConversionException) {
                        debug(LogCategory.DISCOVERY, "Error determining location for fabric.mod.json from %s", url, e)
                    }
                }
            } catch (e: IOException) {
                throw RuntimeException(e)
            }
        } else { // production, add loader as a mod
            try {
                out.accept(UrlUtil.LOADER_CODE_SOURCE!!, false)
            } catch (t: Throwable) {
                debug(LogCategory.DISCOVERY, "Could not retrieve launcher code source!", t)
            }
        }
    }

    companion object {
        private val pathGroups: Map<Path, List<Path>>
            /**
             * Parse fabric.classPathGroups system property into a path group lookup map.
             *
             *
             * This transforms `a:b::c:d:e` into `a=[a,b],b=[a,b],c=[c,d,e],d=[c,d,e],e=[c,d,e]`
             */
            get() {
                val prop =
                    System.getProperty(SystemProperties.PATH_GROUPS)
                        ?: return emptyMap()

                val cp: Set<Path?> =
                    HashSet(MoltenexLauncherBase.launcher!!.classPath)
                val ret: MutableMap<Path, List<Path>> =
                    HashMap()

                for (group in prop.split((File.pathSeparator + File.pathSeparator).toRegex())
                    .dropLastWhile { it.isEmpty() }.toTypedArray()) {
                    val paths: MutableSet<Path> = LinkedHashSet()

                    for (path in group.split(File.pathSeparator.toRegex())
                        .dropLastWhile { it.isEmpty() }.toTypedArray()) {
                        if (path.isEmpty()) continue

                        var resolvedPath = Paths.get(path)

                        if (!Files.exists(resolvedPath)) {
                            debug(
                                LogCategory.DISCOVERY,
                                "Skipping missing class path group entry %s",
                                path
                            )
                            continue
                        }

                        resolvedPath = normalizeExistingPath(resolvedPath)

                        if (cp.contains(resolvedPath)) {
                            paths.add(resolvedPath)
                        }
                    }

                    if (paths.size < 2) {
                        debug(
                            LogCategory.DISCOVERY,
                            "Skipping class path group with no effect: %s",
                            group
                        )
                        continue
                    }

                    val pathList: List<Path> =
                        ArrayList(paths)

                    for (path in pathList) {
                        ret[path] = pathList
                    }
                }

                return ret
            }
    }
}
