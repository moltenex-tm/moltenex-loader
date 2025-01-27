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
package net.fabricmc.loader.api.metadata

/**
 * Represents a custom value in the `fabric.mod.json`.
 */
interface CustomValue {
    /**
     * Returns the type of the value.
     */
    fun getType(): CvType

    /**
     * Returns this value as a [CvType.OBJECT].
     *
     * @throws ClassCastException if this value is not an object
     */
    fun getAsObject(): CvObject

    /**
     * Returns this value as a [CvType.ARRAY].
     *
     * @throws ClassCastException if this value is not an array
     */
    fun getAsArray(): CvArray

    /**
     * Returns this value as a [CvType.STRING].
     *
     * @throws ClassCastException if this value is not a string
     */
    fun getAsString(): String

    /**
     * Returns this value as a [CvType.NUMBER].
     *
     * @throws ClassCastException if this value is not a number
     */
    fun getAsNumber(): Number

    /**
     * Returns this value as a [CvType.BOOLEAN].
     *
     * @throws ClassCastException if this value is not a boolean
     */
    fun getAsBoolean(): Boolean

    /**
     * Represents a [CvType.OBJECT] value.
     */
    interface CvObject : CustomValue, Iterable<Map.Entry<String, CustomValue>> {
        /**
         * Returns the number of key-value pairs within this object value.
         */
        fun size(): Int

        /**
         * Returns whether a [key] is present within this object value.
         *
         * @param key the key to check
         * @return whether the key is present
         */
        fun containsKey(key: String): Boolean

        /**
         * Gets the value associated with a [key] within this object value.
         *
         * @param key the key to check
         * @return the value associated, or `null` if no such value is present
         */
        operator fun get(key: String): CustomValue?
    }

    /**
     * Represents a [CvType.ARRAY] value.
     */
    interface CvArray : CustomValue, Iterable<CustomValue> {
        /**
         * Returns the number of values within this array value.
         */
        fun size(): Int

        /**
         * Gets the value at [index] within this array value.
         *
         * @param index the index of the value
         * @return the value associated
         * @throws IndexOutOfBoundsException if the index is not within [size]
         */
        operator fun get(index: Int): CustomValue
    }

    /**
     * The possible types of a custom value.
     */
    enum class CvType {
        OBJECT, ARRAY, STRING, NUMBER, BOOLEAN, NULL
    }
}
