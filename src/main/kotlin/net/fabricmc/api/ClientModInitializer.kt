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
 * A mod initializer ran only on [EnvType.CLIENT].
 *
 *
 * This entrypoint is suitable for setting up client-specific logic, such as rendering
 * or integrated server tweaks.
 *
 *
 * In `fabric.mod.json`, the entrypoint is defined with `client` key.
 *
 * @see ModInitializer
 *
 * @see DedicatedServerModInitializer
 *
 * @see moltenex.loader.api.MoltenexLoader.getEntrypointContainers
 */
fun interface ClientModInitializer {
    /**
     * Runs the mod initializer on the client environment.
     */
    fun onInitializeClient()
}
