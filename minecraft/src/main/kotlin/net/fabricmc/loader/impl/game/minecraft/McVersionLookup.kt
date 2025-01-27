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
package net.fabricmc.loader.impl.game.minecraft

import com.moltenex.loader.impl.MoltenexLoaderImpl
import net.fabricmc.loader.api.VersionParsingException
import com.beust.klaxon.Klaxon
import com.beust.klaxon.JsonObject
import java.io.InputStream
import java.io.InputStreamReader
import net.fabricmc.loader.impl.util.ExceptionUtil.wrap
import net.fabricmc.loader.impl.util.LoaderUtil.getClassFileName
import net.fabricmc.loader.impl.util.SimpleClassPath
import net.fabricmc.loader.impl.util.version.SemanticVersionImpl
import net.fabricmc.loader.impl.util.version.VersionPredicateParser.parse
import org.objectweb.asm.*
import java.io.DataInputStream
import java.io.IOException
import java.nio.file.Path
import java.util.regex.Matcher
import java.util.regex.Pattern

object McVersionLookup {
    private val VERSION_PATTERN: Pattern = Pattern.compile(
        ("0\\.\\d+(\\.\\d+)?a?(_\\d+)?|" // match classic versions first: 0.1.2a_34
                + "\\d+\\.\\d+(\\.\\d+)?(-pre\\d+| Pre-[Rr]elease \\d+)?|" // modern non-snapshot: 1.2, 1.2.3, optional -preN or " Pre-Release N" suffix
                + "\\d+\\.\\d+(\\.\\d+)?(-rc\\d+| [Rr]elease Candidate \\d+)?|" // 1.16+ Release Candidate
                + "\\d+w\\d+[a-z]|" // modern snapshot: 12w34a
                + "[a-c]\\d\\.\\d+(\\.\\d+)?[a-z]?(_\\d+)?[a-z]?|" // alpha/beta a1.2.3_45
                + "(Alpha|Beta) v?\\d+\\.\\d+(\\.\\d+)?[a-z]?(_\\d+)?[a-z]?|" // long alpha/beta names: Alpha v1.2.3_45
                + "Inf?dev (0\\.31 )?\\d+(-\\d+)?|" // long indev/infdev names: Infdev 12345678-9
                + "(rd|inf?)-\\d+|" // early rd-123, in-20100223, inf-123
                + "1\\.RV-Pre1|3D Shareware v1\\.34|23w13a_or_b|24w14potato|" // odd exceptions
                + "(.*[Ee]xperimental [Ss]napshot )(\\d+)") // Experimental versions.
    )
    private val RELEASE_PATTERN: Pattern = Pattern.compile("\\d+\\.\\d+(\\.\\d+)?")
    private val PRE_RELEASE_PATTERN: Pattern = Pattern.compile(".+(?:-pre| Pre-[Rr]elease )(\\d+)")
    private val RELEASE_CANDIDATE_PATTERN: Pattern = Pattern.compile(".+(?:-rc| [Rr]elease Candidate )(\\d+)")
    private val SNAPSHOT_PATTERN: Pattern = Pattern.compile("(?:Snapshot )?(\\d+)w0?(0|[1-9]\\d*)([a-z])")
    private val EXPERIMENTAL_PATTERN: Pattern = Pattern.compile("(?:.*[Ee]xperimental [Ss]napshot )(\\d+)")
    private val BETA_PATTERN: Pattern = Pattern.compile("(?:b|Beta v?)1\\.(\\d+(\\.\\d+)?[a-z]?(_\\d+)?[a-z]?)")
    private val ALPHA_PATTERN: Pattern = Pattern.compile("(?:a|Alpha v?)[01]\\.(\\d+(\\.\\d+)?[a-z]?(_\\d+)?[a-z]?)")
    private val INDEV_PATTERN: Pattern = Pattern.compile("(?:inf?-|Inf?dev )(?:0\\.31 )?(\\d+(-\\d+)?)")
    private const val STRING_DESC = "Ljava/lang/String;"

    fun getVersion(gameJars: List<Path?>, entrypointClass: String?, versionName: String?): McVersion {
        val builder = McVersion.Builder()

        if (versionName != null) {
            builder.setNameAndRelease(versionName)
        }

        try {
            SimpleClassPath(gameJars).use { cp ->
                // Determine class version
                if (entrypointClass != null) {
                    cp.getInputStream(getClassFileName(entrypointClass)).use { `is` ->
                        val dis = DataInputStream(`is`!!)
                        if (dis.readInt() == -0x35014542) {
                            dis.readUnsignedShort()
                            builder.setClassVersion(dis.readUnsignedShort())
                        }
                    }
                }

                // Check various known files for version information if unknown
                if (versionName == null) {
                    fillVersionFromJar(cp, builder)
                }
            }
        } catch (e: IOException) {
            throw wrap(e)
        }

        return builder.build()
    }

    @JvmStatic
	fun getVersionExceptClassVersion(gameJar: Path): McVersion {
        val builder = McVersion.Builder()

        try {
            SimpleClassPath(listOf(gameJar)).use { cp ->
                fillVersionFromJar(cp, builder)
            }
        } catch (e: IOException) {
            throw wrap(e)
        }

        return builder.build()
    }

    fun fillVersionFromJar(cp: SimpleClassPath, builder: McVersion.Builder) {
        try {
            var `is`: InputStream?

            // version.json - contains version and target release for 18w47b+
            if ((cp.getInputStream("version.json").also { `is` = it }) != null && fromVersionJson(`is`!!, builder)) {
                return
            }

            // constant field RealmsSharedConstants.VERSION_STRING
            if ((cp.getInputStream("net/minecraft/realms/RealmsSharedConstants.class")
                    .also { `is` = it }) != null && fromAnalyzer(
                    `is`!!, FieldStringConstantVisitor("VERSION_STRING"), builder
                )
            ) {
                return
            }

            // constant return value of RealmsBridge.getVersionString (presumably inlined+dead code eliminated VERSION_STRING)
            if ((cp.getInputStream("net/minecraft/realms/RealmsBridge.class")
                    .also { `is` = it }) != null && fromAnalyzer(
                    `is`!!, MethodConstantRetVisitor("getVersionString"), builder
                )
            ) {
                return
            }

            // version-like String constant used in MinecraftServer.run or another MinecraftServer method
            if ((cp.getInputStream("net/minecraft/server/MinecraftServer.class")
                    .also { `is` = it }) != null && fromAnalyzer(
                    `is`!!, MethodConstantVisitor("run"), builder
                )
            ) {
                return
            }

            val entry = cp.getEntry("net/minecraft/client/Minecraft.class")

            if (entry != null) {
                // version-like constant return value of a Minecraft method (obfuscated/unknown name)
                if (fromAnalyzer(entry.inputStream, MethodConstantRetVisitor(null), builder)) {
                    return
                }

                // version-like constant passed into Display.setTitle in a Minecraft method (obfuscated/unknown name)
                if (fromAnalyzer(
                        entry.inputStream,
                        MethodStringConstantContainsVisitor("org/lwjgl/opengl/Display", "setTitle"),
                        builder
                    )
                ) {
                    return
                }
            }

            // classic: version-like String constant used in Minecraft.init, Minecraft referenced by field in MinecraftApplet
            var type: String? = ""

            if (((cp.getInputStream("net/minecraft/client/MinecraftApplet.class")
                    .also { `is` = it }) != null || (cp.getInputStream("com/mojang/minecraft/MinecraftApplet.class")
                    .also { `is` = it }) != null)
                && (analyze(`is`!!, FieldTypeCaptureVisitor()).also { type = it }) != null && (cp.getInputStream(
                    "$type.class"
                ).also { `is` = it }) != null && fromAnalyzer(
                    `is`!!, MethodConstantVisitor("init"), builder
                )
            ) {
                return
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }

        builder.setFromFileName(cp.paths[0]?.fileName.toString())
    }

    private fun fromVersionJson(`is`: InputStream, builder: McVersion.Builder): Boolean {
        try {
            // Using Klaxon to parse the JSON into a JsonObject
            val json = Klaxon().parse<JsonObject>(InputStreamReader(`is`))

            // Check if JSON was parsed correctly
            if (json == null) return false

            val id = json.string("id")
            val name = json.string("name")
            val release = json.string("release_target")

            // Handle the logic for version assignment
            val version = if (name == null || (id != null && id.length < name.length)) {
                id
            } else {
                name
            }

            if (version == null) return false

            // Populate the builder
            builder.setId(id)
            builder.setName(name)

            if (release == null) {
                builder.setNameAndRelease(version)
            } else {
                builder.setVersion(version)
                builder.setRelease(release)
            }
            return true
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return false
    }


    private fun <T> fromAnalyzer(
        `is`: InputStream,
        analyzer: T,
        builder: McVersion.Builder
    ): Boolean where T : ClassVisitor?, T : Analyzer? {
        val result = analyze(`is`, analyzer)

        if (result != null) {
            builder.setNameAndRelease(result)
            return true
        } else {
            return false
        }
    }

    private fun <T> analyze(`is`: InputStream, analyzer: T): String? where T : ClassVisitor?, T : Analyzer? {
        try {
            val cr = ClassReader(`is`)
            cr.accept(analyzer, ClassReader.SKIP_FRAMES or ClassReader.SKIP_DEBUG)

            return analyzer!!.result
        } catch (e: IOException) {
            e.printStackTrace()
        } finally {
            try {
                `is`.close()
            } catch (e: IOException) {
                // ignored
            }
        }

        return null
    }

    @JvmStatic
	fun getRelease(version: String): String? {
        if (RELEASE_PATTERN.matcher(version).matches()) return version

        assert(isProbableVersion(version))

        var pos = version.indexOf("-pre")
        if (pos >= 0) return version.substring(0, pos)

        pos = version.indexOf(" Pre-Release ")
        if (pos >= 0) return version.substring(0, pos)

        pos = version.indexOf(" Pre-release ")
        if (pos >= 0) return version.substring(0, pos)

        pos = version.indexOf(" Release Candidate ")
        if (pos >= 0) return version.substring(0, pos)

        val matcher = SNAPSHOT_PATTERN.matcher(version)

        if (matcher.matches()) {
            val year = matcher.group(1).toInt()
            val week = matcher.group(2).toInt()

            if (year == 24 && week >= 44 || year >= 25) {
                return "1.21.4"
            } else if (year == 24 && week >= 33 && week <= 40) {
                return "1.21.2"
            } else if (year == 24 && week >= 18 && week <= 21) {
                return "1.21"
            } else if (year == 23 && week >= 51 || year == 24 && week <= 14) {
                return "1.20.5"
            } else if (year == 23 && week >= 40 && week <= 46) {
                return "1.20.3"
            } else if (year == 23 && week >= 31 && week <= 35) {
                return "1.20.2"
            } else if (year == 23 && week >= 12 && week <= 18) {
                return "1.20"
            } else if (year == 23 && week <= 7) {
                return "1.19.4"
            } else if (year == 22 && week >= 42) {
                return "1.19.3"
            } else if (year == 22 && week == 24) {
                return "1.19.1"
            } else if (year == 22 && week >= 11 && week <= 19) {
                return "1.19"
            } else if (year == 22 && week >= 3 && week <= 7) {
                return "1.18.2"
            } else if (year == 21 && week >= 37 && week <= 44) {
                return "1.18"
            } else if (year == 20 && week >= 45 || year == 21 && week <= 20) {
                return "1.17"
            } else if (year == 20 && week >= 27 && week <= 30) {
                return "1.16.2"
            } else if (year == 20 && week >= 6 && week <= 22) {
                return "1.16"
            } else if (year == 19 && week >= 34) {
                return "1.15"
            } else if (year == 18 && week >= 43 || year == 19 && week <= 14) {
                return "1.14"
            } else if (year == 18 && week >= 30 && week <= 33) {
                return "1.13.1"
            } else if (year == 17 && week >= 43 || year == 18 && week <= 22) {
                return "1.13"
            } else if (year == 17 && week == 31) {
                return "1.12.1"
            } else if (year == 17 && week >= 6 && week <= 18) {
                return "1.12"
            } else if (year == 16 && week == 50) {
                return "1.11.1"
            } else if (year == 16 && week >= 32 && week <= 44) {
                return "1.11"
            } else if (year == 16 && week >= 20 && week <= 21) {
                return "1.10"
            } else if (year == 16 && week >= 14 && week <= 15) {
                return "1.9.3"
            } else if (year == 15 && week >= 31 || year == 16 && week <= 7) {
                return "1.9"
            } else if (year == 14 && week >= 2 && week <= 34) {
                return "1.8"
            } else if (year == 13 && week >= 47 && week <= 49) {
                return "1.7.3"
            } else if (year == 13 && week >= 36 && week <= 43) {
                return "1.7"
            } else if (year == 13 && week >= 16 && week <= 26) {
                return "1.6"
            } else if (year == 13 && week >= 11 && week <= 12) {
                return "1.5.1"
            } else if (year == 13 && week >= 1 && week <= 10) {
                return "1.5"
            } else if (year == 12 && week >= 49 && week <= 50) {
                return "1.4.6"
            } else if (year == 12 && week >= 32 && week <= 42) {
                return "1.4"
            } else if (year == 12 && week >= 15 && week <= 30) {
                return "1.3"
            } else if (year == 12 && week >= 3 && week <= 8) {
                return "1.2"
            } else if (year == 11 && week >= 47 || year == 12 && week <= 1) {
                return "1.1"
            }
        }

        return null
    }

    private fun isProbableVersion(str: String): Boolean {
        return VERSION_PATTERN.matcher(str).matches()
    }

    /**
     * Returns the probable version contained in the given string, or null if the string doesn't contain a version.
     */
    private fun findProbableVersion(str: String): String? {
        val matcher = VERSION_PATTERN.matcher(str)

        return if (matcher.find()) {
            matcher.group()
        } else {
            null
        }
    }

    /**
     * Convert an arbitrary MC version into semver-like release-preRelease form.
     *
     *
     * MC Snapshot -> alpha, MC Pre-Release -> rc.
     */
	@JvmStatic
	fun normalizeVersion(name: String, release: String?): String {
        var name = name
        if (release == null || name == release) {
            val ret = normalizeSpecialVersion(name)
            return ret ?: normalizeVersion(name)
        }

        var matcher: Matcher

        if ((EXPERIMENTAL_PATTERN.matcher(name).also { matcher = it }).matches()) {
            return String.format("%s-Experimental.%s", release, matcher.group(1))
        } else if (name.startsWith(release)) {
            matcher = RELEASE_CANDIDATE_PATTERN.matcher(name)

            if (matcher.matches()) {
                var rcBuild = matcher.group(1)

                // This is a hack to fake 1.16's new release candidates to follow on from the 8 pre releases.
                if (release == "1.16") {
                    val build = rcBuild.toInt()
                    rcBuild = (8 + build).toString()
                }

                name = String.format("rc.%s", rcBuild)
            } else {
                matcher = PRE_RELEASE_PATTERN.matcher(name)

                if (matcher.matches()) {
                    val legacyVersion: Boolean

                    try {
                        legacyVersion = parse("<=1.16").test(SemanticVersionImpl(release, false))
                    } catch (e: VersionParsingException) {
                        throw RuntimeException("Failed to parse version: $release")
                    }

                    // Mark pre-releases as 'beta' versions, except for version 1.16 and before, where they are 'rc'
                    name = if (legacyVersion) {
                        String.format("rc.%s", matcher.group(1))
                    } else {
                        String.format("beta.%s", matcher.group(1))
                    }
                } else {
                    val ret = normalizeSpecialVersion(name)
                    if (ret != null) return ret
                }
            }
        } else if ((SNAPSHOT_PATTERN.matcher(name).also { matcher = it }).matches()) {
            name = String.format("alpha.%s.%s.%s", matcher.group(1), matcher.group(2), matcher.group(3))
        } else {
            // Try short-circuiting special versions which are complete on their own
            val ret = normalizeSpecialVersion(name)
            if (ret != null) return ret

            name = normalizeVersion(name)
        }

        return String.format("%s-%s", release, name)
    }

    private fun normalizeVersion(version: String): String {
        // old version normalization scheme
        // do this before the main part of normalization as we can get crazy strings like "Indev 0.31 12345678-9"
        var version = version
        var matcher: Matcher

        if ((BETA_PATTERN.matcher(version).also { matcher = it }).matches()) { // beta 1.2.3: 1.0.0-beta.2.3
            version = "1.0.0-beta." + matcher.group(1)
        } else if ((ALPHA_PATTERN.matcher(version).also { matcher = it }).matches()) { // alpha 1.2.3: 1.0.0-alpha.2.3
            version = "1.0.0-alpha." + matcher.group(1)
        } else if ((INDEV_PATTERN.matcher(version)
                .also { matcher = it }).matches()
        ) { // indev/infdev 12345678: 0.31.12345678
            version = "0.31." + matcher.group(1)
        } else if (version.startsWith("c0.")) { // classic: unchanged, except remove prefix
            version = version.substring(1)
        } else if (version.startsWith("rd-")) { // pre-classic
            version = version.substring("rd-".length)
            if ("20090515" == version) version =
                "150000" // account for a weird exception to the pre-classic versioning scheme

            version = "0.0.0-rd.$version"
        }

        val ret = StringBuilder(version.length + 5)
        var lastIsDigit = false
        var lastIsLeadingZero = false
        var lastIsSeparator = false

        var i = 0
        val max = version.length
        while (i < max) {
            var c = version[i]

            if (c >= '0' && c <= '9') {
                if (i > 0 && !lastIsDigit && !lastIsSeparator) { // no separator between non-number and number, add one
                    ret.append('.')
                } else if (lastIsDigit && lastIsLeadingZero) { // leading zero in output -> strip
                    ret.setLength(ret.length - 1)
                }

                lastIsLeadingZero =
                    c == '0' && (!lastIsDigit || lastIsLeadingZero) // leading or continued leading zero(es)
                lastIsSeparator = false
                lastIsDigit = true
            } else if (c == '.' || c == '-') { // keep . and - separators
                if (lastIsSeparator) {
                    i++
                    continue
                }

                lastIsSeparator = true
                lastIsDigit = false
            } else if ((c < 'A' || c > 'Z') && (c < 'a' || c > 'z')) { // replace remaining non-alphanumeric with .
                if (lastIsSeparator) {
                    i++
                    continue
                }

                c = '.'
                lastIsSeparator = true
                lastIsDigit = false
            } else { // keep other characters (alpha)
                if (lastIsDigit) ret.append('.') // no separator between number and non-number, add one


                lastIsSeparator = false
                lastIsDigit = false
            }

            ret.append(c)
            i++
        }

        // strip leading and trailing .
        var start = 0
        while (start < ret.length && ret[start] == '.') start++

        var end = ret.length
        while (end > start && ret[end - 1] == '.') end--

        return ret.substring(start, end)
    }

    private fun normalizeSpecialVersion(version: String): String? {
        return when (version) {
            "13w12~" ->            // A pair of debug snapshots immediately before 1.5.1-pre
                "1.5.1-alpha.13.12.a"

            "15w14a" ->            // The Love and Hugs Update, forked from 1.8.3
                "1.8.4-alpha.15.14.a+loveandhugs"

            "1.RV-Pre1" ->            // The Trendy Update, probably forked from 1.9.2 (although the protocol/data versions immediately follow 1.9.1-pre3)
                "1.9.2-rv+trendy"

            "3D Shareware v1.34" ->            // Minecraft 3D, forked from 19w13b
                "1.14-alpha.19.13.shareware"

            "20w14~" ->            // The Ultimate Content update, forked from 20w13b
                "1.16-alpha.20.13.inf" // Not to be confused with the actual 20w14a

            "1.14.3 - Combat Test" ->            // The first Combat Test, forked from 1.14.3 Pre-Release 4
                "1.14.3-rc.4.combat.1"

            "Combat Test 2" ->            // The second Combat Test, forked from 1.14.4
                "1.14.5-combat.2"

            "Combat Test 3" ->            // The third Combat Test, forked from 1.14.4
                "1.14.5-combat.3"

            "Combat Test 4" ->            // The fourth Combat Test, forked from 1.15 Pre-release 3
                "1.15-rc.3.combat.4"

            "Combat Test 5" ->            // The fifth Combat Test, forked from 1.15.2 Pre-release 2
                "1.15.2-rc.2.combat.5"

            "Combat Test 6" ->            // The sixth Combat Test, forked from 1.16.2 Pre-release 3
                "1.16.2-beta.3.combat.6"

            "Combat Test 7" ->            // Private testing Combat Test 7, forked from 1.16.2
                "1.16.3-combat.7"

            "1.16_combat-2" ->            // Private testing Combat Test 7b, forked from 1.16.2
                "1.16.3-combat.7.b"

            "1.16_combat-3" ->            // The seventh Combat Test 7c, forked from 1.16.2
                "1.16.3-combat.7.c"

            "1.16_combat-4" ->            // Private testing Combat Test 8(a?), forked from 1.16.2
                "1.16.3-combat.8"

            "1.16_combat-5" ->            // The eighth Combat Test 8b, forked from 1.16.2
                "1.16.3-combat.8.b"

            "1.16_combat-6" ->            // The ninth Combat Test 8c, forked from 1.16.2
                "1.16.3-combat.8.c"

            "2point0_red" ->            // 2.0 update version red, forked from 1.5.1
                "1.5.2-red"

            "2point0_purple" ->            // 2.0 update version purple, forked from 1.5.1
                "1.5.2-purple"

            "2point0_blue" ->            // 2.0 update version blue, forked from 1.5.1
                "1.5.2-blue"

            "23w13a_or_b" ->            // Minecraft 23w13a_or_b, forked from 23w13a
                "1.20-alpha.23.13.ab"

            "24w14potato" ->            // Minecraft 24w14potato, forked from 24w12a
                "1.20.5-alpha.24.12.potato"

            else -> null //Don't recognise the version
        }
    }

    private interface Analyzer {
        val result: String?
    }

    private class FieldStringConstantVisitor(private val fieldName: String) :
        ClassVisitor(MoltenexLoaderImpl.ASM_VERSION),
        Analyzer {
        private var className: String? = null
        override var result: String? = null
            private set

        override fun visit(
            version: Int,
            access: Int,
            name: String,
            signature: String,
            superName: String,
            interfaces: Array<String>
        ) {
            this.className = name
        }

        override fun visitField(
            access: Int,
            name: String,
            descriptor: String,
            signature: String,
            value: Any
        ): FieldVisitor? {
            if (result == null && name == fieldName && descriptor == STRING_DESC && value is String) {
                result = value
            }

            return null
        }

        override fun visitMethod(
            access: Int,
            name: String,
            descriptor: String,
            signature: String,
            exceptions: Array<String>
        ): MethodVisitor? {
            if (result != null || name != "<clinit>") return null

            // capture LDC ".." followed by PUTSTATIC this.fieldName
            return object : InsnFwdMethodVisitor() {
                override fun visitLdcInsn(value: Any) {
                    var str: String = ""

                    if (value is String && isProbableVersion(value.also { str = it })) {
                        lastLdc = str
                    } else {
                        lastLdc = null
                    }
                }

                override fun visitFieldInsn(opcode: Int, owner: String, name: String, descriptor: String) {
                    if (result == null && lastLdc != null && opcode == Opcodes.PUTSTATIC && owner == className
                        && name == fieldName
                        && descriptor == STRING_DESC
                    ) {
                        result = lastLdc
                    }

                    lastLdc = null
                }

                override fun visitAnyInsn() {
                    lastLdc = null
                }

                var lastLdc: String? = null
            }
        }
    }

    private class MethodStringConstantContainsVisitor(private val methodOwner: String, private val methodName: String) :
        ClassVisitor(MoltenexLoaderImpl.ASM_VERSION),
        Analyzer {
        override var result: String? = null
            private set

        override fun visitMethod(
            access: Int,
            name: String,
            descriptor: String,
            signature: String,
            exceptions: Array<String>
        ): MethodVisitor? {
            if (result != null) return null

            // capture LDC ".." followed by INVOKE methodOwner.methodName
            return object : InsnFwdMethodVisitor() {
                override fun visitLdcInsn(value: Any) {
                    lastLdc = if (value is String) {
                        findProbableVersion(value)
                    } else {
                        null
                    }
                }

                override fun visitMethodInsn(
                    opcode: Int,
                    owner: String,
                    name: String,
                    descriptor: String,
                    itf: Boolean
                ) {
                    if (result == null && lastLdc != null && owner == methodOwner
                        && name == methodName
                        && descriptor.startsWith("(" + STRING_DESC + ")")
                    ) {
                        result = lastLdc
                    }

                    lastLdc = null
                }

                override fun visitAnyInsn() {
                    lastLdc = null
                }

                var lastLdc: String? = null
            }
        }
    }

    private class MethodConstantRetVisitor(private val methodName: String?) :
        ClassVisitor(MoltenexLoaderImpl.ASM_VERSION),
        Analyzer {
        override var result: String? = null
            private set

        override fun visitMethod(
            access: Int,
            name: String,
            descriptor: String,
            signature: String,
            exceptions: Array<String>
        ): MethodVisitor? {
            if (result != null || methodName != null && name != methodName || !descriptor.endsWith(STRING_DESC) || descriptor[descriptor.length - STRING_DESC.length - 1] != ')') {
                return null
            }

            // capture LDC ".." followed by ARETURN
            return object : InsnFwdMethodVisitor() {
                override fun visitLdcInsn(value: Any) {
                    var str: String = ""

                    if (value is String && isProbableVersion(value.also { str = it })) {
                        lastLdc = str
                    } else {
                        lastLdc = null
                    }
                }

                override fun visitInsn(opcode: Int) {
                    if (result == null && lastLdc != null && opcode == Opcodes.ARETURN) {
                        result = lastLdc
                    }

                    lastLdc = null
                }

                override fun visitAnyInsn() {
                    lastLdc = null
                }

                var lastLdc: String? = null
            }
        }
    }

    private class MethodConstantVisitor(private val methodNameHint: String) :
        ClassVisitor(MoltenexLoaderImpl.ASM_VERSION),
        Analyzer {
        override var result: String? = null
            private set
        private var foundInMethodHint = false

        override fun visitMethod(
            access: Int,
            name: String,
            descriptor: String,
            signature: String,
            exceptions: Array<String>
        ): MethodVisitor? {
            val isRequestedMethod = name == methodNameHint

            if (result != null && !isRequestedMethod) {
                return null
            }

            return object : MethodVisitor(MoltenexLoaderImpl.ASM_VERSION) {
                override fun visitLdcInsn(value: Any) {
                    if ((result == null || !foundInMethodHint && isRequestedMethod) && value is String) {
                        var str = value

                        // a0.1.0 - 1.2.5 have a startup message including the version, extract it from there
                        // Examples:
                        //  release 1.0.0 - Starting minecraft server version 1.0.0
                        // 	beta 1.7.3 - Starting minecraft server version Beta 1.7.3
                        // 	alpha 0.2.8 - Starting minecraft server version 0.2.8
                        if (str.startsWith(STARTING_MESSAGE)) {
                            str = str.substring(STARTING_MESSAGE.length)

                            // Alpha servers don't have any prefix, but they all have 0 as the major
                            if (!str.startsWith("Beta") && str.startsWith("0.")) {
                                str = "Alpha $str"
                            }
                        } else if (str.startsWith(CLASSIC_PREFIX)) {
                            str = str.substring(CLASSIC_PREFIX.length)

                            if (str.startsWith(CLASSIC_PREFIX)) { // some beta versions repeat the Minecraft prefix
                                str = str.substring(CLASSIC_PREFIX.length)
                            }
                        }

                        // 1.0.0 - 1.13.2 have an obfuscated method that just returns the version, so we can use that
                        if (isProbableVersion(str)) {
                            result = str
                            foundInMethodHint = isRequestedMethod
                        }
                    }
                }
            }
        }

        companion object {
            private const val STARTING_MESSAGE = "Starting minecraft server version "
            private const val CLASSIC_PREFIX = "Minecraft "
        }
    }

    private abstract class InsnFwdMethodVisitor : MethodVisitor(MoltenexLoaderImpl.ASM_VERSION) {
        protected abstract fun visitAnyInsn()

        override fun visitLdcInsn(value: Any) {
            visitAnyInsn()
        }

        override fun visitFieldInsn(opcode: Int, owner: String, name: String, descriptor: String) {
            visitAnyInsn()
        }

        override fun visitInsn(opcode: Int) {
            visitAnyInsn()
        }

        override fun visitIntInsn(opcode: Int, operand: Int) {
            visitAnyInsn()
        }

        override fun visitVarInsn(opcode: Int, `var`: Int) {
            visitAnyInsn()
        }

        override fun visitTypeInsn(opcode: Int, type: String) {
            visitAnyInsn()
        }

        override fun visitMethodInsn(
            opcode: Int,
            owner: String,
            name: String,
            descriptor: String,
            isInterface: Boolean
        ) {
            visitAnyInsn()
        }

        override fun visitInvokeDynamicInsn(
            name: String,
            descriptor: String,
            bootstrapMethodHandle: Handle,
            vararg bootstrapMethodArguments: Any
        ) {
            visitAnyInsn()
        }

        override fun visitJumpInsn(opcode: Int, label: Label) {
            visitAnyInsn()
        }

        override fun visitIincInsn(`var`: Int, increment: Int) {
            visitAnyInsn()
        }

        override fun visitTableSwitchInsn(min: Int, max: Int, dflt: Label, vararg labels: Label) {
            visitAnyInsn()
        }

        override fun visitLookupSwitchInsn(dflt: Label, keys: IntArray, labels: Array<Label>) {
            visitAnyInsn()
        }

        override fun visitMultiANewArrayInsn(descriptor: String, numDimensions: Int) {
            visitAnyInsn()
        }
    }

    private class FieldTypeCaptureVisitor : ClassVisitor(MoltenexLoaderImpl.ASM_VERSION),
        Analyzer {
        override var result: String? = null
            private set

        override fun visitField(
            access: Int,
            name: String,
            descriptor: String,
            signature: String,
            value: Any
        ): FieldVisitor? {
            if (result == null && descriptor.startsWith("L") && !descriptor.startsWith("Ljava/")) {
                result = descriptor.substring(1, descriptor.length - 1)
            }

            return null
        }

        override fun visitMethod(
            access: Int,
            name: String,
            descriptor: String,
            signature: String,
            exceptions: Array<String>
        ): MethodVisitor? {
            return null
        }
    }
}
