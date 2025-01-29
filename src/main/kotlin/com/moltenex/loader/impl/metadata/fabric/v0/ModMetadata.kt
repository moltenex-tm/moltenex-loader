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
package com.moltenex.loader.impl.metadata.fabric.v0

import com.moltenex.loader.impl.metadata.fabric.common.ModDependencyImpl
import kotlinx.serialization.Serializable
import net.fabricmc.loader.api.metadata.ContactInformation
import net.fabricmc.loader.api.metadata.ModEnvironment
import net.fabricmc.loader.api.metadata.Person

@Serializable
data class ModMetadata(
    val id: String,
    val version: String,
    val dependencies: List<ModDependencyImpl> = emptyList(),
    val mixins: Mixins? = null,
    val environment: ModEnvironment = ModEnvironment.UNIVERSAL,
    val initializer: String? = null,
    val initializers: List<String> = emptyList(),
    val name: String? = null,
    val description: String? = null,
    val authors: List<Person> = emptyList(),
    val contributors: List<Person> = emptyList(),
    val links: ContactInformation = ContactInformation.EMPTY,
    val license: String? = null
)