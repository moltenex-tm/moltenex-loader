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
package net.fabricmc.api

/**
 * Represents a type of environment.
 *
 *
 * A type of environment is a jar file in a *Minecraft* version's json file's `download`
 * subsection, including the `client.jar` and the `server.jar`.
 *
 * @see Environment
 *
 * @see EnvironmentInterface
 */
enum class EnvType {
    /**
     * Represents the client environment type, in which the `client.jar` for a
     * *Minecraft* version is the main game jar.
     *
     *
     * A client environment type has all client logic (client rendering and integrated
     * server logic), the data generator logic, and dedicated server logic. It encompasses
     * everything that is available on the [server environment type][.SERVER].
     */
    CLIENT,

    /**
     * Represents the server environment type, in which the `server.jar` for a
     * *Minecraft* version is the main game jar.
     *
     *
     * A server environment type has the dedicated server logic and data generator
     * logic, which are all included in the [client environment type][.CLIENT].
     * However, the server environment type has its libraries embedded compared to the
     * client.
     */
    SERVER
}
