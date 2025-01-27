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
package net.fabricmc.loader.impl.game.minecraft;

import java.io.IOException;
import java.net.JarURLConnection;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.URLConnection;
import java.util.concurrent.CompletableFuture;

public final class BundlerClassPathCapture {
	static final CompletableFuture<URL[]> FUTURE = new CompletableFuture<>();

	public static void main(String[] args) { // invoked by the bundler on a thread
		try {
			URLClassLoader cl = (URLClassLoader) Thread.currentThread().getContextClassLoader();
			URL[] urls = cl.getURLs();

			// suppress asm library since it conflicts with Loader's, needed for MC 24w33a

			URL asmUrl = cl.findResource("org/objectweb/asm/ClassReader.class");

			if (asmUrl != null && (asmUrl = getJarUrl(asmUrl)) != null) {
				for (int i = 0; i < urls.length; i++) {
					if (urls[i].equals(asmUrl)) {
						URL[] newUrls = new URL[urls.length - 1];
						System.arraycopy(urls, 0, newUrls, 0, i);
						System.arraycopy(urls, i + 1, newUrls, i, urls.length - i - 1);
						urls = newUrls;
						break;
					}
				}
			}

			FUTURE.complete(urls);
		} catch (Throwable t) {
			FUTURE.completeExceptionally(t);
		}
	}

	/**
	 * Transform jar:file url to its outer file url.
	 *
	 * <p>jar:file:/path/to.jar!/pkg/Cls.class -> file:/path/to.jar
	 */
	private static URL getJarUrl(URL url) throws IOException {
		URLConnection connection = url.openConnection();

		if (connection instanceof JarURLConnection) {
			return ((JarURLConnection) connection).getJarFileURL();
		}

		return null;
	}
}
