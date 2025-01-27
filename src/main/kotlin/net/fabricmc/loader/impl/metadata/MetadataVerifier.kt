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

import net.fabricmc.loader.api.SemanticVersion
import net.fabricmc.loader.api.VersionParsingException
import net.fabricmc.loader.impl.discovery.ModCandidateImpl
import net.fabricmc.loader.impl.util.log.Log
import net.fabricmc.loader.impl.util.log.LogCategory

import java.util.regex.Pattern

object MetadataVerifier {
    private val MOD_ID_PATTERN: Pattern = Pattern.compile("[a-z][a-z0-9-_]{1,63}")

    fun verifyIndev(mod: ModCandidateImpl, isDevelopment: Boolean): ModCandidateImpl {
        if (isDevelopment) {
            try {
                verify(mod.metadata, isDevelopment)
            } catch (e: ParseMetadataException) {
                e.setModPaths(mod.localPath!!, emptyList())
                throw RuntimeException("Invalid mod metadata", e)
            }
        }
        return mod
    }

    @Throws(ParseMetadataException::class)
    fun verify(metadata: LoaderModMetadata, isDevelopment: Boolean) {
        checkModId(metadata.id!!, "mod id")

        for (providesDecl in metadata.provides!!) {
            if (providesDecl != null) {
                checkModId(providesDecl, "provides declaration")
            }
        }

        // TODO: Verify mod id and version declarations in dependencies

        if (isDevelopment && metadata.schemaVersion < ModMetadataParser.LATEST_VERSION) {
            Log.warn(
                LogCategory.METADATA,
                "Mod ${metadata.id} uses an outdated schema version: ${metadata.schemaVersion} < ${ModMetadataParser.LATEST_VERSION}"
            )
        }

        if (metadata.version !is SemanticVersion) {
            val version = metadata.version!!.friendlyString
            val exc = try {
                if (version != null) {
                    SemanticVersion.parse(version)
                }
                null
            } catch (e: VersionParsingException) {
                e
            }

            if (exc != null) {
                Log.warn(
                    LogCategory.METADATA,
                    "Mod ${metadata.id} uses the version $version which isn't compatible with Loader's extended semantic version format (${exc.message}). SemVer is recommended for reliably evaluating dependencies and prioritizing newer versions."
                )
            }

            metadata.emitFormatWarnings()
        }
    }

    @Throws(ParseMetadataException::class)
    private fun checkModId(id: String, name: String) {
        if (MOD_ID_PATTERN.matcher(id).matches()) return

        val errorList = mutableListOf<String>()

        // A more useful error list for MOD_ID_PATTERN
        if (id.isEmpty()) {
            errorList.add("is empty!")
        } else {
            when {
                id.length == 1 -> errorList.add("is only a single character! (It must be at least 2 characters long)!")
                id.length > 64 -> errorList.add("has more than 64 characters!")
            }

            val first = id[0]
            if (first !in 'a'..'z') {
                errorList.add("starts with an invalid character '$first' (it must be a lowercase a-z - uppercase isn't allowed anywhere in the ID)")
            }

            val invalidChars = mutableSetOf<Char>()

            for (c in id.drop(1)) {
                if (c != '-' && c != '_' && c !in '0'..'9' && c !in 'a'..'z') {
                    invalidChars.add(c)
                }
            }

            if (invalidChars.isNotEmpty()) {
                val error = buildString {
                    append("contains invalid characters: ")
                    append(invalidChars.joinToString(", ") { "'$it'" })
                    append("!")
                }
                errorList.add(error)
            }
        }

        require(errorList.isNotEmpty())

        val message = buildString {
            append("Invalid $name $id:")
            if (errorList.size == 1) {
                append(" It ${errorList[0]}")
            } else {
                for (error in errorList) {
                    append("\n\t- It $error")
                }
            }
        }

        throw ParseMetadataException(message)
    }
}
