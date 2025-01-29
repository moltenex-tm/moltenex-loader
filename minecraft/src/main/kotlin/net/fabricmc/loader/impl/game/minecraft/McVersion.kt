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
package net.fabricmc.loader.impl.game.minecraft

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import net.fabricmc.loader.impl.game.minecraft.McVersionLookup.getRelease
import net.fabricmc.loader.impl.game.minecraft.McVersionLookup.normalizeVersion
import java.util.*

class McVersion private constructor(
    /**
     * The id from version.json, if available.
     */
    val id: String?,
    /**
     * The name from version.json, if available.
     */
    val name: String?,
    /**
     * The raw version, such as `18w21a`.
     *
     *
     * This is derived from the version.json's id and name fields if available, otherwise through other sources.
     */
    val raw: String, release: String?, val classVersion: OptionalInt
) {
    /**
     * The normalized version.
     *
     *
     * This is usually compliant with Semver and
     * contains release and pre-release information.
     */
    val normalized: String = normalizeVersion(raw, release)

    override fun toString(): String {
        return String.format(
            "McVersion{id=%s, name=%s, raw=%s, normalized=%s, classVersion=%s}",
            id, name, raw, normalized, classVersion
        )
    }

    companion object {
        fun fromJson(json: JsonObject, builder: Builder): Boolean {
            // Extracting fields from JSON object
            val id = json["id"]?.jsonPrimitive?.contentOrNull
            val name = json["name"]?.jsonPrimitive?.contentOrNull
            val release = json["release_target"]?.jsonPrimitive?.contentOrNull

            // Handling version assignment logic
            val version = if (name == null || (id != null && id.length < name.length)) {
                id
            } else {
                name
            }

            // If no valid version, return false
            if (version == null) return false

            // Populate the builder with extracted values
            builder.setId(id)
            builder.setName(name)

            if (release == null) {
                // If release is null, set version in both name and release
                builder.setNameAndRelease(version)
            } else {
                // Set version and release separately
                builder.setVersion(version)
                builder.setRelease(release)
            }

            return true
        }
    }

    class Builder {
        private var id: String? = null // id as in version.json
        private var name: String? = null // name as in version.json
        private var version: String? = null // derived from version.json's id and name or other sources
        private var release: String? = null // mc release (major.minor)
        private var classVersion: OptionalInt = OptionalInt.empty()

        // Setters
        fun setId(id: String?): Builder {
            this.id = id
            return this
        }

        fun setName(name: String?): Builder {
            this.name = name
            return this
        }

        fun setVersion(name: String?): Builder {
            this.version = name
            return this
        }

        fun setRelease(release: String?): Builder {
            this.release = release
            return this
        }

        fun setClassVersion(classVersion: Int): Builder {
            this.classVersion = OptionalInt.of(classVersion)
            return this
        }

        // Complex setters
        fun setNameAndRelease(name: String): Builder {
            return setVersion(name)
                .setRelease(getRelease(name))
        }

        fun setFromFileName(name: String): Builder {
            // strip extension
            var name = name
            val pos = name.lastIndexOf('.')
            if (pos > 0) name = name.substring(0, pos)

            return setNameAndRelease(name)
        }

        fun build(): McVersion {
            return McVersion(
                this.id, this.name,
                version!!, this.release, this.classVersion
            )
        }
    }
}
