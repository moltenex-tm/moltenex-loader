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
package com.moltenex.loader.impl.launch.knot

import com.moltenex.loader.impl.launch.MoltenexLauncherBase.Companion.properties
import net.fabricmc.loader.impl.launch.knot.MixinStringPropertyKey
import org.spongepowered.asm.service.IGlobalPropertyService
import org.spongepowered.asm.service.IPropertyKey

class MoltenexGlobalPropertyService : IGlobalPropertyService {
    override fun resolveKey(name: String): IPropertyKey {
        return MixinStringPropertyKey(name)
    }

    private fun keyString(key: IPropertyKey): String {
        return (key as MixinStringPropertyKey).key
    }

    override fun <T> getProperty(key: IPropertyKey): T {
        return properties!![keyString(key)] as T
    }

    override fun setProperty(key: IPropertyKey, value: Any?) {
        val currentProperties = (properties ?: mutableMapOf()).toMutableMap()
        currentProperties[keyString(key)] = value as Any
        properties = currentProperties
    }


    override fun <T> getProperty(key: IPropertyKey, defaultValue: T): T {
        return properties!!.getOrDefault(keyString(key), defaultValue) as T
    }

    override fun getPropertyString(key: IPropertyKey, defaultValue: String): String {
        val o = properties!![keyString(key)]
        return o?.toString() ?: defaultValue
    }
}
