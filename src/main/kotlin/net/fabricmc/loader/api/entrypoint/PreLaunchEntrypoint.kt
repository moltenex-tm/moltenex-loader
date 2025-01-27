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
package net.fabricmc.loader.api.entrypoint

/**
 * Entrypoint getting invoked just before launching the game.
 *
 *
 * **Avoid interfering with the game from this!** Accessing anything needs careful consideration to avoid
 * interfering with its own initialization or otherwise harming its state. It is recommended to implement this interface
 * on its own class to avoid running static initializers too early, e.g. because they were referenced in field or method
 * signatures in the same class.
 *
 *
 * The entrypoint is exposed with `preLaunch` key in the mod json and runs for any environment. It usually
 * executes several seconds before the `main`/`client`/`server` entrypoints.
 *
 * @see net.fabricmc.loader.api.MoltenexLoader.getEntrypointContainers
 */
fun interface PreLaunchEntrypoint {
    /**
     * Runs the entrypoint.
     */
    fun onPreLaunch()
}
