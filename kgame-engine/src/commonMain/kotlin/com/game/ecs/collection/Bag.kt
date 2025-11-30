@file:Suppress("ConvertTwoComparisonsToRangeCheck")

package com.game.ecs.collection

import kotlin.math.max


/**
 * Returns a new [Bag] with the given [values].
 */
inline fun <reified T> bagOf(vararg values: T): Bag<T> {
    val bag = Bag<T>(values.size)
    var i = 0
    while (i < values.size) {
        bag[i] = values[i++]
    }
    return bag
}


/**
 * A bag implementation in Kotlin containing only the necessary functions for Fleks.
 */
class Bag<T> @PublishedApi internal constructor(
    capacity: Int = 64
) {

    @PublishedApi
    internal var values: Array<T?> = arrayOfNulls(capacity)

    var size: Int = 0
        private set

    val capacity: Int
        get() = values.size

    fun add(value: T) {
        if (size == values.size) {
            values = values.copyOf(max(1, size * 2))
        }
        values[size++] = value
    }

    operator fun set(index: Int, value: T) {
        if (index >= values.size) {
            values = values.copyOf(max(size * 2, index + 1))
        }
        size = max(size, index + 1)
        values[index] = value
    }

    operator fun get(index: Int): T {
        if (index < 0 || index >= size) throw IndexOutOfBoundsException("$index is not valid for bag of size $size")
        return values[index] ?: throw NoSuchElementException("Bag has no value at index $index")
    }

    /**
     * Returns the element at position [index] or null if there is no element or if [index] is out of bounds
     */
    fun getOrNull(index: Int): T? {
        return if (index >= 0 && index < size) values[index] else null
    }

    fun hasNoValueAtIndex(index: Int): Boolean {
        return index < 0 || index >= size || values[index] == null
    }

    fun hasValueAtIndex(index: Int): Boolean {
        return index >= 0 && index < size && values[index] != null
    }

    fun removeAt(index: Int) {
        values[index] = null
    }

    fun removeValue(value: T): Boolean {
        var i = 0
        while (i < size) {
            if (values[i] == value) {
                values[i] = values[--size]
                values[size] = null
                return true
            }
            i++
        }
        return false
    }

    fun clear() {
        var i = 0
        while (i < size) {
            values[i++] = null
        }
        size = 0
    }

    operator fun contains(value: T): Boolean {
        var i = 0
        while (i < size) {
            if (values[i++] == value) {
                return true
            }
        }
        return false
    }

    inline fun forEach(action: (T) -> Unit) {
        var i = 0
        while (i < size) {
            values[i++]?.let(action)
        }
    }

    override fun toString(): String {
        return "Bag(size=$size, values=${values.contentToString()})"
    }
}


