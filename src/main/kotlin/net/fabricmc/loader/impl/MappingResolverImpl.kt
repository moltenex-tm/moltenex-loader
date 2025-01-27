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
package net.fabricmc.loader.impl

import net.fabricmc.loader.api.MappingResolver
import net.fabricmc.mappingio.tree.MappingTree
import java.util.*

internal class MappingResolverImpl(private val mappings: MappingTree, override val currentRuntimeNamespace: String) :
    MappingResolver {
    private val targetNamespaceId = mappings.getNamespaceId(currentRuntimeNamespace)

    override val namespaces: Collection<String?>
        get() {
            val namespaces = HashSet(mappings.dstNamespaces)
            namespaces.add(mappings.srcNamespace)
            return Collections.unmodifiableSet(namespaces)
        }

    override fun mapClassName(namespace: String?, className: String?): String {
        if (className != null) {
            require(className.indexOf('/') < 0) { "Class names must be provided in dot format: $className" }
        }

        return replaceSlashesWithDots(
            mappings.mapClassName(
                className?.let { replaceDotsWithSlashes(it) },
                mappings.getNamespaceId(namespace),
                targetNamespaceId
            )
        )
    }

    override fun unmapClassName(targetNamespace: String?, className: String?): String {
        if (className != null) {
            require(className.indexOf('/') < 0) { "Class names must be provided in dot format: $className" }
        }

        return replaceSlashesWithDots(
            mappings.mapClassName(
                className?.let { replaceDotsWithSlashes(it) },
                targetNamespaceId,
                mappings.getNamespaceId(targetNamespace)
            )
        )
    }

    override fun mapFieldName(namespace: String?, owner: String?, name: String?, descriptor: String?): String? {
        if (owner != null) {
            require(owner.indexOf('/') < 0) { "Class names must be provided in dot format: $owner" }
        }

        val field =
            mappings.getField(owner?.let { replaceDotsWithSlashes(it) }, name, descriptor, mappings.getNamespaceId(namespace))
        return if (field == null) name else field.getName(targetNamespaceId)
    }

    override fun mapMethodName(namespace: String?, owner: String?, name: String?, descriptor: String?): String? {
        if (owner != null) {
            require(owner.indexOf('/') < 0) { "Class names must be provided in dot format: $owner" }
        }

        val method =
            mappings.getMethod(owner?.let { replaceDotsWithSlashes(it) }, name, descriptor, mappings.getNamespaceId(namespace))
        return if (method == null) name else method.getName(targetNamespaceId)
    }

    companion object {
        private fun replaceSlashesWithDots(cname: String): String {
            return cname.replace('/', '.')
        }

        private fun replaceDotsWithSlashes(cname: String): String {
            return cname.replace('.', '/')
        }
    }
}
