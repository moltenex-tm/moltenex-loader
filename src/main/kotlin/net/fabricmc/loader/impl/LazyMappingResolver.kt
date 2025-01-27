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
import java.util.function.Supplier

class LazyMappingResolver internal constructor(
    private val delegateSupplier: Supplier<MappingResolver>,
    override val currentRuntimeNamespace: String
) :
    MappingResolver {
    private var delegate: MappingResolver? = null
        get() {
            if (field == null) {
                field = delegateSupplier.get()
            }

            return field
        }

    override val namespaces: Collection<String?>?
        get() = delegate!!.namespaces

    override fun mapClassName(namespace: String?, className: String?): String? {
        if (namespace == currentRuntimeNamespace) {
            // Skip loading the mappings if the namespace is the same as the current runtime namespace
            return className
        }

        return delegate!!.mapClassName(namespace, className)
    }

    override fun unmapClassName(targetNamespace: String?, className: String?): String? {
        return delegate!!.unmapClassName(targetNamespace, className)
    }

    override fun mapFieldName(namespace: String?, owner: String?, name: String?, descriptor: String?): String? {
        if (namespace == currentRuntimeNamespace) {
            // Skip loading the mappings if the namespace is the same as the current runtime namespace
            return name
        }

        return delegate!!.mapFieldName(namespace, owner, name, descriptor)
    }

    override fun mapMethodName(namespace: String?, owner: String?, name: String?, descriptor: String?): String? {
        if (namespace == currentRuntimeNamespace) {
            // Skip loading the mappings if the namespace is the same as the current runtime namespace
            return name
        }

        return delegate!!.mapMethodName(namespace, owner, name, descriptor)
    }
}
