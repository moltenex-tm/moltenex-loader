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
package com.moltenex.loader.impl.metadata.fabric.v1

import kotlinx.serialization.Serializable
import net.fabricmc.loader.api.metadata.*
import net.fabricmc.loader.impl.metadata.EntrypointMetadata
import net.fabricmc.loader.impl.metadata.NestedJarEntry
import net.fabricmc.loader.impl.metadata.V1ModMetadata

@Serializable
data class ModMetadata (
    val id: String,
    val version: String,
    val provides: List<String> = emptyList(),
    val environment: ModEnvironment = ModEnvironment.UNIVERSAL,
    val entrypoints: Map<String, List<EntrypointMetadata>> = emptyMap(),
    val jars: List<NestedJarEntry> = emptyList(),
    val mixins: List<V1ModMetadata.MixinEntry> = emptyList(),
    val accessWidener: String? = null,
    val dependencies: List<ModDependency> = emptyList(),
    val hasRequires: Boolean = false,
    val name: String? = null,
    val description: String? = null,
    val authors: List<Person> = emptyList(),
    val contributors: List<Person> = emptyList(),
    val contact: ContactInformation? = null,
    val license: List<String> = emptyList(),
    val icon: V1ModMetadata.IconEntry? = null,
    val languageAdapters: Map<String, String> = emptyMap(),
    val customValues: Map<String, CustomValue> = emptyMap()
)