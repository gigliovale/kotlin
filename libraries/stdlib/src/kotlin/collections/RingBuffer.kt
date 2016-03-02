package kotlin.collections

import java.util.*

/**
 * Provides ring buffer implementation.
 *
 * Buffer overflow is not allowed so [add] doesn't overwrite tail but raises an exception while [offer] returns `false`
 * If it is going to be public API perhaps this behaviour could be customizable
 */
internal class RingBuffer<T>(val capacity: Int): Iterable<T> {
    init {
        require(capacity >= 0) { "ring buffer capacity should not be negative but it is $capacity" }
    }

    private val buffer = arrayOfNulls<Any?>(capacity)
    private var writePosition = 0

    var size: Int = 0
        private set

    fun isEmpty() = size == 0
    fun isFull() = size == capacity

    override fun iterator(): Iterator<T> = when {
        isEmpty() -> EmptyIterator
        else -> object : AbstractIterator<T>() {
            private var count = size
            private var idx = writePosition.backward(count)

            override fun computeNext() {
                if (count == 0) {
                    done()
                } else {
                    setNext(getAtUnsafe(idx))
                    idx = idx.forward()
                    count--
                }
            }
        }
    }

    fun toArray(): Array<out Any?> {
        val size = this.size
        val result = arrayOfNulls<Any?>(size)
        var widx = 0
        var idx = writePosition.backward(size)

        while (widx < size && idx < capacity) {
            result[widx] = buffer[idx]
            widx++
            idx++
        }

        idx = 0
        while (widx < size) {
            result[widx] = buffer[idx]
            widx++
            idx++
        }

        return result
    }

    /**
     * Add [element] to the buffer or fail with [IllegalStateException] if no free space available in the buffer
     */
    fun add(element: T) {
        if (!offer(element)) {
            throw IllegalStateException("ring buffer is full")
        }
    }

    /**
     * Offers [element] to the buffer and return `true` if it was added or `false` when there is no free space available in the buffer
     */
    fun offer(element: T): Boolean {
        if (isFull()) {
            return false
        }

        buffer[writePosition] = element
        writePosition = writePosition.forward()
        size++
        return true
    }

    /**
     * Takes first element from the buffer or fails with [NoSuchElementException] if the buffer is empty
     */
    fun get(): T {
        if (isEmpty()) {
            throw NoSuchElementException("ring buffer is empty")
        }

        val readPosition = writePosition.backward(size)

        val result = getAtUnsafe(readPosition)
        buffer[readPosition] = null

        size--

        return result
    }

    /**
     * Removes [n] first elements from the buffer or fails with [IllegalArgumentException] if not enough elements in the buffer to remove
     */
    fun removeFirst(n: Int) {
        require(n >= 0) { "n shouldn't be negative but it is $n" }
        require(n <= size) { "n shouldn't be greater than the buffer size: n = $n, size = $size" }

        if (n > 0) {
            val start = writePosition.backward(size)
            val end = start.forward(n - 1)

            for (i in start .. end) {
                buffer[i] = null
            }
            if (start > end) {
                buffer.fill(null, start, capacity)
                buffer.fill(null, 0, end + 1)
            } else {
                buffer.fill(null, start, end + 1)
            }

            size -= n
        }
    }

    /**
     * Removes all elements from the buffer
     */
    fun clear() {
        size = 0
        buffer.fill(null)
    }

    @Suppress("NOTHING_TO_INLINE", "UNCHECKED_CAST")
    private inline fun getAtUnsafe(idx: Int): T = buffer[idx] as T

    @Suppress("NOTHING_TO_INLINE")
    private inline fun Int.forward(n: Int = 1): Int {
        require(n >= 0)
        require(n <= capacity)

        val result = this + n
        return if (result >= capacity) result - capacity else result
    }

    @Suppress("NOTHING_TO_INLINE")
    private inline fun Int.backward(n: Int = 1): Int {
        require(n >= 0)
        require(n <= capacity)

        return if (this < n) (this - n + capacity) else this - n
    }
}