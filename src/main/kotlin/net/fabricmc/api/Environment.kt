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
package net.fabricmc.api


/**
 * Applied to declare that the annotated element is present only in the specified environment.
 *
 * Use with caution, as Fabric-loader will remove the annotated element in a mismatched environment!
 *
 * When the annotated element is removed, bytecode associated with the element will not be removed.
 * For example, if a field is removed, its initializer code will not, and will cause an error on execution.
 *
 * If an overriding method has this annotation and its overridden method doesn't,
 * unexpected behavior may happen. If an overridden method has this annotation
 * while the overriding method doesn't, it is safe, but the method can be used from
 * the overridden class only in the specified environment.
 *
 * @see EnvironmentInterface
 */
@Retention(AnnotationRetention.BINARY)
@Target(
    AnnotationTarget.CLASS,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY_GETTER,
    AnnotationTarget.PROPERTY_SETTER,
    AnnotationTarget.FIELD,
    AnnotationTarget.CONSTRUCTOR,
    AnnotationTarget.FILE
)
@MustBeDocumented
annotation class Environment(
    /**
     * Returns the environment type that the annotated element is only present in.
     */
    val value: EnvType
)
