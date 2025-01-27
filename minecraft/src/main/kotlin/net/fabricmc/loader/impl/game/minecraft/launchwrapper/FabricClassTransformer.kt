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
package net.fabricmc.loader.impl.game.minecraft.launchwrapper

import com.moltenex.loader.impl.MoltenexLoaderImpl
import com.moltenex.loader.impl.launch.MoltenexLauncherBase
import net.fabricmc.api.EnvType
import net.fabricmc.loader.impl.transformer.FabricTransformer.transform
import net.minecraft.launchwrapper.IClassTransformer

class FabricClassTransformer : IClassTransformer {
    override fun transform(name: String, transformedName: String, bytes: ByteArray?): ByteArray? {
        val isDevelopment: Boolean = MoltenexLauncherBase.launcher!!.isDevelopment
        val envType: EnvType = MoltenexLauncherBase.launcher!!.environmentType!!

        val input: ByteArray? = MoltenexLoaderImpl.INSTANCE!!.gameProvider.getEntrypointTransformer().transform(name)

        return if (input != null) {
            transform(isDevelopment, envType, name, input)
        } else {
            if (bytes != null) {
                transform(isDevelopment, envType, name, bytes)
            } else {
                null
            }
        }
    }
}
