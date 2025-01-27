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
package net.fabricmc.loader.api

import com.moltenex.loader.impl.launch.MoltenexLauncher
import com.moltenex.loader.impl.launch.MoltenexLauncherBase
import net.fabricmc.api.EnvType
import net.fabricmc.loader.impl.util.DefaultLanguageAdapter
import net.fabricmc.loader.impl.util.LoaderUtil
import org.objectweb.asm.ClassReader
import java.io.IOException
import java.io.InputStream


/**
 * Creates instances of objects from custom notations.
 *
 *
 * It enables obtaining of other JVM languages' objects with custom instantiation logic.
 *
 *
 * A language adapter is defined as so in `fabric.mod.json`:
 * <pre><blockquote>
 * "languageAdapter": {
 * "&lt;a key&gt;": "&lt;the binary name of the language adapter class&gt;"
 * }
</blockquote></pre> *
 * Multiple keys can be present in the `languageAdapter` section.
 *
 *
 * In the declaration, the language adapter is referred by its [binary name](https://docs.oracle.com/javase/specs/jls/se8/html/jls-13.html#jls-13.1),
 * such as `"mypackage.MyClass$Inner"`. It must have a no-argument public constructor for the Loader to instantiate.
 *
 *
 * The `default` language adapter from Fabric Loader can accept `value` as follows:
 *
 *  * A fully qualified reference to a class, in [
 * binary name](https://docs.oracle.com/javase/specs/jls/se8/html/jls-13.html#jls-13.1), such as `package.MyClass$Inner`, where the class has a public no-argument constructor
 * and `type` is assignable from the class.
 *
 *
 * An example of an entrypoint class
 * <pre><blockquote>
 * package net.fabricmc.example;
 * import net.fabricmc.api.ModInitializer;
 * public class ExampleMod implements ModInitializer {
 * public ExampleMod() {} // the constructor must be public no-argument
 * @Override
 * public void onInitialize() {}
 * }
</blockquote></pre> *
 * You would declare `"net.fabricmc.example.ExampleMod"`.
 *
 *
 * For each entrypoint reference, a new instance of the class is created.
 * If this class implements two separate entrypoints, there will be two distinct
 * instances of this class in two entrypoint containers.
 *
 *
 *  * A fully qualified reference to a class in binary name followed by `::` and a
 * field name. The field must be static, and `type` must be assignable from
 * the field's class.
 *
 *
 * An example of an entrypoint field
 * <pre><blockquote>
 * package net.fabricmc.example;
 * import net.fabricmc.api.ModInitializer;
 * public final class ExampleMod implements ModInitializer {
 * public static final ExampleMod INSTANCE = new ExampleMod();
 *
 * private ExampleMod() {} // Doesn't need to be instantiable by loader
 *
 * @Override
 * public void onInitialize() {}
 * }
</blockquote></pre> *
 * You would declare `"net.fabricmc.example.ExampleMod::INSTANCE"`.
 *
 *
 *  * A fully qualified reference to a class in binary name followed by `::` and a
 * method name. The method must be capable to implement `type` as a
 * method reference. If the method is not static, the class must have an
 * accessible no-argument constructor for the Loader to create an instance.
 *
 *
 * An example of an entrypoint method
 * <pre><blockquote>
 * package net.fabricmc.example;
 * public final class ExampleMod {
 * private ExampleMod() {} // doesn't need to be instantiable by others if method is static
 *
 * public static void init() {}
 * }
</blockquote></pre> *
 * You would declare `"net.fabricmc.example.ExampleMod::init"`.
 *
 *
 */
interface LanguageAdapter {
    enum class MissingSuperclassBehavior {
        RETURN_NULL,
        CRASH
    }

    @Throws(IOException::class)
    private fun canApplyInterface(itfString: String): Boolean {
        // TODO: Be a bit more involved
        when (itfString) {
            "net/fabricmc/api/ClientModInitializer" -> if (MoltenexLauncher.getInstance().environmentType === EnvType.SERVER
            ) {
                return false
            }

            "net/fabricmc/api/DedicatedServerModInitializer" -> if (MoltenexLauncher.getInstance().environmentType === EnvType.CLIENT
            ) {
                return false
            }
        }

        val stream: InputStream =
            MoltenexLauncherBase.launcher!!.getResourceAsStream(LoaderUtil.getClassFileName(itfString))
                ?: return false

        val reader = ClassReader(stream)

        for (s in reader.interfaces) {
            if (!canApplyInterface(s)) {
                stream.close()
                return false
            }
        }

        stream.close()
        return true
    }


    @Throws(ClassNotFoundException::class, LanguageAdapterException::class)
    fun createInstance(classString: String?, options: Options?): Any? {
        try {
            val c: Class<*> = getClass(classString!!, options!!)!!

            return createInstance(c.toString(), options)
        } catch (e: IOException) {
            throw LanguageAdapterException("I/O error!", e)
        }
    }

    @Throws(ClassNotFoundException::class, IOException::class)
    fun getClass(className: String, options: Options): Class<*>? {
        val stream = MoltenexLauncherBase.launcher!!.getResourceAsStream(LoaderUtil.getClassFileName(className))
            ?: throw ClassNotFoundException("Could not find or load class $className")

        val reader = ClassReader(stream)

        for (interfaceName in reader.interfaces) {
            if (!canApplyInterface(interfaceName)) {
                stream.close() // Ensure the stream is closed in all cases
                when (options.missingSuperclassBehavior) {
                    MissingSuperclassBehavior.RETURN_NULL -> return null
                    MissingSuperclassBehavior.CRASH -> throw ClassNotFoundException("Could not find or load class $interfaceName")
                    null -> throw ClassNotFoundException("Could not find or load class $interfaceName")
                }
            }
        }

        stream.close() // Close the stream before returning
        return MoltenexLauncherBase.getClass(className)
    }

    /**
     * Creates an object of `type` from an arbitrary string declaration.
     *
     * @param mod   the mod which the object is from
     * @param value the string declaration of the object
     * @param type  the type that the created object must be an instance of
     * @param <T>   the type
     * @return the created object
     * @throws LanguageAdapterException if a problem arises during creation, such as an invalid declaration
    </T> */
    @Throws(LanguageAdapterException::class)
    fun <T> create(mod: ModContainer?, value: String?, type: Class<T>?): T

    companion object {
        val default: LanguageAdapter
            /**
             * Get an instance of the default language adapter.
             */
            get() = DefaultLanguageAdapter.INSTANCE
    }

    class Options {
        var missingSuperclassBehavior: MissingSuperclassBehavior? = null
            private set

        class Builder private constructor() {
            private val options = Options()

            fun missingSuperclassBehaviour(value: MissingSuperclassBehavior?): Builder {
                options.missingSuperclassBehavior = value
                return this
            }

            fun build(): Options {
                return options
            }

            companion object {
                fun create(): Builder {
                    return Builder()
                }
            }
        }
    }
}
