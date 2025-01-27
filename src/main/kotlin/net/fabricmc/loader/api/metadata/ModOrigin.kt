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
package net.fabricmc.loader.api.metadata

import java.nio.file.Path

/**
 * Representation of the various locations a mod was loaded from originally.
 *
 *
 * This location is not necessarily identical to the code source used at runtime, a mod may get copied or otherwise
 * transformed before being put on the class path. It thus mostly represents the installation and initial loading, not
 * what is being directly accessed at runtime.
 */
interface ModOrigin {
    /**
     * Get the kind of this origin, determines the available methods.
     *
     * @return mod origin kind
     */
    val kind: Kind?

    /**
     * Get the jar or folder paths for a [Kind.PATH] origin.
     *
     * @return jar or folder paths
     * @throws UnsupportedOperationException for incompatible kinds
     */
    val paths: List<Path?>?

    /**
     * Get the parent mod for a [Kind.NESTED] origin.
     *
     * @return parent mod
     * @throws UnsupportedOperationException for incompatible kinds
     */
    var parentModId: String?

    /**
     * Get the sub-location within the parent mod for a [Kind.NESTED] origin.
     *
     * @return sub-location
     * @throws UnsupportedOperationException for incompatible kinds
     */
    val parentSubLocation: String?

    /**
     * Non-exhaustive list of possible [ModOrigin] kinds.
     *
     *
     * New kinds may be added in the future, use a default switch case!
     */
    enum class Kind {
        PATH, NESTED, UNKNOWN
    }
}
