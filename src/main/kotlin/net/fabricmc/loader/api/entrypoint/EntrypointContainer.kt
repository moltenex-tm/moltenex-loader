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

import net.fabricmc.loader.api.ModContainer

/**
 * A container holding both an entrypoint instance and the [ModContainer] which has provided the entrypoint.
 *
 * @param <T> The type of the entrypoint
 * @see net.fabricmc.loader.api.MoltenexLoader.getEntrypointContainers
</T> */
interface EntrypointContainer<T> {
    /**
     * Returns the entrypoint instance. It will be constructed the first time you call this method.
     */
    val entrypoint: T

    /**
     * Returns the mod that provided this entrypoint.
     */
    val provider: ModContainer?

    val definition: String
        /**
         * Returns a string representation of the entrypoint's definition.
         */
        get() = ""
}
