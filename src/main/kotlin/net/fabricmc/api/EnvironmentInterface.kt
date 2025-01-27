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

import kotlin.reflect.KClass

/**
 * Applied to declare that a class implements an interface only in the specified environment.
 *
 *
 * Use with caution, as Fabric-loader will remove the interface from `implements` declaration
 * of the class in a mismatched environment!
 *
 *
 * Implemented methods are not removed. To remove implemented methods, use [Environment].
 *
 * @see Environment
 */
@Retention(AnnotationRetention.BINARY)
@JvmRepeatable(EnvironmentInterfaces::class)
@Target(AnnotationTarget.CLASS)
@MustBeDocumented
annotation class EnvironmentInterface(
    /**
     * Returns the environment type that the specific interface is only implemented in.
     */
    val value: EnvType,
    /**
     * Returns the interface class.
     */
    val itf: KClass<*>
)
