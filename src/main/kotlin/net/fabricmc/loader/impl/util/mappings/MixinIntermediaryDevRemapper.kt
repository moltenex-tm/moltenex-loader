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
package net.fabricmc.loader.impl.util.mappings

import net.fabricmc.mappingio.tree.MappingTree
import net.fabricmc.mappingio.tree.MappingTree.MemberMapping
import org.spongepowered.asm.mixin.transformer.ClassInfo
import java.util.*

class MixinIntermediaryDevRemapper(mappings: MappingTree, from: String?, to: String?) :
    MixinRemapper(mappings, mappings.getNamespaceId(from), mappings.getNamespaceId(to)) {
    private val allPossibleClassNames: MutableSet<String?> = HashSet()
    private val nameFieldLookup: MutableMap<String?, String?> = HashMap()
    private val nameMethodLookup: MutableMap<String?, String?> = HashMap()
    private val nameDescFieldLookup: MutableMap<String?, String?> = HashMap()
    private val nameDescMethodLookup: MutableMap<String?, String?> = HashMap()

    init {
        for (classDef in mappings.classes) {
            allPossibleClassNames.add(classDef.getName(from))
            allPossibleClassNames.add(classDef.getName(to))

            putMemberInLookup(fromId, toId, classDef.fields, nameFieldLookup, nameDescFieldLookup)
            putMemberInLookup(fromId, toId, classDef.methods, nameMethodLookup, nameDescMethodLookup)
        }
    }

    private fun <T : MemberMapping?> putMemberInLookup(
        from: Int,
        to: Int,
        descriptored: Collection<T>,
        nameMap: MutableMap<String?, String?>,
        nameDescMap: MutableMap<String?, String?>
    ) {
        for (field in descriptored) {
            val nameFrom = field!!.getName(from)
            val descFrom = field.getDesc(from)
            val nameTo = field.getName(to)

            var prev = nameMap.putIfAbsent(nameFrom, nameTo)

            if (prev != null && prev !== ambiguousName && (prev != nameTo)) {
                nameDescMap[nameFrom] = ambiguousName
            }

            val key = getNameDescKey(nameFrom, descFrom)
            prev = nameDescMap.putIfAbsent(key, nameTo)

            if (prev != null && prev !== ambiguousName && (prev != nameTo)) {
                nameDescMap[key] = ambiguousName
            }
        }
    }

    private fun throwAmbiguousLookup(type: String, name: String, desc: String?) {
        throw RuntimeException("Ambiguous Mixin: $type lookup $name $desc is not unique")
    }

    private fun mapMethodNameInner(owner: String, name: String, desc: String): String {
        val result = super.mapMethodName(owner, name, desc)

        if (result == name) {
            val otherClass = unmap(owner)
            return super.mapMethodName(otherClass, name, unmapDesc(desc)).toString()
        } else {
            return result.toString()
        }
    }

    private fun mapFieldNameInner(owner: String, name: String, desc: String): String {
        val result = super.mapFieldName(owner, name, desc)

        if (result == name) {
            val otherClass = unmap(owner)
            return super.mapFieldName(otherClass, name, unmapDesc(desc)).toString()
        } else {
            return result.toString()
        }
    }

    override fun mapMethodName(owner: String, name: String, desc: String): String? {
        // handle unambiguous values early
        if (allPossibleClassNames.contains(owner)) {
            val newName = nameDescMethodLookup[getNameDescKey(name, desc)]

            if (newName != null) {
                if (newName === ambiguousName) {
                    throwAmbiguousLookup("method", name, desc)
                } else {
                    return newName
                }
            } else {
                // FIXME: this kind of namespace mixing shouldn't happen..
                // TODO: this should not repeat more than once
                val unmapOwner = unmap(owner)
                val unmapDesc = unmapDesc(desc)

                return if (unmapOwner != owner || unmapDesc != desc) {
                    mapMethodName(unmapOwner, name, unmapDesc)
                } else {
                    // take advantage of the fact allPossibleClassNames
                    // and nameDescLookup cover all sets; if none are present,
                    // we don't have a mapping for it.
                    name
                }
            }
        }

        var classInfo: ClassInfo? = ClassInfo.forName(map(owner))
            ?: // unknown class?
            return name

        val queue: Queue<ClassInfo> = ArrayDeque()

        do {
            val ownerO = unmap(classInfo!!.name)
            val s: String

            if ((mapMethodNameInner(ownerO, name, desc).also { s = it }) != name) {
                return s
            }

            if (classInfo.superName != null && !classInfo.superName.startsWith("java/")) {
                val cSuper = classInfo.superClass

                if (cSuper != null) {
                    queue.add(cSuper)
                }
            }

            for (itf in classInfo.interfaces) {
                if (itf.startsWith("java/")) {
                    continue
                }

                val cItf = ClassInfo.forName(itf)

                if (cItf != null) {
                    queue.add(cItf)
                }
            }
        } while ((queue.poll().also { classInfo = it }) != null)

        return name
    }

    override fun mapFieldName(owner: String, name: String, desc: String): String? {
        // handle unambiguous values early
        if (allPossibleClassNames.contains(owner)) {
            val newName = nameDescFieldLookup[getNameDescKey(name, desc)]

            if (newName != null) {
                if (newName === ambiguousName) {
                    throwAmbiguousLookup("field", name, desc)
                } else {
                    return newName
                }
            } else {
                // FIXME: this kind of namespace mixing shouldn't happen..
                // TODO: this should not repeat more than once
                val unmapOwner = unmap(owner)
                val unmapDesc = unmapDesc(desc)

                return if (unmapOwner != owner || unmapDesc != desc) {
                    mapFieldName(unmapOwner, name, unmapDesc)
                } else {
                    // take advantage of the fact allPossibleClassNames
                    // and nameDescLookup cover all sets; if none are present,
                    // we don't have a mapping for it.
                    name
                }
            }
        }

        var c = ClassInfo.forName(map(owner))

        while (c != null) {
            val nextOwner = unmap(c.name)
            val s = mapFieldNameInner(nextOwner, name, desc)

            if (s != name) {
                return s
            }

            if (c.superName == null || c.superName.startsWith("java/")) {
                break
            }

            c = c.superClass
        }

        return name
    }

    companion object {
        private const val ambiguousName =
            "<ambiguous>" // dummy value for ambiguous mappings - needs querying with additional owner and/or desc info

        private fun getNameDescKey(name: String?, descriptor: String?): String {
            return "$name;;$descriptor"
        }
    }
}
