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

import net.fabricmc.mappingio.MappedElementKind
import net.fabricmc.mappingio.MappingVisitor
import net.fabricmc.mappingio.adapter.ForwardingMappingVisitor
import java.io.IOException

// Filter out all method arguments, local variable names and comments
class FilteringMappingVisitor(next: MappingVisitor) : ForwardingMappingVisitor(next) {
    @Throws(IOException::class)
    override fun visitMethodArg(argPosition: Int, lvIndex: Int, srcName: String?): Boolean {
        // ignored
        return false
    }

    @Throws(IOException::class)
    override fun visitMethodVar(
        lvtRowIndex: Int,
        lvIndex: Int,
        startOpIdx: Int,
        endOpIdx: Int,
        srcName: String?
    ): Boolean {
        // ignored
        return false
    }

    @Throws(IOException::class)
    override fun visitComment(targetKind: MappedElementKind, comment: String) {
        // ignored
    }
}
