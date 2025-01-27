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
package net.fabricmc.loader.api

/**
 * Helper class for performing mapping resolution.
 *
 *
 * **Note**: The target namespace (the one being mapped to) for mapping (or the
 * source one for unmapping) is always implied to be the one Loader is
 * currently operating in.
 *
 *
 * All the `className` used in this resolver are in [binary names](https://docs.oracle.com/javase/specs/jls/se8/html/jls-13.html#jls-13.1),
 * such as `"mypackage.MyClass$Inner"`.
 *
 * @since 0.4.1
 */
interface MappingResolver {
    /**
     * Get the list of all available mapping namespaces in the loaded instance.
     *
     * @return The list of all available namespaces.
     */
	val namespaces: Collection<String?>?

    /**
     * Get the current namespace being used at runtime.
     *
     * @return the runtime namespace
     */
	val currentRuntimeNamespace: String?

    /**
     * Map a class name to the mapping currently used at runtime.
     *
     * @param namespace the namespace of the provided class name
     * @param className the provided binary class name
     * @return the mapped class name, or `className` if no such mapping is present
     */
    fun mapClassName(namespace: String?, className: String?): String?

    /**
     * Unmap a class name to the mapping currently used at runtime.
     *
     * @param targetNamespace The target namespace for unmapping.
     * @param className the provided binary class name of the mapping form currently used at runtime
     * @return the mapped class name, or `className` if no such mapping is present
     */
    fun unmapClassName(targetNamespace: String?, className: String?): String?

    /**
     * Map a field name to the mapping currently used at runtime.
     *
     * @param namespace the namespace of the provided field name and descriptor
     * @param owner the binary name of the owner class of the field
     * @param name the name of the field
     * @param descriptor the descriptor of the field
     * @return the mapped field name, or `name` if no such mapping is present
     */
    fun mapFieldName(namespace: String?, owner: String?, name: String?, descriptor: String?): String?

    /**
     * Map a method name to the mapping currently used at runtime.
     *
     * @param namespace the namespace of the provided method name and descriptor
     * @param owner the binary name of the owner class of the method
     * @param name the name of the method
     * @param descriptor the descriptor of the method
     * @return the mapped method name, or `name` if no such mapping is present
     */
    fun mapMethodName(namespace: String?, owner: String?, name: String?, descriptor: String?): String?
}
