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
package net.fabricmc.loader.impl.launch.knot

import com.moltenex.loader.impl.launch.MoltenexLauncherBase
import net.fabricmc.api.EnvType
import net.fabricmc.loader.impl.game.GameProvider
import net.fabricmc.loader.impl.launch.knot.KnotClassDelegate.ClassLoaderAccess
import net.fabricmc.loader.impl.transformer.FabricTransformer.transform
import net.fabricmc.loader.impl.util.ExceptionUtil.wrap
import net.fabricmc.loader.impl.util.FileSystemUtil.getJarFileSystem
import net.fabricmc.loader.impl.util.LoaderUtil.getClassFileName
import net.fabricmc.loader.impl.util.LoaderUtil.normalizeExistingPath
import net.fabricmc.loader.impl.util.ManifestUtil.readManifestFromBasePath
import net.fabricmc.loader.impl.util.SystemProperties
import net.fabricmc.loader.impl.util.UrlConversionException
import net.fabricmc.loader.impl.util.UrlUtil
import net.fabricmc.loader.impl.util.UrlUtil.asUrl
import net.fabricmc.loader.impl.util.log.Log.debug
import net.fabricmc.loader.impl.util.log.Log.info
import net.fabricmc.loader.impl.util.log.Log.warn
import net.fabricmc.loader.impl.util.log.LogCategory
import org.spongepowered.asm.mixin.transformer.IMixinTransformer
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.lang.reflect.Constructor
import java.net.JarURLConnection
import java.net.MalformedURLException
import java.net.URL
import java.nio.file.FileSystemNotFoundException
import java.nio.file.Files
import java.nio.file.Path
import java.security.CodeSource
import java.security.cert.Certificate
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.jar.Manifest
import kotlin.concurrent.Volatile

internal class KnotClassDelegate<T>(
    private val isDevelopment: Boolean,
    private val envType: EnvType,
    override val classLoader: T,
    private val parentClassLoader: ClassLoader,
    private val provider: GameProvider
) :
    KnotClassLoaderInterface where T : ClassLoader?, T : ClassLoaderAccess? {
    internal class Metadata(val manifest: Manifest?, val codeSource: CodeSource?) {
        companion object {
            val EMPTY: Metadata = Metadata(null, null)
        }
    }

    private val metadataCache: MutableMap<Path, Metadata> = ConcurrentHashMap()
    private var mixinTransformer: IMixinTransformer? = null
    private var transformInitialized = false

    @Volatile
    private var codeSources = emptySet<Path>()

    @Volatile
    private var validParentCodeSources: Set<Path>? =
        null // null = disabled isolation, game provider has to set it to opt in
    private val allowedPrefixes: MutableMap<Path, Array<String>> = ConcurrentHashMap()
    private val parentSourcedClasses: MutableSet<String> = Collections.newSetFromMap(ConcurrentHashMap())

    override fun initializeTransformers() {
        check(!transformInitialized) { "Cannot initialize KnotClassDelegate twice!" }

        mixinTransformer = MixinServiceKnot.transformer

        if (mixinTransformer == null) {
            try { // reflective instantiation for older mixin versions
                val ctor = Class.forName("org.spongepowered.asm.mixin.transformer.MixinTransformer")
                    .getConstructor() as Constructor<IMixinTransformer>
                ctor.isAccessible = true
                mixinTransformer = ctor.newInstance()
            } catch (e: ReflectiveOperationException) {
                debug(
                    LogCategory.KNOT,
                    "Can't create Mixin transformer through reflection (only applicable for 0.8-0.8.2): %s",
                    e
                )

                // both lookups failed (not received through IMixinService.offer and not found through reflection)
                throw IllegalStateException("mixin transformer unavailable?")
            }
        }

        transformInitialized = true
    }

    private fun getMixinTransformer(): IMixinTransformer {
        checkNotNull(mixinTransformer)
        return mixinTransformer!!
    }

    override fun addCodeSource(path: Path?) {
        var path = path!!
        path = normalizeExistingPath(path)

        synchronized(this) {
            val codeSources = this.codeSources
            if (codeSources.contains(path)) return

            val newCodeSources: MutableSet<Path> = HashSet(codeSources.size + 1, 1f)
            newCodeSources.addAll(codeSources)
            newCodeSources.add(path)
            this.codeSources = newCodeSources
        }

        try {
            classLoader!!.addUrlFwd(asUrl(path))
        } catch (e: MalformedURLException) {
            throw RuntimeException(e)
        }

        if (LOG_CLASS_LOAD_ERRORS) info(LogCategory.KNOT, "added code source %s", path)
    }

    override fun setAllowedPrefixes(codeSource: Path?, vararg prefixes: String?) {
        var codeSource = codeSource!!
        codeSource = normalizeExistingPath(codeSource)

        if (prefixes.size == 0) {
            allowedPrefixes.remove(codeSource)
        } else {
            allowedPrefixes[codeSource] = prefixes as Array<String>
        }
    }

    override fun setValidParentClassPath(codeSources: Collection<Path?>?) {
        val validPaths: MutableSet<Path> = HashSet(codeSources!!.size, 1f)

        for (path in codeSources) {
            validPaths.add(normalizeExistingPath(path!!))
        }

        this.validParentCodeSources = validPaths
    }

    override fun getManifest(codeSource: Path?): Manifest? {
        return getMetadata(normalizeExistingPath(codeSource!!)).manifest
    }

    override fun isClassLoaded(name: String?): Boolean {
        synchronized(classLoader!!.getClassLoadingLockFwd(name)!!) {
            return classLoader.findLoadedClassFwd(name) != null
        }
    }

    @Throws(ClassNotFoundException::class)
    override fun loadIntoTarget(name: String?): Class<*> {
        synchronized(classLoader!!.getClassLoadingLockFwd(name)!!) {
            var c = classLoader.findLoadedClassFwd(name)
            if (c == null) {
                c = tryLoadClass(name!!, true)

                if (c == null) {
                    throw ClassNotFoundException("can't find class $name")
                } else if (LOG_CLASS_LOAD) {
                    info(LogCategory.KNOT, "loaded class %s into target", name)
                }
            }

            classLoader.resolveClassFwd(c)
            return c
        }
    }

    @Throws(ClassNotFoundException::class)
    fun loadClass(name: String, resolve: Boolean): Class<*>? {
        synchronized(classLoader!!.getClassLoadingLockFwd(name)!!) {
            var c = classLoader.findLoadedClassFwd(name)
            if (c == null) {
                if (name.startsWith("java.")) { // fast path for java.** (can only be loaded by the platform CL anyway)
                    c = PLATFORM_CLASS_LOADER.loadClass(name)
                } else {
                    c = tryLoadClass(name, false) // try local load

                    if (c == null) { // not available locally, try system class loader
                        val fileName = getClassFileName(name)
                        val url = parentClassLoader.getResource(fileName)

                        if (url == null) { // no .class file
                            try {
                                c = PLATFORM_CLASS_LOADER.loadClass(name)
                                if (LOG_CLASS_LOAD) info(
                                    LogCategory.KNOT,
                                    "loaded resources-less class %s from platform class loader"
                                )
                            } catch (e: ClassNotFoundException) {
                                if (LOG_CLASS_LOAD_ERRORS) warn(LogCategory.KNOT, "can't find class %s", name)
                                throw e
                            }
                        } else if (!isValidParentUrl(url, fileName)) { // available, but restricted
                            // The class would technically be available, but the game provider restricted it from being
                            // loaded by setting validParentUrls and not including "url". Typical causes are:
                            // - accessing classes too early (game libs shouldn't be used until Loader is ready)
                            // - using jars that are only transient (deobfuscation input or pass-through installers)
                            val msg = String.format(
                                "can't load class %s at %s as it hasn't been exposed to the game (yet? The system property " + SystemProperties.PATH_GROUPS + " may not be set correctly in-dev)",
                                name, getCodeSource(url, fileName)
                            )
                            if (LOG_CLASS_LOAD_ERRORS) warn(LogCategory.KNOT, msg)
                            throw ClassNotFoundException(msg)
                        } else { // load from system cl
                            if (LOG_CLASS_LOAD) info(
                                LogCategory.KNOT,
                                "loading class %s using the parent class loader",
                                name
                            )
                            c = parentClassLoader.loadClass(name)
                        }
                    } else if (LOG_CLASS_LOAD) {
                        info(LogCategory.KNOT, "loaded class %s", name)
                    }
                }
            }

            if (resolve) {
                classLoader.resolveClassFwd(c)
            }
            return c
        }
    }

    /**
     * Check if an url is loadable by the parent class loader.
     *
     *
     * This handles explicit parent url whitelisting by [.validParentCodeSources] or shadowing by [.codeSources]
     */
    private fun isValidParentUrl(url: URL?, fileName: String): Boolean {
        if (url == null) return false
        if (DISABLE_ISOLATION) return true
        if (!hasRegularCodeSource(url)) return true

        val codeSource = getCodeSource(url, fileName)
        val validParentCodeSources = this.validParentCodeSources

        return if (validParentCodeSources != null) { // explicit whitelist (in addition to platform cl classes)
            validParentCodeSources.contains(codeSource) || PLATFORM_CLASS_LOADER.getResource(
                fileName
            ) != null
        } else { // reject urls shadowed by this cl
            !codeSources.contains(codeSource)
        }
    }

    @Throws(ClassNotFoundException::class)
    fun tryLoadClass(name: String, allowFromParent: Boolean): Class<*>? {
        var allowFromParent = allowFromParent
        if (name.startsWith("java.")) {
            return null
        }

        if (!allowedPrefixes.isEmpty() && !DISABLE_ISOLATION) { // check prefix restrictions (allows exposing libraries partially during startup)
            val fileName = getClassFileName(name)
            val url = classLoader!!.getResource(fileName)

            if (url != null && hasRegularCodeSource(url)) {
                val codeSource = getCodeSource(url, fileName)
                val prefixes = allowedPrefixes[codeSource]

                if (prefixes != null) {
                    assert(prefixes.size > 0)
                    var found = false

                    for (prefix in prefixes) {
                        if (name.startsWith(prefix)) {
                            found = true
                            break
                        }
                    }

                    if (!found) {
                        val msg = "class $name is currently restricted from being loaded"
                        if (LOG_CLASS_LOAD_ERRORS) warn(LogCategory.KNOT, msg)
                        throw ClassNotFoundException(msg)
                    }
                }
            }
        }

        if (!allowFromParent && !parentSourcedClasses.isEmpty()) { // propagate loadIntoTarget behavior to its nested classes
            var pos = name.length

            while ((name.lastIndexOf('$', pos - 1).also { pos = it }) > 0) {
                if (parentSourcedClasses.contains(name.substring(0, pos))) {
                    allowFromParent = true
                    break
                }
            }
        }

        val input = getPostMixinClassByteArray(name, allowFromParent) ?: return null

        // The class we're currently loading could have been loaded already during Mixin initialization triggered by `getPostMixinClassByteArray`.
        // If this is the case, we want to return the instance that was already defined to avoid attempting a duplicate definition.
        val existingClass = classLoader!!.findLoadedClassFwd(name)

        if (existingClass != null) {
            return existingClass
        }

        if (allowFromParent) {
            parentSourcedClasses.add(name)
        }

        val metadata = getMetadata(name)

        val pkgDelimiterPos = name.lastIndexOf('.')

        if (pkgDelimiterPos > 0) {
            // TODO: package definition stub
            val pkgString = name.substring(0, pkgDelimiterPos)

            if (classLoader.getPackageFwd(pkgString) == null) {
                try {
                    classLoader.definePackageFwd(pkgString, null, null, null, null, null, null, null)
                } catch (e: IllegalArgumentException) { // presumably concurrent package definition
                    if (classLoader.getPackageFwd(pkgString) == null) throw e // still not defined?
                }
            }
        }

        return classLoader.defineClassFwd(name, input, 0, input.size, metadata.codeSource)
    }

    private fun getMetadata(name: String): Metadata {
        val fileName = getClassFileName(name)
        val url = classLoader!!.getResource(fileName)
        if (url == null || !hasRegularCodeSource(url)) return Metadata.EMPTY

        return getMetadata(getCodeSource(url, fileName))
    }

    private fun getMetadata(codeSource: Path): Metadata {
        return metadataCache.computeIfAbsent(codeSource) { path: Path ->
            var manifest: Manifest? = null
            var cs: CodeSource? = null
            var certificates: Array<Certificate?>? = null

            try {
                if (Files.isDirectory(path)) {
                    manifest = readManifestFromBasePath(path)
                } else {
                    val connection = URL("jar:" + path.toUri().toString() + "!/").openConnection()

                    if (connection is JarURLConnection) {
                        manifest = connection.manifest
                        certificates = connection.certificates
                    }

                    if (manifest == null) {
                        getJarFileSystem(path, false).use { jarFs ->
                            manifest = readManifestFromBasePath(
                                jarFs.get()!!.rootDirectories.iterator().next()
                            )
                        }
                    }

                    // TODO
                    /* JarEntry codeEntry = codeSourceJar.getJarEntry(filename);

					if (codeEntry != null) {
						cs = new CodeSource(codeSourceURL, codeEntry.getCodeSigners());
					} */
                }
            } catch (e: IOException) {
                if (MoltenexLauncherBase.launcher!!.isDevelopment) {
                    warn(LogCategory.KNOT, "Failed to load manifest", e)
                }
            } catch (e: FileSystemNotFoundException) {
                if (MoltenexLauncherBase.launcher!!.isDevelopment) {
                    warn(LogCategory.KNOT, "Failed to load manifest", e)
                }
            }

            if (cs == null) {
                try {
                    cs = CodeSource(asUrl(path), certificates)
                } catch (e: MalformedURLException) {
                    throw RuntimeException(e)
                }
            }
            Metadata(manifest, cs)
        }
    }

    private fun getPostMixinClassByteArray(name: String, allowFromParent: Boolean): ByteArray? {
        val transformedClassArray = getPreMixinClassByteArray(name, allowFromParent)

        if (!transformInitialized || !canTransformClass(name)) {
            return transformedClassArray
        }

        try {
            return getMixinTransformer().transformClassBytes(name, name, transformedClassArray)
        } catch (t: Throwable) {
            val msg = String.format("Mixin transformation of %s failed", name)
            if (LOG_TRANSFORM_ERRORS) warn(LogCategory.KNOT, msg, t)

            throw RuntimeException(msg, t)
        }
    }

    override fun getPreMixinClassBytes(name: String?): ByteArray? {
        return getPreMixinClassByteArray(name!!, true)
    }

    /**
     * Runs all the class transformers except mixin.
     */
    private fun getPreMixinClassByteArray(name: String, allowFromParent: Boolean): ByteArray? {
        // some of the transformers rely on dot notation
        var name = name
        name = name.replace('/', '.')

        if (!transformInitialized || !canTransformClass(name)) {
            try {
                return getRawClassByteArray(name, allowFromParent)
            } catch (e: IOException) {
                throw RuntimeException("Failed to load class file for '$name'!", e)
            }
        }

        var input = provider.getEntrypointTransformer().transform(name)

        if (input == null) {
            try {
                input = getRawClassByteArray(name, allowFromParent)
            } catch (e: IOException) {
                throw RuntimeException("Failed to load class file for '$name'!", e)
            }
        }

        if (input != null) {
            return transform(isDevelopment, envType, name, input)
        }

        return null
    }

    @Throws(IOException::class)
    override fun getRawClassBytes(name: String?): ByteArray? {
        return getRawClassByteArray(name!!, true)
    }

    @Throws(IOException::class)
    private fun getRawClassByteArray(name: String, allowFromParent: Boolean): ByteArray? {
        var name = name
        name = getClassFileName(name)
        var url = classLoader!!.findResourceFwd(name)

        if (url == null) {
            if (!allowFromParent) return null

            url = parentClassLoader.getResource(name)

            if (!isValidParentUrl(url, name)) {
                if (LOG_CLASS_LOAD) info(
                    LogCategory.KNOT,
                    "refusing to load class %s at %s from parent class loader",
                    name,
                    getCodeSource(url, name)
                )

                return null
            }
        }

        url!!.openStream().use { inputStream ->
            val a = inputStream.available()
            val outputStream = ByteArrayOutputStream(if (a < 32) 32768 else a)
            val buffer = ByteArray(8192)
            var len: Int

            while ((inputStream.read(buffer).also { len = it }) > 0) {
                outputStream.write(buffer, 0, len)
            }
            return outputStream.toByteArray()
        }
    }

    internal interface ClassLoaderAccess {
        fun addUrlFwd(url: URL?)
        fun findResourceFwd(name: String?): URL?

        fun getPackageFwd(name: String?): Package?

        @Throws(IllegalArgumentException::class)
        fun definePackageFwd(
            name: String?,
            specTitle: String?,
            specVersion: String?,
            specVendor: String?,
            implTitle: String?,
            implVersion: String?,
            implVendor: String?,
            sealBase: URL?
        ): Package?

        fun getClassLoadingLockFwd(name: String?): Any?
        fun findLoadedClassFwd(name: String?): Class<*>?
        fun defineClassFwd(name: String?, b: ByteArray?, off: Int, len: Int, cs: CodeSource?): Class<*>?
        fun resolveClassFwd(cls: Class<*>?)
    }

    companion object {
        private val LOG_CLASS_LOAD = System.getProperty(SystemProperties.DEBUG_LOG_CLASS_LOAD) != null
        private val LOG_CLASS_LOAD_ERRORS =
            LOG_CLASS_LOAD || System.getProperty(SystemProperties.DEBUG_LOG_CLASS_LOAD_ERRORS) != null
        private val LOG_TRANSFORM_ERRORS = System.getProperty(SystemProperties.DEBUG_LOG_TRANSFORM_ERRORS) != null
        private val DISABLE_ISOLATION = System.getProperty(SystemProperties.DEBUG_DISABLE_CLASS_PATH_ISOLATION) != null

        private val PLATFORM_CLASS_LOADER = platformClassLoader

        private fun canTransformClass(name: String): Boolean {
            var name = name
            name = name.replace('/', '.')
            // Blocking Fabric Loader classes is no longer necessary here as they don't exist on the modding class loader
            return  /* !"net.fabricmc.api.EnvType".equals(name) && !name.startsWith("net.fabricmc.loader.") && */!name.startsWith(
                "org.apache.logging.log4j"
            )
        }

        private fun hasRegularCodeSource(url: URL): Boolean {
            return url.protocol == "file" || url.protocol == "jar"
        }

        private fun getCodeSource(url: URL, fileName: String): Path {
            try {
                return normalizeExistingPath(UrlUtil.getCodeSource(url, fileName))
            } catch (e: UrlConversionException) {
                throw wrap(e)
            }
        }

        private val platformClassLoader: ClassLoader
            get() {
                return try {
                    ClassLoader::class.java.getMethod("getPlatformClassLoader")
                        .invoke(null) as ClassLoader // Java 9+ only
                } catch (e: NoSuchMethodException) {
                    object : ClassLoader(null) {} // fall back to boot cl
                } catch (e: ReflectiveOperationException) {
                    throw RuntimeException(e)
                }
            }
    }
}
