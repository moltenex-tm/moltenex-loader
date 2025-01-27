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
package net.fabricmc.loader.impl.metadata

import net.fabricmc.loader.api.metadata.ModOrigin
import java.io.File
import java.nio.file.Path
import java.util.stream.Collectors

class ModOriginImpl : ModOrigin {
    override val kind: ModOrigin.Kind
    override var paths: List<Path>? = null
        get() {
            if (kind != ModOrigin.Kind.PATH) throw UnsupportedOperationException("kind " + kind.name + " doesn't have paths")

            return field
        }
    override var parentSubLocation: String? = null
        get() {
            if (kind != ModOrigin.Kind.NESTED) throw UnsupportedOperationException("kind " + kind.name + " doesn't have a parent sub-location")

            return field
        }

    constructor() {
        this.kind = ModOrigin.Kind.UNKNOWN
    }

    override var parentModId: String? = null
        get() = if (kind != ModOrigin.Kind.NESTED) {
            throw UnsupportedOperationException("kind " + kind.name + " doesn't have a parent mod")
        } else {
            field
        }


    constructor(paths: List<Path>?) {
        this.kind = ModOrigin.Kind.PATH
        this.paths = paths
    }

    constructor(parentModId: String?, parentSubLocation: String?) {
        this.kind = ModOrigin.Kind.NESTED
        this.parentModId = parentModId
        this.parentSubLocation = parentSubLocation
    }

    override fun toString(): String {
        return when (kind) {
            ModOrigin.Kind.PATH -> paths!!.stream()
                .map { obj: Path -> obj.toString() }
                .collect(Collectors.joining(File.pathSeparator))

            ModOrigin.Kind.NESTED -> String.format(
                "%s:%s",
                parentModId,
                parentSubLocation
            )

            else -> "unknown"
        }
    }
}
