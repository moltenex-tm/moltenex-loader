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
/*
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScopeInstance.align
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.graphics.painter.BitmapPainter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import net.fabricmc.loader.impl.util.StringUtil
import okio.IOException
import java.awt.GraphicsEnvironment
import java.awt.HeadlessException
import java.awt.Image
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.io.InputStream
import java.util.concurrent.CountDownLatch

class MoltenexMainWindow {

    fun open(tree: MoltenexStatusTree, shouldWait: Boolean) {
        if (GraphicsEnvironment.isHeadless()) {
            throw HeadlessException()
        }

        // Set MacOS specific system props
        System.setProperty("apple.awt.application.appearance", "system")
        System.setProperty("apple.awt.application.name", tree.title)

        // Use Compose for Desktop to open the UI
        open0(tree, shouldWait)
    }

    private fun open0(tree: MoltenexStatusTree, shouldWait: Boolean) {
        val guiTerminatedLatch = CountDownLatch(1)

        // Launch the Compose UI on the main thread
        runBlocking {
            launch(Dispatchers.Main) {
                createUi(guiTerminatedLatch, tree)
            }
        }

        if (shouldWait) {
            guiTerminatedLatch.await()
        }
    }
@Composable
    fun createUi(guiTerminatedLatch: CountDownLatch?, tree: MoltenexStatusTree) {
        val image: Painter? = remember { loadImage("/ui/icon/fabric_x128.png") }

        LaunchedEffect(Unit) {
            withContext(Dispatchers.IO) {
                image?.let {
                    setTaskBarImage(it) // Implement your taskbar update logic here
                }
            }
        }

        // Dialog title
        Text(
            text = tree.title,
            style = MaterialTheme.typography.h5,
            modifier = Modifier.padding(16.dp)
        )

        // Display mainText as error label
        if (tree.mainText.isNotEmpty()) {
            Text(
                text = tree.mainText,
                style = MaterialTheme.typography.h6,
                modifier = Modifier.padding(16.dp).align(Alignment.CenterHorizontally)
            )
        }

        // Handle Tabs
        if (tree.tabs.isEmpty()) {
            Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                Text("Opening Errors", style = MaterialTheme.typography.h6)
                Text("No tabs provided! (Something is very broken)", style = MaterialTheme.typography.body1)
            }
        } else {
            tree.tabs.forEach { tab ->
                TabScreen(tab)
            }
        }

        // Handle Buttons
        if (tree.buttons.isNotEmpty()) {
            Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                tree.buttons.forEach { button ->
                    Button(
                        onClick = {
                            handleButtonClick(button, onCloseLatch)
                        },
                        modifier = Modifier.fillMaxWidth().padding(8.dp)
                    ) {
                        Text(button.text)
                    }
                }
            }
        }

        // Handle close logic on window close
        DisposableEffect(onCloseLatch) {
            onDispose {
                onCloseLatch.countDown()
            }
        }
    }
    @Composable
    fun TabScreen(tab: MoltenexStatusTree.MoltenexStatusTab) {
        // Render tab content, assuming createTreePanel() provides some UI layout for each tab
        Column(modifier = Modifier.fillMaxSize()) {
            // Use createTreePanel equivalent in Compose to render tab details
            Text(tab.node.name, style = MaterialTheme.typography.h6)
            // Add the content of the tab
        }
    }

    fun handleButtonClick(button: MoltenexStatusTree.MoltenexStatusButton, onCloseLatch: CountDownLatch) {
        if (button.type == MoltenexStatusTree.FabricBasicButtonType.CLICK_ONCE) {
            // Disable button logic can be implemented here
        }

        if (button.clipboard != null) {
            try {
                val clipboard = StringSelection(button.clipboard)
                Toolkit.getDefaultToolkit().systemClipboard.setContents(clipboard, clipboard)
            } catch (e: IllegalStateException) {
                // Clipboard unavailable
            }
        }

        if (button.shouldClose) {
            // Close window logic in Compose (simply dispose of UI screen/activity)
        }

        if (button.shouldContinue) {
            onCloseLatch.countDown()
        }
    }

    @Composable
    fun createTreePanel(
        rootNode: MoltenexStatusTree.FabricStatusNode,
        minimumWarningLevel: MoltenexStatusTree.FabricTreeWarningLevel,
        iconSet: IconSet
    ) {
        val treeState = remember { mutableStateOf(buildTreeState(rootNode, minimumWarningLevel)) }

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(treeState.value) { node ->
                TreeNodeView(node, iconSet)
            }
        }
    }

    @Composable
    fun TreeNodeView(node: CustomTreeNode, iconSet: IconSet) {
        Column(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
            Text(
                text = node.node.name, // Assuming `name` is the node name
                style = MaterialTheme.typography.body1,
                modifier = Modifier.padding(8.dp)
            )
            // Display any additional information or icons based on the node data
            if (node.node.expandByDefault) {
                // You could handle expanding nodes or add more nested views here.
            }
        }
    }

    @Composable
    fun buildTreeState(
        rootNode: MoltenexStatusTree.FabricStatusNode,
        minimumWarningLevel: MoltenexStatusTree.FabricTreeWarningLevel
    ): List<CustomTreeNode> {
        // Here, we build the list of CustomTreeNode objects based on the rootNode
        // This replaces the functionality of the JTree and CustomTreeNode creation
        return listOf(CustomTreeNode(rootNode)) // Replace with actual node processing logic
    }

    fun loadImage(path: String): Painter? {
        // Implement the logic to load images as a Painter in Compose, you may use Coil or another image loading library
        return null
    }

    fun setTaskBarImage(image: Image) {
        // This logic is specific to desktop Java, so it's not directly applicable in Compose for Android
        // However, if this is being used in a desktop environment with JetBrains Compose for Desktop, you can integrate this there.
        try {
            // Use reflection for the taskbar setting on Java
            val taskbarClass = Class.forName("java.awt.Taskbar")
            val getTaskbar = taskbarClass.getDeclaredMethod("getTaskbar")
            val setIconImage = taskbarClass.getDeclaredMethod("setIconImage", Image::class.java)
            val taskbar = getTaskbar.invoke(null)
            setIconImage.invoke(taskbar, image)
        } catch (e: Exception) {
            // Handle exception (ignored for now)
        }
    }

    class IconSet {
        private val icons: MutableMap<IconInfo, MutableMap<Int, BitmapPainter>> = mutableMapOf()

        @Composable
        fun get(info: IconInfo): BitmapPainter {
            val scale = 16 // You can adjust this based on screen density

            val map = icons.getOrPut(info) { mutableMapOf() }
            val cachedIcon = map[scale]

            return cachedIcon ?: run {
                try {
                    val icon = loadIcon(info, scale)
                    map[scale] = icon
                    icon
                } catch (e: IOException) {
                    missingIcon()
                }
            }
        }

        @Composable
        fun missingIcon(): BitmapPainter {
            val missingIcon = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888)

            for (y in 0 until 16) {
                for (x in 0 until 16) {
                    missingIcon.setPixel(x, y, Color(255, 255, 242).toArgb())
                }
            }

            for (i in 0 until 16) {
                missingIcon.setPixel(0, i, Color(34, 34, 34).toArgb())
                missingIcon.setPixel(15, i, Color(34, 34, 34).toArgb())
                missingIcon.setPixel(i, 0, Color(34, 34, 34).toArgb())
                missingIcon.setPixel(i, 15, Color(34, 34, 34).toArgb())
            }

            for (i in 3 until 13) {
                missingIcon.setPixel(i, i, Color(155, 0, 0).toArgb())
                missingIcon.setPixel(i, 16 - i, Color(155, 0, 0).toArgb())
            }

            return BitmapPainter(missingIcon.asImageBitmap())
        }

        @Composable
        fun loadIcon(info: IconInfo, scale: Int): BitmapPainter {
            val img = loadImage("/ui/icon/${info.mainPath}_x${scale}.png")

            // Create a Bitmap of the requested scale
            val scaledBitmap = Bitmap.createBitmap(scale, scale, Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(scaledBitmap)

            canvas.drawBitmap(img, 0f, 0f, null)

            val coords = arrayOf(
                intArrayOf(0, 8),
                intArrayOf(8, 8),
                intArrayOf(8, 0)
            )

            for (i in info.decor.indices) {
                val decor = info.decor[i]

                if (decor != null) {
                    val decorImg = loadImage("/ui/icon/decoration/${decor}_x${scale / 2}.png")
                    canvas.drawBitmap(decorImg, coords[i][0].toFloat(), coords[i][1].toFloat(), null)
                }
            }

            return BitmapPainter(scaledBitmap.asImageBitmap())
        }

        private fun loadImage(path: String): Bitmap {
            val inputStream = loadStream(path)
            return BitmapFactory.decodeStream(inputStream)
        }

        private fun loadStream(path: String): InputStream {
            return javaClass.getResourceAsStream(path)
                ?: throw IOException("Resource not found: $path")
        }
    }

    @Composable
    fun IconDisplay(iconSet: IconSet, info: IconInfo) {
        val iconPainter = iconSet.get(info)

        Image(painter = iconPainter, contentDescription = "Icon", modifier = Modifier.size(16.dp))
    }

    data class IconInfo(
        val mainPath: String,
        val decor: Array<String> = emptyArray()
    ) {
        init {
            require(decor.size < 4) { "Cannot fit more than 3 decorations into an image (and leave space for the background)" }
        }

        val hash: Int = mainPath.hashCode() * 31 + decor.contentHashCode()

        companion object {
            fun fromNode(node: MoltenexStatusTree.FabricStatusNode): IconInfo {
                val split = node.iconType.split("+")

                val decors = mutableListOf<String>()
                val warnLevel = node.getMaximumWarningLevel()
                val main: String

                if (split.isEmpty() || split[0].isEmpty()) {
                    // Empty string, but we might replace it with a warning
                    main = if (warnLevel == MoltenexStatusTree.FabricTreeWarningLevel.NONE) {
                        "missing"
                    } else {
                        "level_${warnLevel.lowerCaseName}"
                    }
                } else {
                    main = split[0]
                    if (warnLevel == MoltenexStatusTree.FabricTreeWarningLevel.NONE) {
                        // Just to add a gap
                        decors.add("")
                    } else {
                        decors.add("level_${warnLevel.lowerCaseName}")
                    }

                    for (i in 1 until split.size) {
                        if (i < 3) {
                            decors.add(split[i])
                        }
                    }
                }

                return IconInfo(main, decors.toTypedArray())
            }
        }
    }

    data class CustomTreeNode(
        val node: MoltenexStatusTree.FabricStatusNode,
        val parent: CustomTreeNode? = null,
        val minimumWarningLevel: MoltenexStatusTree.FabricTreeWarningLevel
    ) {
        val displayedChildren: List<CustomTreeNode> = node.children
            .filter { minimumWarningLevel.isHigherThan(it.getMaximumWarningLevel()).not() }
            .map { CustomTreeNode(it, this, minimumWarningLevel) }

        val iconInfo: IconInfo by lazy {
            IconInfo.fromNode(node)
        }

        val toolTip: String? = node.details?.let { applyWrapping(it) }

        fun getChildAt(index: Int): CustomTreeNode = displayedChildren[index]
        fun getChildCount(): Int = displayedChildren.size
        fun isLeaf(): Boolean = displayedChildren.isEmpty()

        // Return the string version of the node, with wrapping for UI purposes.
        override fun toString(): String = applyWrapping(StringUtil.wrapLines(node.name, 120))
    }

    private fun applyWrapping(str: String): String {
        return if (str.contains("\n")) {
            str.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\n", "<br>")
                .let { "<html>$it</html>" }
        } else {
            str
        }
    }

    @Composable
    fun CustomTreeNodeView(node: CustomTreeNode) {
        val icon = remember { IconSet().get(node.iconInfo) }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(8.dp)
        ) {
            Image(painter = icon, contentDescription = "Icon", modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.width(8.dp))

            Column {
                Text(
                    text = node.toString(),
                    style = MaterialTheme.typography.body1
                )
                node.toolTip?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.body2,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
    }

    @Composable
    fun CustomTreeNodeList(nodes: List<CustomTreeNode>) {
        LazyColumn {
            items(nodes) { node ->
                CustomTreeNodeView(node)
                if (node.isLeaf().not()) {
                    CustomTreeNodeList(nodes = node.displayedChildren)  // Recursively display children
                }
            }
        }
    }

}*/