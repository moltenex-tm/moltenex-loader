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
package net.fabricmc.loader.impl.game.minecraft.patch

import com.moltenex.loader.api.util.version.Version
import com.moltenex.loader.impl.launch.MoltenexLauncher
import net.fabricmc.api.EnvType
import net.fabricmc.loader.api.VersionParsingException
import net.fabricmc.loader.api.metadata.version.VersionPredicate
import net.fabricmc.loader.impl.game.minecraft.Hooks
import net.fabricmc.loader.impl.game.minecraft.MinecraftGameProvider
import net.fabricmc.loader.impl.game.patch.GamePatch
import net.fabricmc.loader.impl.util.log.Log.debug
import net.fabricmc.loader.impl.util.log.Log.warn
import net.fabricmc.loader.impl.util.log.LogCategory
import net.fabricmc.loader.impl.util.version.VersionPredicateParser.parse
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.tree.*
import java.util.function.Consumer
import java.util.function.Function
import java.util.function.Predicate

class EntrypointPatch(private val gameProvider: MinecraftGameProvider) : GamePatch() {
    private fun finishEntrypoint(type: EnvType?, it: MutableListIterator<AbstractInsnNode>) {
        val methodName = String.format("start%s", if (type == EnvType.CLIENT) "Client" else "Server")
        it.add(
            MethodInsnNode(
                Opcodes.INVOKESTATIC,
                Hooks.INTERNAL_NAME,
                methodName,
                "(Ljava/io/File;Ljava/lang/Object;)V",
                false
            )
        )
    }

    override fun process(
        launcher: MoltenexLauncher?,
        classSource: (String?) -> ClassNode?,
        classEmitter: Consumer<ClassNode?>?
    ) {
        val type = launcher?.environmentType ?: return
        val entrypoint = launcher.entrypoint ?: return
        val gameVersion = gameVersion

        if (!entrypoint.startsWith("net.minecraft.") && !entrypoint.startsWith("com.mojang.")) {
            return
        }

        var gameEntrypoint: String? = null
        var serverHasFile = true
        val isApplet = entrypoint.contains("Applet")
        val mainClass = classSource(entrypoint) ?: throw RuntimeException("Could not load main class $entrypoint!")

        // Main -> Game entrypoint search
        var is20w22aServerOrHigher = false

        if (type == EnvType.CLIENT) {
            // pre-1.6 route
            val newGameFields = findFields(mainClass) { f ->
                !isStatic(f?.access ?: 0) && f?.desc?.startsWith("L") == true && !f.desc.startsWith("Ljava/")
            }

            if (newGameFields.size == 1) {
                gameEntrypoint = Type.getType(newGameFields[0].desc).className
            }
        }

        if (gameEntrypoint == null) {
            // main method searches
            var mainMethod = findMethod(mainClass) { method ->
                method?.name == "main" && method.desc == "([Ljava/lang/String;)V" && isPublicStatic(method.access)
            }

            if (mainMethod == null) {
                throw RuntimeException("Could not find main method in $entrypoint!")
            }

            if (type == EnvType.CLIENT && mainMethod.instructions.size() < 10) {
                // Handle version 22w24+ forwarding to another method
                var invocation: MethodInsnNode? = null

                for (insn in mainMethod.instructions) {
                    if (invocation == null && insn is MethodInsnNode && insn.owner == mainClass.name) {
                        invocation = insn
                    } else if (insn.opcode > Opcodes.ALOAD && insn.opcode != Opcodes.RETURN) {
                        invocation = null
                        break
                    }
                }

                if (invocation != null) {
                    val reqMethod = invocation
                    mainMethod = findMethod(mainClass) { m ->
                        m?.name == reqMethod.name && m!!.desc == reqMethod.desc
                    }
                }
            } else if (type == EnvType.SERVER) {
                // pre-1.6 method search route
                val newGameInsn = findInsn(mainMethod, Predicate { insn ->
                    insn is MethodInsnNode && insn.opcode == Opcodes.INVOKESPECIAL && insn.name == "<init>" && insn.owner == mainClass.name
                }, false) as MethodInsnNode?

                if (newGameInsn != null) {
                    gameEntrypoint = newGameInsn.owner.replace('/', '.')
                    serverHasFile = newGameInsn.desc.startsWith("(Ljava/io/File;")
                }
            }

            if (gameEntrypoint == null) {
                // modern method search routes
                var newGameInsn = findInsn(
                    mainMethod!!,
                    if (type == EnvType.CLIENT) {
                        Predicate { insn ->
                            insn is MethodInsnNode && (insn.opcode == Opcodes.INVOKESPECIAL || insn.opcode == Opcodes.INVOKEVIRTUAL) && !insn.owner.startsWith("java/")
                        }
                    } else {
                        Predicate { insn ->
                            insn is MethodInsnNode && insn.opcode == Opcodes.INVOKESPECIAL && hasSuperClass(insn.owner, mainClass.name, classSource)
                        }
                    },
                    true
                ) as MethodInsnNode?

                // Check for 20w20b server constructor
                if (newGameInsn == null && type == EnvType.SERVER) {
                    newGameInsn = findInsn(
                        mainMethod,
                        Predicate { insn ->
                            insn is MethodInsnNode && insn.opcode == Opcodes.INVOKESPECIAL && hasStrInMethod(insn.owner, "<clinit>", "()V", "^[a-fA-F0-9]{40}$", classSource)
                        },
                        false
                    ) as MethodInsnNode?
                }

                // Check for a specific log message in server version 20w22a and higher
                if (type == EnvType.SERVER && hasStrInMethod(
                        mainClass.name,
                        mainMethod.name,
                        mainMethod.desc,
                        "Safe mode active, only vanilla datapack will be loaded",
                        classSource
                    )
                ) {
                    is20w22aServerOrHigher = true
                    gameEntrypoint = mainClass.name
                }

                if (newGameInsn != null) {
                    gameEntrypoint = newGameInsn.owner.replace('/', '.')
                    serverHasFile = newGameInsn.desc.startsWith("(Ljava/io/File;")
                }
            }
        }

        if (gameEntrypoint == null) {
            throw RuntimeException("Could not find game constructor in $entrypoint!")
        }

        debug(LogCategory.GAME_PATCH, "Found game constructor: %s -> %s", entrypoint, gameEntrypoint)

        val gameClass: ClassNode?

        // Determine the final game class
        if (gameEntrypoint == entrypoint || is20w22aServerOrHigher) {
            gameClass = mainClass
        } else {
            gameClass = classSource(gameEntrypoint) ?: throw RuntimeException("Could not load game class $gameEntrypoint!")
        }

        var gameMethod: MethodNode? = null
        var gameConstructor: MethodNode? = null
        var lwjglLogNode: AbstractInsnNode? = null
        var currentThreadNode: AbstractInsnNode? = null
        var gameMethodQuality = 0

        if (!is20w22aServerOrHigher) {
            for (gmCandidate in gameClass.methods) {
                if (gmCandidate.name == "<init>") {
                    gameConstructor = gmCandidate

                    if (gameMethodQuality < 1) {
                        gameMethod = gmCandidate
                        gameMethodQuality = 1
                    }
                }

                if (type == EnvType.CLIENT && !isApplet && gameMethodQuality < 2) {
                    // Try to find a method with an LDC string "LWJGL Version: ".
                    // This is the "init()" method, or as of 19w38a is the constructor, or called somewhere in that vicinity,
                    // and is by far superior in hooking into for a well-off mod start.
                    // Also try and find a Thread.currentThread() call before the LWJGL version print.

                    var qual = 2
                    var hasLwjglLog = false

                    for (insn in gmCandidate.instructions) {
                        if (insn.opcode == Opcodes.INVOKESTATIC && insn is MethodInsnNode) {
                            val methodInsn = insn

                            if ("currentThread" == methodInsn.name && "java/lang/Thread" == methodInsn.owner && "()Ljava/lang/Thread;" == methodInsn.desc) {
                                currentThreadNode = methodInsn
                            }
                        } else if (insn is LdcInsnNode) {
                            val cst = insn.cst

                            if (cst is String) {
                                val s = cst

                                //This log output was renamed to Backend library in 19w34a
                                if (s.startsWith("LWJGL Version: ") || s.startsWith("Backend library: ")) {
                                    hasLwjglLog = true

                                    if ("LWJGL Version: " == s || "LWJGL Version: {}" == s || "Backend library: {}" == s) {
                                        qual = 3
                                        lwjglLogNode = insn
                                    }

                                    break
                                }
                            }
                        }
                    }

                    if (hasLwjglLog) {
                        gameMethod = gmCandidate
                        gameMethodQuality = qual
                    }
                }
            }
        } else {
            gameMethod = findMethod(
                mainClass
            ) { method: MethodNode? ->
                method!!.name == "main" && method.desc == "([Ljava/lang/String;)V" && isPublicStatic(
                    method.access
                )
            }
        }

        if (gameMethod == null) {
            throw RuntimeException("Could not find game constructor method in " + gameClass.name + "!")
        }

        var patched = false
        debug(LogCategory.GAME_PATCH, "Patching game constructor %s%s", gameMethod.name, gameMethod.desc)

        if (type == EnvType.SERVER) {
            val it = gameMethod.instructions.iterator()

            if (!is20w22aServerOrHigher) {
                // Server-side: first argument (or null!) is runDirectory, run at end of init
                moveBefore(it, Opcodes.RETURN)

                // runDirectory
                if (serverHasFile) {
                    it.add(VarInsnNode(Opcodes.ALOAD, 1))
                } else {
                    it.add(InsnNode(Opcodes.ACONST_NULL))
                }

                it.add(VarInsnNode(Opcodes.ALOAD, 0))

                finishEntrypoint(type, it)
                patched = true
            } else {
                // Server-side: Run before `server.properties` is loaded so early logic like world generation is not broken due to being loaded by server properties before mods are initialized.
                // ----------------
                // ldc "server.properties"
                // iconst_0
                // anewarray java/lang/String
                // invokestatic java/nio/file/Paths.get (Ljava/lang/String;[Ljava/lang/String;)Ljava/nio/file/Path;
                // ----------------
                debug(LogCategory.GAME_PATCH, "20w22a+ detected, patching main method...")

                // Find the "server.properties".
                val serverPropertiesLdc = findInsn(
                    gameMethod,
                    Predicate { insn: AbstractInsnNode? -> insn is LdcInsnNode && insn.cst == "server.properties" },
                    false
                ) as LdcInsnNode?

                // Move before the `server.properties` ldc is pushed onto stack
                moveBefore(it, serverPropertiesLdc!!)

                // Detect if we are running exactly 20w22a.
                // Find the synthetic method where dedicated server instance is created so we can set the game instance.
                // This cannot be the main method, must be static (all methods are static, so useless to check)
                // Cannot return a void or boolean
                // Is only method that returns a class instance
                // If we do not find this, then we are certain this is 20w22a.
                val serverStartMethod = findMethod(
                    mainClass
                ) { method: MethodNode? ->
                    if ((method!!.access and Opcodes.ACC_SYNTHETIC) == 0 // reject non-synthetic
                        || method.name == "main" && method.desc == "([Ljava/lang/String;)V"
                    ) { // reject main method (theoretically superfluous now)
                        return@findMethod false
                    }
                    val methodReturnType =
                        Type.getReturnType(method.desc)
                    methodReturnType.sort != Type.BOOLEAN && methodReturnType.sort != Type.VOID && methodReturnType.sort == Type.OBJECT
                }

                if (serverStartMethod == null) {
                    // We are running 20w22a, this dependencyHandler a separate process for capturing game instance
                    debug(LogCategory.GAME_PATCH, "Detected 20w22a")
                } else {
                    debug(LogCategory.GAME_PATCH, "Detected version above 20w22a")

                    // We are not running 20w22a.
                    // This means we need to position ourselves before any dynamic registries are initialized.
                    // Since it is a bit hard to figure out if we are on most 1.16-pre1+ versions.
                    // So if the version is below 1.16.2-pre2, this injection will be before the timer thread hack. This should have no adverse effects.

                    // This diagram shows the intended result for 1.16.2-pre2
                    // ----------------
                    // invokestatic ... Bootstrap log missing
                    // <---- target here (1.16-pre1 to 1.16.2-pre1)
                    // ...misc
                    // invokestatic ... (Timer Thread Hack)
                    // <---- target here (1.16.2-pre2+)
                    // ... misc
                    // invokestatic ... (Registry Manager) [Only present in 1.16.2-pre2+]
                    // ldc "server.properties"
                    // ----------------

                    // The invokestatic insn we want is just before the ldc
                    var previous = serverPropertiesLdc.previous

                    while (true) {
                        if (previous == null) {
                            throw RuntimeException("Failed to find static method before loading server properties")
                        }

                        if (previous.opcode == Opcodes.INVOKESTATIC) {
                            break
                        }

                        previous = previous.previous
                    }

                    var foundNode = false

                    // Move the iterator back till we are just before the insn node we wanted
                    while (it.hasPrevious()) {
                        if (it.previous() === previous) {
                            if (it.hasPrevious()) {
                                foundNode = true
                                // Move just before the method insn node
                                it.previous()
                            }

                            break
                        }
                    }

                    if (!foundNode) {
                        throw RuntimeException("Failed to find static method before loading server properties")
                    }
                }

                it.add(InsnNode(Opcodes.ACONST_NULL))

                // Pass null for now, we will set the game instance when the dedicated server is created.
                it.add(InsnNode(Opcodes.ACONST_NULL))

                finishEntrypoint(type, it) // Inject the hook entrypoint.

                // Time to find the dedicated server ctor to capture game instance
                if (serverStartMethod == null) {
                    // FIXME: For 20w22a, find the only constructor in the game method that takes a DataFixer.
                    // That is the guaranteed to be dedicated server constructor
                    debug(LogCategory.GAME_PATCH, "Server game instance has not be implemented yet for 20w22a")
                } else {
                    val serverStartIt = serverStartMethod.instructions.iterator()

                    // 1.16-pre1+ Find the only constructor which takes a Thread as it's first parameter
                    val dedicatedServerConstructor = findInsn(
                        serverStartMethod,
                        { insn: AbstractInsnNode? ->
                            if (insn is MethodInsnNode && insn.name == "<init>") {
                                val constructorType =
                                    Type.getMethodType(insn.desc)

                                if (constructorType.argumentTypes.size <= 0) {
                                    return@findInsn false
                                }

                                return@findInsn constructorType.argumentTypes[0].descriptor == "Ljava/lang/Thread;"
                            }
                            false
                        }, false
                    ) as MethodInsnNode?

                    if (dedicatedServerConstructor == null) {
                        throw RuntimeException("Could not find dedicated server constructor")
                    }

                    // Jump after the <init> call
                    moveAfter(serverStartIt, dedicatedServerConstructor)

                    // Duplicate dedicated server instance for loader
                    serverStartIt.add(InsnNode(Opcodes.DUP))
                    serverStartIt.add(
                        MethodInsnNode(
                            Opcodes.INVOKESTATIC,
                            Hooks.INTERNAL_NAME,
                            "setGameInstance",
                            "(Ljava/lang/Object;)V",
                            false
                        )
                    )
                }

                patched = true
            }
        } else if (type == EnvType.CLIENT && isApplet) {
            // Applet-side: field is private static File, run at end
            // At the beginning, set file field (hook)
            val runDirectory = findField(
                gameClass
            ) { f: FieldNode? ->
                isStatic(
                    f!!.access
                ) && f.desc == "Ljava/io/File;"
            }

            if (runDirectory == null) {
                // TODO: Handle pre-indev versions.
                //
                // Classic has no agreed-upon run directory.
                // - level.dat is always stored in CWD. We can assume CWD is set, launchers generally adhere to that.
                // - options.txt in newer Classic versions is stored in user.home/.minecraft/. This is not currently handled,
                // but as these versions are relatively low on options this is not a huge concern.
                warn(
                    LogCategory.GAME_PATCH,
                    "Could not find applet run directory! (If you're running pre-late-indev versions, this is fine.)"
                )

                val it = gameMethod.instructions.iterator()

                if (gameConstructor === gameMethod) {
                    moveBefore(it, Opcodes.RETURN)
                }

                /*it.add(new TypeInsnNode(Opcodes.NEW, "java/io/File"));
					it.add(new InsnNode(Opcodes.DUP));
					it.add(new LdcInsnNode("."));
					it.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, "java/io/File", "<init>", "(Ljava/lang/String;)V", false)); */
                it.add(InsnNode(Opcodes.ACONST_NULL))
                it.add(
                    MethodInsnNode(
                        Opcodes.INVOKESTATIC,
                        "net/fabricmc/loader/impl/game/minecraft/applet/AppletMain",
                        "hookGameDir",
                        "(Ljava/io/File;)Ljava/io/File;",
                        false
                    )
                )
                it.add(VarInsnNode(Opcodes.ALOAD, 0))
                finishEntrypoint(type, it)
            } else {
                // Indev and above.
                var it = gameConstructor!!.instructions.iterator()
                moveAfter(it, Opcodes.INVOKESPECIAL) /* Object.init */
                it.add(FieldInsnNode(Opcodes.GETSTATIC, gameClass.name, runDirectory.name, runDirectory.desc))
                it.add(
                    MethodInsnNode(
                        Opcodes.INVOKESTATIC,
                        "net/fabricmc/loader/impl/game/minecraft/applet/AppletMain",
                        "hookGameDir",
                        "(Ljava/io/File;)Ljava/io/File;",
                        false
                    )
                )
                it.add(FieldInsnNode(Opcodes.PUTSTATIC, gameClass.name, runDirectory.name, runDirectory.desc))

                it = gameMethod.instructions.iterator()

                if (gameConstructor === gameMethod) {
                    moveBefore(it, Opcodes.RETURN)
                }

                it.add(FieldInsnNode(Opcodes.GETSTATIC, gameClass.name, runDirectory.name, runDirectory.desc))
                it.add(VarInsnNode(Opcodes.ALOAD, 0))
                finishEntrypoint(type, it)
            }

            patched = true
        } else {
            // Client-side:
            // - if constructor, identify runDirectory field + location, run immediately after
            // - if non-constructor (init method), head

            if (gameConstructor == null) {
                throw RuntimeException("Non-applet client-side, but could not find constructor?")
            }

            val consIt = gameConstructor.instructions.iterator()

            while (consIt.hasNext()) {
                val insn = consIt.next()
                if (insn.opcode == Opcodes.PUTFIELD
                    && (insn as FieldInsnNode).desc == "Ljava/io/File;"
                ) {
                    debug(LogCategory.GAME_PATCH, "Run directory field is thought to be %s/%s", insn.owner, insn.name)

                    val it = if (gameMethod === gameConstructor) {
                        consIt
                    } else {
                        gameMethod.instructions.iterator()
                    }

                    // Add the hook just before the Thread.currentThread() call for 1.19.4 or later
                    // If older 4 method insn's before the lwjgl log
                    if (currentThreadNode != null && VERSION_1_19_4.test(gameVersion)) {
                        moveBefore(it, currentThreadNode)
                    } else if (lwjglLogNode != null) {
                        moveBefore(it, lwjglLogNode)

                        for (i in 0..3) {
                            moveBeforeType(it, AbstractInsnNode.METHOD_INSN)
                        }
                    }

                    it.add(VarInsnNode(Opcodes.ALOAD, 0))
                    it.add(FieldInsnNode(Opcodes.GETFIELD, insn.owner, insn.name, insn.desc))
                    it.add(VarInsnNode(Opcodes.ALOAD, 0))
                    finishEntrypoint(type, it)

                    patched = true
                    break
                }
            }
        }

        if (!patched) {
            throw RuntimeException("Game constructor patch not applied!")
        }

        if (gameClass !== mainClass) {
            classEmitter!!.accept(gameClass)
        } else {
            classEmitter!!.accept(mainClass)
        }

        if (isApplet) {
            Hooks.appletMainClass = entrypoint
        }
    }

    private fun hasSuperClass(cls: String, superCls: String, classSource: Function<String?, ClassNode?>): Boolean {
        if (cls.contains("$") || (!cls.startsWith("net/minecraft") && cls.contains("/"))) {
            return false
        }

        val classNode = classSource.apply(cls)

        return classNode != null && classNode.superName == superCls
    }

    private fun hasStrInMethod(
        cls: String,
        methodName: String,
        methodDesc: String,
        str: String,
        classSource: Function<String?, ClassNode?>
    ): Boolean {
        if (cls.contains("$") || (!cls.startsWith("net/minecraft") && cls.contains("/"))) {
            return false
        }

        val node = classSource.apply(cls) ?: return false

        for (method in node.methods) {
            if (method.name == methodName && method.desc == methodDesc) {
                for (insn in method.instructions) {
                    if (insn is LdcInsnNode) {
                        val cst = insn.cst

                        if (cst is String) {
                            if (cst == str) {
                                return true
                            }
                        }
                    }
                }

                break
            }
        }

        return false
    }

    private val gameVersion: Version
        get() {
            try {
                return Version.parse(gameProvider.getNormalizedGameVersion())
            } catch (e: VersionParsingException) {
                throw RuntimeException(e)
            }
        }

    companion object {
        private val VERSION_1_19_4 = createVersionPredicate(">=1.19.4-")

        private fun createVersionPredicate(predicate: String): VersionPredicate {
            try {
                return parse(predicate)
            } catch (e: VersionParsingException) {
                throw RuntimeException(e)
            }
        }
    }
}
