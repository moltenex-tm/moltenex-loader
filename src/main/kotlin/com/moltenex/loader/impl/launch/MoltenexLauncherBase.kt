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
package com.moltenex.loader.impl.launch

import com.moltenex.loader.impl.MoltenexLoaderImpl
import net.fabricmc.loader.impl.FormattedException
import com.moltenex.loader.impl.gui.MoltenexGuiEntry.displayError
import net.fabricmc.loader.impl.util.log.Log
import net.fabricmc.loader.impl.util.log.LogCategory
import org.spongepowered.asm.mixin.MixinEnvironment
import java.io.FileDescriptor
import java.io.FileOutputStream
import java.io.PrintWriter
import kotlin.system.exitProcess

abstract class MoltenexLauncherBase protected constructor() : MoltenexLauncher {
    init {
        launcher = this
    }

    override val mappingConfiguration: MappingConfiguration
        get() = Companion.mappingConfiguration

    companion object {
        var isMixinReady: Boolean = false
            private set

        var properties: Map<String, Any>? = null
            set(propertiesA) {
                if (field != null && field !== propertiesA) {
                    throw RuntimeException("Duplicate setProperties call!")
                }

                field = propertiesA
            }

        var launcher: MoltenexLauncher? = null
            set(launcherA) {
                if (field != null && field !== launcherA) {
                    throw RuntimeException("Duplicate setLauncher call!")
                }
                field = launcherA
            }

        private val mappingConfiguration = MappingConfiguration()

        @Throws(ClassNotFoundException::class)
        fun getClass(className: String?): Class<*> {
            return Class.forName(className, true, launcher!!.targetClassLoader)
        }

        @JvmStatic
		protected fun handleFormattedException(exc: FormattedException) {
            val actualExc = if (exc.message != null) exc else exc.cause!!
            Log.error(LogCategory.GENERAL, exc.mainText, actualExc)

            val gameProvider = MoltenexLoaderImpl.INSTANCE?.tryGetGameProvider()

            if (gameProvider == null || !gameProvider.displayCrash(actualExc, exc.displayedText)) {
                displayError(exc.displayedText, actualExc, true)
            } else {
                exitProcess(1)
            }

            throw AssertionError("exited")
        }

        @JvmStatic
		protected fun setupUncaughtExceptionHandler() {
            val mainThread = Thread.currentThread()
            Thread.setDefaultUncaughtExceptionHandler { t, e ->
                try {
                    if (e is FormattedException) {
                        handleFormattedException(e)
                    } else {
                        val mainText = String.format("Uncaught exception in thread \"%s\"", t.name)
                        Log.error(LogCategory.GENERAL, mainText, e)

                        val gameProvider = MoltenexLoaderImpl.INSTANCE?.tryGetGameProvider()

                        if (Thread.currentThread() === mainThread
                            && (gameProvider == null || !gameProvider.displayCrash(e, mainText))
                        ) {
                            displayError(mainText, e, false)
                        }
                    }
                } catch (e2: Throwable) { // just in case
                    e.addSuppressed(e2)

                    try {
                        e.printStackTrace()
                    } catch (e3: Throwable) {
                        val pw = PrintWriter(FileOutputStream(FileDescriptor.err))
                        e.printStackTrace(pw)
                        pw.flush()
                    }
                }
            }
        }

        @JvmStatic
		protected fun finishMixinBootstrapping() {
            if (isMixinReady) {
                throw RuntimeException("Must not call MoltenexLauncherBase.finishMixinBootstrapping() twice!")
            }

            try {
                val m =
                    MixinEnvironment::class.java.getDeclaredMethod("gotoPhase", MixinEnvironment.Phase::class.java)
                m.isAccessible = true
                m.invoke(null, MixinEnvironment.Phase.INIT)
                m.invoke(null, MixinEnvironment.Phase.DEFAULT)
            } catch (e: Exception) {
                throw RuntimeException(e)
            }

            isMixinReady = true
        }
    }
}
