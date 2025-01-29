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

import kotlinx.serialization.SerializationException

open class ParseMetadataException : SerializationException {
    private var modPaths: MutableList<String>? = null

    constructor(message: String?) : super(message)

    constructor(message: String, exception: Throwable) : this(
        "$message Error was located at: $exception"
    )

    constructor(t: Throwable?) : super(t)

    fun setModPaths(modPath: String, modParentPaths: List<String?>) {
        val modParentPath = modParentPaths.toString()
        modPaths = arrayListOf(modParentPath)
        (modPaths as ArrayList<String>).add(modPath)
    }

    override val message: String
        get() {
            var ret = "Error reading fabric.mod.json file for mod at "

            ret += if (modPaths == null) {
                "unknown location"
            } else {
                java.lang.String.join(" -> ", modPaths)
            }

            val msg = super.message

            if (msg != null) {
                ret += ": $msg"
            }

            return ret
        }

    class MissingField(field: String?) :
        ParseMetadataException(String.format("Missing required field \"%s\".", field))
}