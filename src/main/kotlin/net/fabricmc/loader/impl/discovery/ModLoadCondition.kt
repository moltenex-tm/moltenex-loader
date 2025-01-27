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
package net.fabricmc.loader.impl.discovery

/**
 * Conditions for whether to load a mod.
 *
 *
 * These apply only after the unique ID and nesting requirements are met. If a mod ID is shared by multiple mod
 * candidates only one of them will load. Mods nested within another mod only load if the encompassing mod loads.
 *
 *
 * Each condition encompasses all conditions after it in enum declaration order. For example [.IF_POSSIBLE]
 * also loads if the conditions for [.IF_RECOMMENDED] or [.IF_NEEDED] are met.
 */
enum class ModLoadCondition {
    /**
     * Load always, triggering an error if that is not possible.
     *
     *
     * This is the default for root mods (typically those in the mods folder).
     */
    ALWAYS,

    /**
     * Load whenever there is nothing contradicting.
     *
     *
     * This is the default for nested mods.
     */
    IF_POSSIBLE,

    /**
     * Load if the mod is recommended by another loading mod.
     */
    IF_RECOMMENDED,

    /**
     * Load if another loading mod dependencyHandler the mod.
     */
    IF_NEEDED
}
