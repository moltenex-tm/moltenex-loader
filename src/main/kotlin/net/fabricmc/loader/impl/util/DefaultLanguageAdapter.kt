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

import net.fabricmc.loader.api.LanguageAdapter
import net.fabricmc.loader.api.LanguageAdapterException
import net.fabricmc.loader.api.ModContainer
import com.moltenex.loader.impl.launch.MoltenexLauncherBase
import java.lang.invoke.MethodHandle
import java.lang.invoke.MethodHandleProxies
import java.lang.invoke.MethodHandles
import java.lang.reflect.Method
import java.lang.reflect.Modifier

class DefaultLanguageAdapter private constructor() : LanguageAdapter {
    @Throws(LanguageAdapterException::class)
    override fun <T> create(mod: ModContainer?, value: String?, type: Class<T>?): T {
        val methodSplit = value?.split("::".toRegex())?.dropLastWhile { it.isEmpty() }?.toTypedArray()

        if (methodSplit != null) {
            if (methodSplit.size >= 3) {
                throw LanguageAdapterException("Invalid handle format: $value")
            }
        }

        val c: Class<*>

        try {
            c = Class.forName(methodSplit!![0], true, MoltenexLauncherBase.launcher!!.targetClassLoader)
        } catch (e: ClassNotFoundException) {
            throw LanguageAdapterException(e)
        }

        if (methodSplit.size == 1) {
            if (type!!.isAssignableFrom(c)) {
                try {
                    return c.getDeclaredConstructor().newInstance() as T
                } catch (e: Exception) {
                    throw LanguageAdapterException(e)
                }
            } else {
                throw LanguageAdapterException("Class " + c.name + " cannot be cast to " + type.name + "!")
            }
        } else  /* length == 2 */ {
            val methodList: MutableList<Method> = ArrayList()

            for (m in c.declaredMethods) {
                if (m.name != methodSplit[1]) {
                    continue
                }

                methodList.add(m)
            }

            try {
                val field = c.getDeclaredField(methodSplit[1])
                val fType = field.type

                if ((field.modifiers and Modifier.STATIC) == 0) {
                    throw LanguageAdapterException("Field $value must be static!")
                }

                if (methodList.isNotEmpty()) {
                    throw LanguageAdapterException("Ambiguous $value - refers to both field and method!")
                }

                if (type != null) {
                    if (!type.isAssignableFrom(fType)) {
                        throw LanguageAdapterException("Field " + value + " cannot be cast to " + type.name + "!")
                    }
                }

                return field[null] as T
            } catch (e: NoSuchFieldException) {
                // ignore
            } catch (e: IllegalAccessException) {
                throw LanguageAdapterException("Field $value cannot be accessed!", e)
            }

            if (type != null) {
                if (!type.isInterface) {
                    throw LanguageAdapterException("Cannot proxy method " + value + " to non-interface type " + type.name + "!")
                }
            }

            if (methodList.isEmpty()) {
                throw LanguageAdapterException("Could not find $value!")
            } else if (methodList.size >= 2) {
                throw LanguageAdapterException("Found multiple method entries of name $value!")
            }

            val targetMethod = methodList[0]
            var `object`: Any? = null

            if ((targetMethod.modifiers and Modifier.STATIC) == 0) {
                try {
                    `object` = c.getDeclaredConstructor().newInstance()
                } catch (e: Exception) {
                    throw LanguageAdapterException(e)
                }
            }

            var handle: MethodHandle

            try {
                handle = MethodHandles.lookup()
                    .unreflect(targetMethod)
            } catch (ex: Exception) {
                throw LanguageAdapterException(ex)
            }

            if (`object` != null) {
                handle = handle.bindTo(`object`)
            }

            // uses proxy as well, but this handles default and object methods
            try {
                return MethodHandleProxies.asInterfaceInstance(type, handle)
            } catch (ex: Exception) {
                throw LanguageAdapterException(ex)
            }
        }
    }

    companion object {
        val INSTANCE: DefaultLanguageAdapter = DefaultLanguageAdapter()
    }
}
