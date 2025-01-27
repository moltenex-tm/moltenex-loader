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
package com.moltenex.loader.impl.gui

import com.moltenex.loader.impl.MoltenexLoaderImpl
import com.moltenex.loader.impl.gui.MoltenexStatusTree.FabricBasicButtonType
import com.moltenex.loader.impl.gui.MoltenexStatusTree.FabricTreeWarningLevel
import net.fabricmc.loader.impl.util.LoaderUtil
import net.fabricmc.loader.impl.util.Localization
import net.fabricmc.loader.impl.util.UrlUtil
import net.fabricmc.loader.impl.util.log.Log
import net.fabricmc.loader.impl.util.log.LogCategory
import java.awt.GraphicsEnvironment
import java.io.*
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.function.Consumer
import kotlin.system.exitProcess

/** The main entry point for all fabric-based stuff.  */
object MoltenexGuiEntry {
    /** Opens the given [MoltenexStatusTree] in a new swing window.
     *
     * @throws Exception if something went wrong while opening the window.
     */
    @Throws(Exception::class)
    fun open(tree: MoltenexStatusTree) {
        val provider = MoltenexLoaderImpl.INSTANCE?.tryGetGameProvider()

        if (provider == null && LoaderUtil.hasAwtSupport()
            || provider != null && provider.hasAwtSupport()
        ) {
            FabricMainWindow.open(tree, true)
        } else {
            openForked(tree)
        }
    }

    @Throws(IOException::class, InterruptedException::class)
    private fun openForked(tree: MoltenexStatusTree) {
        val javaBinDir = LoaderUtil.normalizePath(Paths.get(System.getProperty("java.home"), "bin"))
        val executables = arrayOf("javaw.exe", "java.exe", "java")
        var javaPath: Path? = null

        for (executable in executables) {
            val path = javaBinDir.resolve(executable)

            if (Files.isRegularFile(path)) {
                javaPath = path
                break
            }
        }

        if (javaPath == null) throw RuntimeException("can't find java executable in $javaBinDir")

        val process = ProcessBuilder(
            javaPath.toString(), "-Xmx100M", "-cp", UrlUtil.LOADER_CODE_SOURCE.toString(),
            MoltenexGuiEntry::class.java.name
        )
            .redirectOutput(ProcessBuilder.Redirect.INHERIT)
            .redirectError(ProcessBuilder.Redirect.INHERIT)
            .start()

        val shutdownHook = Thread { process.destroy() }

        Runtime.getRuntime().addShutdownHook(shutdownHook)

        DataOutputStream(process.outputStream).use { os ->
            tree.writeTo(os)
        }
        val rVal = process.waitFor()

        Runtime.getRuntime().removeShutdownHook(shutdownHook)

        if (rVal != 0) throw IOException("subprocess exited with code $rVal")
    }

    @Throws(Exception::class)
    @JvmStatic
    fun main(args: Array<String>) {
        val tree = MoltenexStatusTree(DataInputStream(System.`in`))
        FabricMainWindow.open(tree, true)
        exitProcess(0)
    }

    /** @param exitAfter If true then this will call [System.exit] after showing the gui, otherwise this will
     * return normally.
     */
    fun displayCriticalError(exception: Throwable?, exitAfter: Boolean) {
        Log.error(LogCategory.GENERAL, "A critical error occurred", exception)

        displayError(Localization.format("gui.error.header"), exception, exitAfter)
    }

    @JvmStatic
	fun displayError(mainText: String?, exception: Throwable?, exitAfter: Boolean) {
        displayError(mainText, exception, { tree: MoltenexStatusTree ->
            val error = StringWriter()
            error.append(mainText)

            if (exception != null) {
                error.append(System.lineSeparator())
                exception.printStackTrace(PrintWriter(error))
            }
            tree.addButton(Localization.format("gui.button.copyError"), FabricBasicButtonType.CLICK_MANY)
                .withClipboard(error.toString())
        }, exitAfter)
    }

    fun displayError(
        mainText: String?,
        exception: Throwable?,
        treeCustomiser: Consumer<MoltenexStatusTree>,
        exitAfter: Boolean
    ) {
        val provider = MoltenexLoaderImpl.INSTANCE?.tryGetGameProvider()

        if (!GraphicsEnvironment.isHeadless() && (provider == null || provider.canOpenErrorGui())) {
            val title = "Fabric Loader " + MoltenexLoaderImpl.VERSION
            val tree = mainText?.let { MoltenexStatusTree(title, it) }
            val crashTab = tree?.addTab(Localization.format("gui.tab.crash"))

            if (exception != null) {
                crashTab?.node?.addCleanedException(exception)
            } else {
                crashTab?.node?.addMessage(Localization.format("gui.error.missingException"), FabricTreeWarningLevel.NONE)
            }

            // Maybe add an "open mods folder" button?
            // or should that be part of the main tree's right-click menu?
            tree?.addButton(Localization.format("gui.button.exit"), FabricBasicButtonType.CLICK_ONCE)?.makeClose()
            tree?.let { treeCustomiser.accept(it) }

            try {
                tree?.let { open(it) }
            } catch (e: Exception) {
                if (exitAfter) {
                    Log.warn(LogCategory.GENERAL, "Failed to open the error gui!", e)
                } else {
                    throw RuntimeException("Failed to open the error gui!", e)
                }
            }
        }

        if (exitAfter) {
            exitProcess(1)
        }
    }
}
