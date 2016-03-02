package text

import org.junit.*
import kotlin.test.*

class RingBufferTest {
    private val buffer = RingBuffer<Int>(5)

    @Test
    fun smokeTest() {
        assertEquals(0, buffer.size)
        assertEquals(5, buffer.capacity)
        assertTrue { buffer.isEmpty() }

        assertEquals(emptyList(), buffer.toList())
    }

    @Test
    fun testAddOnly() {
        for (i in 1..5) {
            assertTrue { buffer.offer(i) }
            assertFalse { buffer.isEmpty() }
        }

        assertFalse { buffer.offer(0) }
        assertTrue { buffer.isFull() }

        assertEquals(listOf(1, 2, 3, 4, 5), buffer.toList())
    }

    @Test
    fun testAddThenRemove() {
        for (i in 1..5) {
            assertTrue { buffer.offer(i) }
        }

        for (i in 1..5) {
            assertEquals(i, buffer.get())
        }

        assertTrue { buffer.isEmpty() }
        assertFails { buffer.get() }

        assertEquals(listOf(), buffer.toList())
    }

    @Test
    fun testAddRemove() {
        for (i in 1..20) {
            assertTrue { buffer.offer(i) }
            assertEquals(i, buffer.get())
        }

        assertFails { buffer.get() }
    }

    @Test
    fun testAddRemoveGrowth() {
        for (j in 1..10) {
            for (i in 1..4) {
                buffer.add(i * 2 - 1)
                buffer.add(i * 2)

                assertEquals(i, buffer.get())
            }

            assertEquals(listOf(5, 6, 7, 8), buffer.toList())
            buffer.clear()

            assertEquals(0, buffer.size)
            assertEquals(emptyList(), buffer.toList())
        }
    }

    @Test
    fun testRemoveFirst() {
        for (j in 1..5) {
            for (i in 1..5) {
                buffer.add(i)
            }

            buffer.removeFirst(0)
            assertEquals((1 .. 5).toList(), buffer.toList())

            buffer.removeFirst(j)
            assertEquals((j + 1 .. 5).toList(), buffer.toList())
            buffer.removeFirst(5 - j)
            assertTrue { buffer.isEmpty() }
        }
    }

    @Test
    fun toArrayTestNoWrap() {
        buffer.add(1)
        buffer.add(2)

        assertEquals(listOf(1, 2), buffer.toArray().toList())

        buffer.add(3)
        assertEquals(listOf(1, 2, 3), buffer.toArray().toList())

        buffer.add(4)
        assertEquals(listOf(1, 2, 3, 4), buffer.toArray().toList())

        buffer.add(5)
        assertEquals(listOf(1, 2, 3, 4, 5), buffer.toArray().toList())
    }

    @Test
    fun toArrayTestWithWrap() {
        for (i in 1..5) {
            buffer.add(i)
        }
        buffer.removeFirst(2)
        assertEquals(listOf(3, 4, 5), buffer.toArray().toList())

        buffer.add(6)
        assertEquals(listOf(3, 4, 5, 6), buffer.toArray().toList())

        buffer.add(7)
        assertEquals(listOf(3, 4, 5, 6, 7), buffer.toArray().toList())

    }
}