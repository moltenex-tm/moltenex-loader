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
package com.moltenex.loader.impl.launch

import com.moltenex.loader.impl.launch.MoltenexLauncherBase.Companion.launcher
import net.fabricmc.api.EnvType
import net.fabricmc.loader.api.SemanticVersion
import net.fabricmc.loader.api.SemanticVersion.Companion.parse
import net.fabricmc.loader.api.VersionParsingException
import net.fabricmc.loader.api.metadata.ModDependency
import com.moltenex.loader.api.util.version.VersionInterval
import net.fabricmc.loader.impl.ModContainerImpl
import com.moltenex.loader.impl.MoltenexLoaderImpl
import net.fabricmc.loader.impl.launch.knot.MixinServiceKnot
import net.fabricmc.loader.impl.launch.knot.MixinServiceKnotBootstrap
import net.fabricmc.loader.impl.util.log.Log.error
import net.fabricmc.loader.impl.util.log.Log.info
import net.fabricmc.loader.impl.util.log.LogCategory
import net.fabricmc.loader.impl.util.mappings.MixinIntermediaryDevRemapper
import org.spongepowered.asm.launch.MixinBootstrap
import org.spongepowered.asm.mixin.FabricUtil
import org.spongepowered.asm.mixin.MixinEnvironment
import org.spongepowered.asm.mixin.Mixins
import org.spongepowered.asm.mixin.extensibility.IMixinConfig

object MoltenexMixinBootstrap {
    private var initialized = false

    @JvmStatic
	fun init(side: EnvType?, loader: MoltenexLoaderImpl) {
        if (initialized) {
            throw RuntimeException("FabricMixinBootstrap has already been initialized!")
        }

        System.setProperty("mixin.bootstrapService", MixinServiceKnotBootstrap::class.java.name)
        System.setProperty("mixin.service", MixinServiceKnot::class.java.name)

        MixinBootstrap.init()

        if (launcher!!.isDevelopment) {
            val mappingConfiguration = launcher!!.mappingConfiguration
            val mappings = mappingConfiguration!!.getMappings()

            if (mappings != null) {
                val namespaces: MutableList<String?> = ArrayList(mappings.dstNamespaces)
                namespaces.add(mappings.srcNamespace)

                if (namespaces.contains("intermediary") && namespaces.contains(mappingConfiguration.targetNamespace)) {
                    System.setProperty("mixin.env.remapRefMap", "true")

                    try {
                        val remapper =
                            MixinIntermediaryDevRemapper(mappings, "intermediary", mappingConfiguration.targetNamespace)
                        MixinEnvironment.getDefaultEnvironment().remappers.add(remapper)
                        info(LogCategory.MIXIN, "Loaded Fabric development mappings for mixin remapper!")
                    } catch (e: Exception) {
                        error(
                            LogCategory.MIXIN,
                            "Fabric development environment setup error - the game will probably crash soon!"
                        )
                        e.printStackTrace()
                    }
                }
            }
        }

        val configToModMap: MutableMap<String?, ModContainerImpl> = HashMap()

        for (mod in loader.modsInternal) {
            for (config in mod.metadata.getMixinConfigs(side)!!) {
                val prev = configToModMap.putIfAbsent(config, mod)
                if (prev != null) throw RuntimeException(
                    java.lang.String.format(
                        "Non-unique Mixin config name %s used by the mods %s and %s",
                        config,
                        prev.metadata.id,
                        mod.metadata.id
                    )
                )

                try {
                    Mixins.addConfiguration(config)
                } catch (t: Throwable) {
                    throw RuntimeException(
                        java.lang.String.format(
                            "Error parsing or using Mixin config %s for mod %s",
                            config,
                            mod.metadata.id
                        ), t
                    )
                }
            }
        }

        for (config in Mixins.getConfigs()) {
            val mod = configToModMap[config.name] ?: continue
        }

        try {
            IMixinConfig::class.java.getMethod("decorate", String::class.java, Any::class.java)
            MixinConfigDecorator.apply(configToModMap)
        } catch (e: NoSuchMethodException) {
            info(LogCategory.MIXIN, "Detected old Mixin version without config decoration support")
        }

        initialized = true
    }

    private object MixinConfigDecorator {
        private val versions: MutableList<LoaderMixinVersionEntry> = ArrayList()

        init {
            // maximum loader version and bundled fabric mixin version, DESCENDING ORDER, LATEST FIRST
            // loader versions with new mixin versions need to be added here

            addVersion("0.16.0", FabricUtil.COMPATIBILITY_0_14_0)
            addVersion("0.12.0-", FabricUtil.COMPATIBILITY_0_10_0)
        }

        fun apply(configToModMap: Map<String?, ModContainerImpl>) {
            for (rawConfig in Mixins.getConfigs()) {
                val mod = configToModMap[rawConfig.name] ?: continue

                val config = rawConfig.config
                config.decorate(FabricUtil.KEY_MOD_ID, mod.metadata.id)
                config.decorate(FabricUtil.KEY_COMPATIBILITY, getMixinCompat(mod))
            }
        }

        fun getMixinCompat(mod: ModContainerImpl): Int {
            // infer from loader dependency by determining the least relevant loader version the mod accepts
            // AND any loader deps

            var reqIntervals: List<VersionInterval> = listOf(VersionInterval.INFINITE)

            for (dep in mod.metadata.dependencies) {
                if (dep != null) {
                    if (dep.modId.equals("fabricloader") || dep.modId.equals("fabric-loader")) {
                        if (dep.kind === ModDependency.Kind.DEPENDS) {
                            reqIntervals = VersionInterval.and(reqIntervals, dep.getVersionIntervals())
                        } else if (dep.kind === ModDependency.Kind.BREAKS) {
                            reqIntervals = VersionInterval.and(reqIntervals, VersionInterval.not(dep.getVersionIntervals()))
                        }
                    }
                }
            }

            check(!reqIntervals.isEmpty()) { "mod $mod is incompatible with every loader version?" }

            val minLoaderVersion = reqIntervals[0]!!.min // it is sorted, to 0 has the absolute lower bound

            if (minLoaderVersion != null) { // has a lower bound
                for (version in versions) {
                    if (minLoaderVersion >= version.loaderVersion) { // lower bound is >= current version
                        return version.mixinVersion
                    }
                }
            }

            return FabricUtil.COMPATIBILITY_0_9_2
        }

        fun addVersion(minLoaderVersion: String, mixinCompat: Int) {
            try {
                versions.add(LoaderMixinVersionEntry(parse(minLoaderVersion), mixinCompat))
            } catch (e: VersionParsingException) {
                throw RuntimeException(e)
            }
        }

        private class LoaderMixinVersionEntry(val loaderVersion: SemanticVersion, val mixinVersion: Int)
    }
}
