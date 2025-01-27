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
 * Represents an exception that arises when obtaining entrypoints.
 *
 * @see MoltenexLoader.getEntrypointContainers
 */
class EntrypointException : RuntimeException {
    /**
     * Returns the key of entrypoint in which the exception arose.
     *
     * @return the key
     */
    val key: String

    @Deprecated("For internal use only, to be removed!")
    constructor(key: String, cause: Throwable?) : super(
        "Exception while loading entries for entrypoint '$key'!",
        cause
    ) {
        this.key = key
    }

    @Deprecated("For internal use only, use regular exceptions!")
    constructor(
        key: String,
        causingMod: String,
        cause: Throwable?
    ) : super("Exception while loading entries for entrypoint '$key' provided by '$causingMod'", cause) {
        this.key = key
    }

    @Deprecated("For internal use only, to be removed!")
    constructor(s: String?) : super(s) {
        this.key = ""
    }

    @Deprecated("For internal use only, to be removed!")
    constructor(t: Throwable?) : super(t) {
        this.key = ""
    }
}
