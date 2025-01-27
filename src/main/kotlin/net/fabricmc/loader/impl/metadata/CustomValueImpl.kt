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
package net.fabricmc.loader.impl.metadata
import com.beust.klaxon.Klaxon
import com.beust.klaxon.JsonObject
import com.beust.klaxon.JsonArray
import net.fabricmc.loader.api.metadata.CustomValue
import okio.IOException

sealed class CustomValueImpl : CustomValue {

    companion object {
        val BOOLEAN_TRUE: CustomValue = BooleanImpl(true)
        val BOOLEAN_FALSE: CustomValue = BooleanImpl(false)
        val NULL: CustomValue = NullImpl

        @Throws(IOException::class, ParseMetadataException::class)
        fun readCustomValue(json: Any): CustomValue {
            return when (json) {
                is JsonObject -> {
                    val values = linkedMapOf<String, CustomValue>()
                    json.forEach { (key, value) ->
                        values[key] = readCustomValue(value!!)
                    }
                    ObjectImpl(values)
                }
                is JsonArray<*> -> {
                    val entries = mutableListOf<CustomValue>()
                    json.forEach { value ->
                        entries.add(readCustomValue(value!!))
                    }
                    ArrayImpl(entries)
                }
                is String -> StringImpl(json)
                is Number -> NumberImpl(json)
                is Boolean -> if (json) BOOLEAN_TRUE else BOOLEAN_FALSE
                else -> NULL
            }
        }
    }

    override fun getAsObject(): CustomValue.CvObject {
        return this as? CustomValue.CvObject
            ?: throw ClassCastException("Can't convert ${getType().name} to Object")
    }

    override fun getAsArray(): CustomValue.CvArray {
        return this as? CustomValue.CvArray
            ?: throw ClassCastException("Can't convert ${getType().name} to Array")
    }

    override fun getAsString(): String {
        return (this as? StringImpl)?.value
            ?: throw ClassCastException("Can't convert ${getType().name} to String")
    }

    override fun getAsNumber(): Number {
        return (this as? NumberImpl)?.value
            ?: throw ClassCastException("Can't convert ${getType().name} to Number")
    }

    override fun getAsBoolean(): Boolean {
        return (this as? BooleanImpl)?.value
            ?: throw ClassCastException("Can't convert ${getType().name} to Boolean")
    }

    data class ObjectImpl(val entries: Map<String, CustomValue>) : CustomValueImpl(), CustomValue.CvObject {
        override fun getType() = CustomValue.CvType.OBJECT
        override fun size() = entries.size
        override fun containsKey(key: String) = entries.containsKey(key)
        override fun get(key: String) = entries[key]
        override fun iterator(): Iterator<Map.Entry<String, CustomValue>> = entries.entries.iterator()
    }

    data class ArrayImpl(val entries: List<CustomValue>) : CustomValueImpl(), CustomValue.CvArray {
        override fun getType() = CustomValue.CvType.ARRAY
        override fun size() = entries.size
        override fun get(index: Int) = entries[index]
        override fun iterator(): Iterator<CustomValue> = entries.iterator()
    }

    data class StringImpl(val value: String) : CustomValueImpl() {
        override fun getType() = CustomValue.CvType.STRING
    }

    data class NumberImpl(val value: Number) : CustomValueImpl() {
        override fun getType() = CustomValue.CvType.NUMBER
    }

    data class BooleanImpl(val value: Boolean) : CustomValueImpl() {
        override fun getType() = CustomValue.CvType.BOOLEAN
    }

    object NullImpl : CustomValueImpl() {
        override fun getType() = CustomValue.CvType.NULL
    }
}
