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
package net.fabricmc.loader.impl.util.version

import com.moltenex.loader.api.util.version.Version
import net.fabricmc.loader.api.SemanticVersion
import net.fabricmc.loader.api.VersionParsingException


object VersionParser {
    @Throws(VersionParsingException::class)
    fun parse(s: String?, storeX: Boolean): Version {
        if (s == null || s.isEmpty()) {
            throw VersionParsingException("Version must be a non-empty string!")
        }

        var version: Version = try {
            SemanticVersionImpl(s, storeX)
        } catch (e: VersionParsingException) {
            StringVersion(s)
        }

        return version
    }

    @Throws(VersionParsingException::class)
    fun parseSemantic(s: String?): SemanticVersion {
        if (s == null || s.isEmpty()) {
            throw VersionParsingException("Version must be a non-empty string!")
        }

        return SemanticVersionImpl(s, false)
    }
}