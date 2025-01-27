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

import net.fabricmc.loader.impl.FormattedException
import java.io.*
import java.util.*
import java.util.function.UnaryOperator

class MoltenexStatusTree {
    enum class FabricTreeWarningLevel {
        ERROR,
        WARN,
        INFO,
        NONE;

        @JvmField
		val lowerCaseName: String = name.lowercase()

        fun isHigherThan(other: FabricTreeWarningLevel): Boolean {
            return ordinal < other.ordinal
        }

        fun isAtLeast(other: FabricTreeWarningLevel): Boolean {
            return ordinal <= other.ordinal
        }

        companion object {
            fun getHighest(a: FabricTreeWarningLevel, b: FabricTreeWarningLevel): FabricTreeWarningLevel {
                return if (a.isHigherThan(b)) a else b
            }
        }
    }

    enum class FabricBasicButtonType {
        /** Sends the status message to the main application, then disables itself.  */
        CLICK_ONCE,

        /** Sends the status message to the main application, remains enabled.  */
        CLICK_MANY
    }

    @JvmField
	val title: String
    @JvmField
	val mainText: String
    @JvmField
	val tabs: MutableList<MoltenexStatusTab> = ArrayList()
    @JvmField
	val buttons: MutableList<MoltenexStatusButton> = ArrayList()

    constructor(title: String, mainText: String) {
        Objects.requireNonNull(title, "null title")
        Objects.requireNonNull(mainText, "null mainText")

        this.title = title
        this.mainText = mainText
    }

    constructor(`is`: DataInputStream) {
        title = `is`.readUTF()
        mainText = `is`.readUTF()

        for (i in `is`.readInt() downTo 1) {
            tabs.add(MoltenexStatusTab(`is`))
        }

        for (i in `is`.readInt() downTo 1) {
            buttons.add(MoltenexStatusButton(`is`))
        }
    }

    @Throws(IOException::class)
    fun writeTo(os: DataOutputStream) {
        os.writeUTF(title)
        os.writeUTF(mainText)
        os.writeInt(tabs.size)

        for (tab in tabs) {
            tab.writeTo(os)
        }

        os.writeInt(buttons.size)

        for (button in buttons) {
            button.writeTo(os)
        }
    }

    fun addTab(name: String): MoltenexStatusTab {
        val tab = MoltenexStatusTab(name)
        tabs.add(tab)
        return tab
    }

    fun addButton(text: String, type: FabricBasicButtonType): MoltenexStatusButton {
        val button = MoltenexStatusButton(text, type)
        buttons.add(button)
        return button
    }

    class MoltenexStatusButton {
        @JvmField
		val text: String
        @JvmField
		val type: FabricBasicButtonType
        @JvmField
		var clipboard: String? = null
        @JvmField
		var shouldClose: Boolean = false
        @JvmField
		var shouldContinue: Boolean = false

        constructor(text: String, type: FabricBasicButtonType) {
            Objects.requireNonNull(text, "null text")

            this.text = text
            this.type = type
        }

        constructor(`is`: DataInputStream) {
            text = `is`.readUTF()
            type = FabricBasicButtonType.valueOf(`is`.readUTF())
            shouldClose = `is`.readBoolean()
            shouldContinue = `is`.readBoolean()

            if (`is`.readBoolean()) clipboard = `is`.readUTF()
        }

        @Throws(IOException::class)
        fun writeTo(os: DataOutputStream) {
            os.writeUTF(text)
            os.writeUTF(type.name)
            os.writeBoolean(shouldClose)
            os.writeBoolean(shouldContinue)

            if (clipboard != null) {
                os.writeBoolean(true)
                os.writeUTF(clipboard)
            } else {
                os.writeBoolean(false)
            }
        }

        fun makeClose(): MoltenexStatusButton {
            shouldClose = true
            return this
        }

        fun makeContinue(): MoltenexStatusButton {
            this.shouldContinue = true
            return this
        }

        fun withClipboard(clipboard: String?): MoltenexStatusButton {
            this.clipboard = clipboard
            return this
        }
    }

    class MoltenexStatusTab {
        @JvmField
		val node: FabricStatusNode

        /** The minimum warning level to display for this tab.  */
		@JvmField
		var filterLevel: FabricTreeWarningLevel = FabricTreeWarningLevel.NONE

        constructor(name: String) {
            this.node = FabricStatusNode(null, name)
        }

        constructor(`is`: DataInputStream) {
            node = FabricStatusNode(null, `is`)
            filterLevel = FabricTreeWarningLevel.valueOf(`is`.readUTF())
        }

        @Throws(IOException::class)
        fun writeTo(os: DataOutputStream) {
            node.writeTo(os)
            os.writeUTF(filterLevel.name)
        }

        fun addChild(name: String): FabricStatusNode {
            return node.addChild(name)
        }
    }

    class FabricStatusNode {
        private var parent: FabricStatusNode?
        @JvmField
		var name: String

        /** The icon type. There can be a maximum of 2 decorations (added with "+" symbols), or 3 if the
         * [warning level][.setWarningLevel] is set to
         * [FabricTreeWarningLevel.NONE]  */
		@JvmField
		var iconType: String = ICON_TYPE_DEFAULT
        var maximumWarningLevel: FabricTreeWarningLevel = FabricTreeWarningLevel.NONE
            private set
        @JvmField
		var expandByDefault: Boolean = false

        /** Extra text for more information. Lines should be separated by "\n".  */
		@JvmField
		var details: String? = null
        @JvmField
		val children: MutableList<FabricStatusNode> = ArrayList()

        internal constructor(parent: FabricStatusNode?, name: String) {
            Objects.requireNonNull(name, "null name")

            this.parent = parent
            this.name = name
        }

        constructor(parent: FabricStatusNode?, `is`: DataInputStream) {
            this.parent = parent

            name = `is`.readUTF()
            iconType = `is`.readUTF()
            maximumWarningLevel = FabricTreeWarningLevel.valueOf(`is`.readUTF())
            expandByDefault = `is`.readBoolean()
            if (`is`.readBoolean()) details = `is`.readUTF()

            for (i in `is`.readInt() downTo 1) {
                children.add(FabricStatusNode(this, `is`))
            }
        }

        @Throws(IOException::class)
        fun writeTo(os: DataOutputStream) {
            os.writeUTF(name)
            os.writeUTF(iconType)
            os.writeUTF(maximumWarningLevel.name)
            os.writeBoolean(expandByDefault)
            os.writeBoolean(details != null)
            if (details != null) os.writeUTF(details)
            os.writeInt(children.size)

            for (child in children) {
                child.writeTo(os)
            }
        }

        fun moveTo(newParent: FabricStatusNode) {
            parent!!.children.remove(this)
            this.parent = newParent
            newParent.children.add(this)
        }

        fun setWarningLevel(level: FabricTreeWarningLevel) {
            if (this.maximumWarningLevel == level) {
                return
            }

            if (maximumWarningLevel.isHigherThan(level)) {
                // Just because I haven't written the back-fill revalidation for this
                throw Error("Why would you set the warning level multiple times?")
            } else {
                if (parent != null && level.isHigherThan(parent!!.maximumWarningLevel)) {
                    parent!!.setWarningLevel(level)
                }

                this.maximumWarningLevel = level
                expandByDefault = expandByDefault or level.isAtLeast(FabricTreeWarningLevel.WARN)
            }
        }

        fun setError() {
            setWarningLevel(FabricTreeWarningLevel.ERROR)
        }

        fun setWarning() {
            setWarningLevel(FabricTreeWarningLevel.WARN)
        }

        fun setInfo() {
            setWarningLevel(FabricTreeWarningLevel.INFO)
        }

        fun addChild(string: String): FabricStatusNode {
            if (string.startsWith("\t")) {
                if (children.size == 0) {
                    val rootChild = FabricStatusNode(this, "")
                    children.add(rootChild)
                }

                val lastChild = children[children.size - 1]
                lastChild.addChild(string.substring(1))
                lastChild.expandByDefault = true
                return lastChild
            } else {
                val child = FabricStatusNode(this, cleanForNode(string))
                children.add(child)
                return child
            }
        }

        private fun cleanForNode(string: String): String {
            var string = string
            string = string.trim { it <= ' ' }

            if (string.length > 1) {
                if (string.startsWith("-")) {
                    string = string.substring(1)
                    string = string.trim { it <= ' ' }
                }
            }

            return string
        }

        fun addMessage(message: String, warningLevel: FabricTreeWarningLevel): FabricStatusNode {
            val lines = message.split("\n".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()

            val sub = FabricStatusNode(this, lines[0])
            children.add(sub)
            sub.setWarningLevel(warningLevel)

            for (i in 1..<lines.size) {
                sub.addChild(lines[i])
            }

            return sub
        }

        fun addException(exception: Throwable): FabricStatusNode {
            return addException(
                this,
                Collections.newSetFromMap(IdentityHashMap()),
                exception,
                UnaryOperator.identity(),
                arrayOfNulls(0)
            )
        }

        fun addCleanedException(exception: Throwable): FabricStatusNode {
            return addException(
                this, Collections.newSetFromMap(IdentityHashMap()), exception,
                { e: Throwable ->
                    var e = e
                    // Remove some self-repeating exception traces from the tree
                    // (for example the RuntimeException that is is created unnecessarily by ForkJoinTask)
                    var cause: Throwable

                    while ((e.cause.also { cause = it!! }) != null) {
                        if (e.suppressed.size > 0) {
                            break
                        }

                        var msg = e.message

                        if (msg == null) {
                            msg = e.javaClass.name
                        }

                        if (msg != cause.message && msg != cause.toString()) {
                            break
                        }

                        e = cause
                    }
                    e
                }, arrayOfNulls(0)
            )
        }

        private fun addException(exception: Throwable, parentTrace: Array<StackTraceElement?>): FabricStatusNode {
            val showTrace = exception !is FormattedException || exception.cause != null

            val msg = if (exception is FormattedException) {
                Objects.toString(exception.message)
            } else if (exception.message == null || exception.message!!.isEmpty()) {
                exception.toString()
            } else {
                String.format("%s: %s", exception.javaClass.simpleName, exception.message)
            }

            val sub = addMessage(msg, FabricTreeWarningLevel.ERROR)

            if (!showTrace) return sub

            val trace = exception.stackTrace
            var uniqueFrames = trace.size - 1

            run {
                var i = parentTrace.size - 1
                while (uniqueFrames >= 0 && i >= 0 && trace[uniqueFrames] == parentTrace[i]) {
                    uniqueFrames--
                    i--
                }
            }

            val frames = StringJoiner("\n")
            val inheritedFrames = trace.size - 1 - uniqueFrames

            for (i in 0..uniqueFrames) {
                frames.add("at " + trace[i])
            }

            if (inheritedFrames > 0) {
                frames.add("... $inheritedFrames more")
            }

            sub.addChild(frames.toString()).iconType = ICON_TYPE_JAVA_CLASS

            val sw = StringWriter()
            exception.printStackTrace(PrintWriter(sw))
            sub.details = sw.toString()

            return sub
        }

        /** If this node has one child then it merges the child node into this one.  */
        fun mergeWithSingleChild(join: String) {
            if (children.size != 1) {
                return
            }

            val child = children.removeAt(0)
            name += join + child.name

            for (cc in child.children) {
                cc.parent = this
                children.add(cc)
            }

            child.children.clear()
        }

        fun mergeSingleChildFilePath(folderType: String) {
            if (iconType != folderType) {
                return
            }

            while (children.size == 1 && children[0].iconType == folderType) {
                mergeWithSingleChild("/")
            }

            children.sortWith { a: FabricStatusNode, b: FabricStatusNode -> a.name.compareTo(b.name) }
            mergeChildFilePaths(folderType)
        }

        fun mergeChildFilePaths(folderType: String) {
            for (node in children) {
                node.mergeSingleChildFilePath(folderType)
            }
        }

        fun getFileNode(file: String, folderType: String, fileType: String): FabricStatusNode {
            var fileNode = this

            pathIteration@ for (s in file.split("/".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()) {
                if (s.isEmpty()) {
                    continue
                }

                for (c in fileNode.children) {
                    if (c.name == s) {
                        fileNode = c
                        continue@pathIteration
                    }
                }

                if (fileNode.iconType == ICON_TYPE_DEFAULT) {
                    fileNode.iconType = folderType
                }

                fileNode = fileNode.addChild(s)
            }

            fileNode.iconType = fileType
            return fileNode
        }

        companion object {
            private fun addException(
                node: FabricStatusNode,
                seen: MutableSet<Throwable>,
                exception: Throwable,
                filter: UnaryOperator<Throwable>,
                parentTrace: Array<StackTraceElement?>
            ): FabricStatusNode {
                var exception = exception
                if (!seen.add(exception)) {
                    return node
                }

                exception = filter.apply(exception)
                val sub = node.addException(exception, parentTrace)
                val trace = exception.stackTrace

                for (t in exception.suppressed) {
                    val suppressed = addException(sub, seen, t, filter, trace)
                    suppressed.name += " (suppressed)"
                    suppressed.expandByDefault = false
                }

                if (exception.cause != null) {
                    addException(sub, seen, exception.cause!!, filter, trace)
                }

                return sub
            }
        }
    }

    companion object {
        /** No icon is displayed.  */
        const val ICON_TYPE_DEFAULT: String = ""

        /** Generic folder.  */
        const val ICON_TYPE_FOLDER: String = "folder"

        /** Generic (unknown contents) file.  */
        const val ICON_TYPE_UNKNOWN_FILE: String = "file"

        /** Generic non-Fabric jar file.  */
        const val ICON_TYPE_JAR_FILE: String = "jar"

        /** Generic Fabric-related jar file.  */
        const val ICON_TYPE_FABRIC_JAR_FILE: String = "jar+fabric"

        /** Something related to Fabric (It's not defined what exactly this is for, but it uses the main Fabric logo).  */
        const val ICON_TYPE_FABRIC: String = "fabric"

        /** Generic JSON file.  */
        const val ICON_TYPE_JSON: String = "json"

        /** A file called "fabric.mod.json".  */
        const val ICON_TYPE_FABRIC_JSON: String = "json+fabric"

        /** Java bytecode class file.  */
        const val ICON_TYPE_JAVA_CLASS: String = "java_class"

        /** A folder inside of a Java JAR.  */
        const val ICON_TYPE_PACKAGE: String = "package"

        /** A folder that contains Java class files.  */
        const val ICON_TYPE_JAVA_PACKAGE: String = "java_package"

        /** A tick symbol, used to indicate that something matched.  */
        const val ICON_TYPE_TICK: String = "tick"

        /** A cross symbol, used to indicate that something didn't match (although it's not an error). Used as the opposite
         * of [.ICON_TYPE_TICK]  */
        const val ICON_TYPE_LESSER_CROSS: String = "lesser_cross"
    }
}
