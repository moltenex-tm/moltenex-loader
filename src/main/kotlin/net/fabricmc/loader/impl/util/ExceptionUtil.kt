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
package net.fabricmc.loader.impl.util


import java.io.UncheckedIOException
import java.util.concurrent.CompletionException
import java.util.concurrent.ExecutionException
import java.util.function.Function


object ExceptionUtil {
    private val THROW_DIRECTLY = System.getProperty(SystemProperties.DEBUG_THROW_DIRECTLY) != null

    fun <T : Throwable> gatherExceptions(exc: Throwable, prev: T?, mainExcFactory: Function<Throwable?, T>): T {
        var exc = exc
        exc = unwrap(exc)

        if (THROW_DIRECTLY) throw mainExcFactory.apply(exc)

        if (prev == null) {
            return mainExcFactory.apply(exc)
        } else if (exc !== prev) {
            for (t in prev.suppressed) {
                if (exc == t) return prev
            }

            prev.addSuppressed(exc)
        }

        return prev
    }

    fun wrap(exc: Throwable): RuntimeException {
        var exc = exc
        if (exc is RuntimeException) return exc

        exc = unwrap(exc)
        if (exc is RuntimeException) return exc

        return WrappedException(exc)
    }

    private fun unwrap(exc: Throwable): Throwable {
        if (exc is WrappedException
            || exc is UncheckedIOException
            || exc is ExecutionException
            || exc is CompletionException
        ) {
            val ret = exc.cause
            if (ret != null) return unwrap(ret)
        }

        return exc
    }

    class WrappedException(cause: Throwable?) : RuntimeException(cause)
}