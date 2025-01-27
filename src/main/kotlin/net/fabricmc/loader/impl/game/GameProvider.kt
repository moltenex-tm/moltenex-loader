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
package net.fabricmc.loader.impl.game

import java.nio.file.Path
import java.util.Objects

import net.fabricmc.loader.api.metadata.ModMetadata
import net.fabricmc.loader.impl.game.patch.GameTransformer
import com.moltenex.loader.impl.launch.MoltenexLauncher
import net.fabricmc.loader.impl.util.Arguments
import net.fabricmc.loader.impl.util.LoaderUtil

interface GameProvider { // name directly referenced in net.fabricmc.loader.impl.launch.knot.Knot.findEmbeddedGameProvider() and service loader records
	fun getGameId(): String
	fun getGameName(): String
	fun getRawGameVersion(): String
	fun getNormalizedGameVersion(): String
	fun getBuiltinMods(): Collection<BuiltinMod>

	fun getEntrypoint(): String
	fun getLaunchDirectory(): Path
	fun isObfuscated(): Boolean
	fun requiresUrlClassLoader(): Boolean

	fun isEnabled(): Boolean
	fun locateGame(launcher: MoltenexLauncher, args: Array<String>): Boolean
	fun initialize(launcher: MoltenexLauncher)
	fun getEntrypointTransformer(): GameTransformer
	fun unlockClassPath(launcher: MoltenexLauncher)
	fun launch(loader: ClassLoader)

	fun displayCrash(exception:Throwable, context:String):Boolean {
		return false
	}

	fun getArguments(): Arguments
	fun getLaunchArguments(sanitize:Boolean): Array<String>

	fun canOpenErrorGui(): Boolean {
		return true
	}

	fun hasAwtSupport(): Boolean {
		return LoaderUtil.hasAwtSupport()
	}

	class BuiltinMod(paths: List<Path>, metadata: ModMetadata) {
		var paths : List<Path>
		var metadata: ModMetadata

		init{
			Objects.requireNonNull(paths, "null paths")
			Objects.requireNonNull(metadata, "null metadata")

			this.paths = paths
			this.metadata = metadata
		}
	}
}
