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
package net.fabricmc.loader.impl.util.log

interface LogHandler {
    fun log(
        time: Long,
        level: LogLevel?,
        category: LogCategory?,
        msg: String?,
        exc: Throwable?,
        fromReplay: Boolean,
        wasSuppressed: Boolean
    )

    fun shouldLog(level: LogLevel?, category: LogCategory?): Boolean
    fun close()
}
