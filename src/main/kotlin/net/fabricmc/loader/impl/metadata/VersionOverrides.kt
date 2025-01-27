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

import com.moltenex.loader.api.util.version.Version
import net.fabricmc.loader.api.VersionParsingException
import net.fabricmc.loader.impl.util.SystemProperties
import net.fabricmc.loader.impl.util.version.VersionParser.parse
import kotlin.system.exitProcess

class VersionOverrides {
    private val replacements: MutableMap<String, Version> = HashMap()

    init {
        val property = System.getProperty(SystemProperties.DEBUG_REPLACE_VERSION) ?: exitProcess(1)

        for (entry in property.split(",".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()) {
            val pos = entry.indexOf(":")
            if (pos <= 0 || pos >= entry.length - 1) throw RuntimeException("invalid version replacement entry: $entry")

            val id = entry.substring(0, pos)
            val rawVersion = entry.substring(pos + 1)
            val version: Version

            try {
                version = parse(rawVersion, false)
            } catch (e: VersionParsingException) {
                throw RuntimeException(String.format("Invalid replacement version for mod %s: %s", id, rawVersion), e)
            }

            replacements[id] = version
        }
    }

    fun apply(metadata: LoaderModMetadata) {
        if (replacements.isEmpty()) return

        val replacement = replacements[metadata.id]

        if (replacement != null) {
            metadata.version = replacement
        }
    }

    val affectedModIds: Collection<String>
        get() = replacements.keys
}
