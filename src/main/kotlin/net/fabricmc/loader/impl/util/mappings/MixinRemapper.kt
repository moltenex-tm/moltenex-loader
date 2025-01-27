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
package net.fabricmc.loader.impl.util.mappings

import net.fabricmc.mappingio.tree.MappingTree
import org.spongepowered.asm.mixin.extensibility.IRemapper

open class MixinRemapper(protected val mappings: MappingTree, protected val fromId: Int, protected val toId: Int) :
    IRemapper {
    override fun mapMethodName(owner: String, name: String, desc: String): String? {
        val method = mappings.getMethod(owner, name, desc, fromId)
        return if (method == null) name else method.getName(toId)
    }

    override fun mapFieldName(owner: String, name: String, desc: String): String? {
        val field = mappings.getField(owner, name, desc, fromId)
        return if (field == null) name else field.getName(toId)
    }

    override fun map(typeName: String): String {
        return mappings.mapClassName(typeName, fromId, toId)
    }

    override fun unmap(typeName: String): String {
        return mappings.mapClassName(typeName, toId, fromId)
    }

    override fun mapDesc(desc: String): String {
        return mappings.mapDesc(desc, fromId, toId)
    }

    override fun unmapDesc(desc: String): String {
        return mappings.mapDesc(desc, toId, fromId)
    }
}
