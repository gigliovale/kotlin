/*
 * Copyright 2010-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package test.collections

import kotlin.test.*
import org.junit.Test
import java.util.*

class GenerateTest {
    @Test
    fun testSimple() {
        val result = generate<Int> {
            for (i in 1..3) {
                yield(2 * i)
            }
        }

        assertEquals(listOf(2, 4, 6), result.toList())
        // Repeated calls also work
        assertEquals(listOf(2, 4, 6), result.toList())
    }

    @Test
    fun testCallHasNextSeveralTimes() {
        val result = generate<Int> {
            yield(1)
        }

        val iterator = result.iterator()

        assertTrue(iterator.hasNext())
        assertTrue(iterator.hasNext())
        assertTrue(iterator.hasNext())

        assertEquals(1, iterator.next())

        assertFalse(iterator.hasNext())
        assertFalse(iterator.hasNext())
        assertFalse(iterator.hasNext())

        assertTrue(assertFails { iterator.next() } is NoSuchElementException)
    }

    @Test
    fun testManualIteration() {
        val result = generate<Int> {
            yield(1)
            yield(2)
            yield(3)
        }

        val iterator = result.iterator()

        assertTrue(iterator.hasNext())
        assertTrue(iterator.hasNext())
        assertEquals(1, iterator.next())

        assertTrue(iterator.hasNext())
        assertTrue(iterator.hasNext())
        assertEquals(2, iterator.next())

        assertEquals(3, iterator.next())

        assertFalse(iterator.hasNext())
        assertFalse(iterator.hasNext())

        assertTrue(assertFails { iterator.next() } is NoSuchElementException)

        assertEquals(1, result.iterator().next())
    }

    @Test
    fun testEmptySequence() {
        val result = generate<Int> {}
        val iterator = result.iterator()

        assertFalse(iterator.hasNext())
        assertFalse(iterator.hasNext())

        assertTrue(assertFails { iterator.next() } is NoSuchElementException)
    }

    @Test
    fun testLaziness() {
        var sharedVar = -2
        val result = generate<Int> {
            while (true) {
                when (sharedVar) {
                    -1 -> return@generate
                    -2 -> error("Invalid state: -2")
                    else -> yield(sharedVar)
                }
            }
        }

        val iterator = result.iterator()

        sharedVar = 1
        assertTrue(iterator.hasNext())
        assertEquals(1, iterator.next())

        sharedVar = 2
        assertTrue(iterator.hasNext())
        assertEquals(2, iterator.next())

        sharedVar = 3
        assertTrue(iterator.hasNext())
        assertEquals(3, iterator.next())

        sharedVar = -1
        assertFalse(iterator.hasNext())
        assertTrue(assertFails { iterator.next() } is NoSuchElementException)
    }

    @Test
    fun testExceptionInCoroutine() {
        var sharedVar = -2
        val result = generate<Int> {
            while (true) {
                when (sharedVar) {
                    -1 -> return@generate
                    -2 -> error("Invalid state: -2")
                    else -> yield(sharedVar)
                }
            }
        }

        val iterator = result.iterator()

        sharedVar = 1
        assertEquals(1, iterator.next())

        sharedVar = -2
        assertTrue(assertFails { iterator.hasNext() } is IllegalStateException)
    }

    @Test
    fun testParallelIteration() {
        var inc = 0
        val result = generate<Int> {
            for (i in 1..3) {
                inc++
                yield(inc * i)
            }
        }

        assertEquals(listOf(Pair(1, 2), Pair(6, 8), Pair(15, 18)), result.zip(result).toList())
    }
}