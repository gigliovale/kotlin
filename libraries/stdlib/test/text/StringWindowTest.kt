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

package text

import org.junit.*
import kotlin.test.*

class StringWindowTest {

    @Test
    @Suppress("UNUSED_VARIABLE")
    fun testSignature() {
        val windowForString: Sequence<String> = "".slidingWindow(1)
        val windowBackwardForString: Sequence<String> = "".slidingWindowBackward(1)

        val windowForCharSequence: Sequence<CharSequence> = "".cs().slidingWindow(1)
        val windowBackwardForCharSequence: Sequence<CharSequence> = "".cs().slidingWindowBackward(1)

        assertTrue { true }
    }

    @Test
    fun testInvalidArguments() {
        assertFails {
            "".slidingWindow(-1)
        }
        assertFails {
            "".cs().slidingWindow(-1)
        }
        assertFails {
            "".slidingWindow(0, -1)
        }
        assertFails {
            "".cs().slidingWindow(0, -1)
        }
        assertFails {
            "".slidingWindow(0, 0)
        }
        assertFails {
            "".cs().slidingWindow(0, 0)
        }
        assertFails {
            "".slidingWindowBackward(-1)
        }
        assertFails {
            "".cs().slidingWindowBackward(-1)
        }
        assertFails {
            "".slidingWindowBackward(0, -1)
        }
        assertFails {
            "".cs().slidingWindowBackward(0, -1)
        }
        assertFails {
            "".slidingWindowBackward(0, 0)
        }
        assertFails {
            "".cs().slidingWindowBackward(0, 0)
        }
        assertTrue { true } // it is required for JS tests to have at least one assertion
    }

    @Test
    fun testSimpleForward() {
        assertEquals(listOf("a", "b", "c"), "abc".slidingWindow(1).toList())
        assertEquals(listOf("a", "b", "c"), "abc".cs().slidingWindow(1).toList())
        assertEquals(listOf("ab", "c"), "abc".slidingWindow(2).toList())
        assertEquals(listOf("ab", "c"), "abc".cs().slidingWindow(2).toList())
        assertEquals(listOf("abc"), "abc".slidingWindow(3).toList())
        assertEquals(listOf("abc"), "abc".cs().slidingWindow(3).toList())
        assertEquals(listOf("abc"), "abc".slidingWindow(4).toList())
        assertEquals(listOf("abc"), "abc".cs().slidingWindow(4).toList())
    }

    @Test
    fun testForwardDropTrailing() {
        assertEquals(listOf("a", "b", "c"), "abc".slidingWindow(1, dropTrailing = true).toList())
        assertEquals(listOf("a", "b", "c"), "abc".cs().slidingWindow(1, dropTrailing = true).toList())
        assertEquals(listOf("ab"), "abc".slidingWindow(2, dropTrailing = true).toList())
        assertEquals(listOf("ab"), "abc".cs().slidingWindow(2, dropTrailing = true).toList())
        assertEquals(listOf("abc"), "abc".slidingWindow(3, dropTrailing = true).toList())
        assertEquals(listOf("abc"), "abc".cs().slidingWindow(3, dropTrailing = true).toList())
        assertEquals(emptyList(), "abc".slidingWindow(4, dropTrailing = true).toList())
        assertEquals(emptyList(), "abc".cs().slidingWindow(4, dropTrailing = true).toList())
    }

    @Test
    fun testForwardCustomStep() {
        assertEquals(listOf("ab", "bc", "cd", "de", "ef", "f"), "abcdef".slidingWindow(2, step = 1).toList())
        assertEquals(listOf("ab", "bc", "cd", "de", "ef", "f"), "abcdef".cs().slidingWindow(2, step = 1).toList())
        assertEquals(listOf("ab", "de"), "abcdef".slidingWindow(2, step = 3).toList())
        assertEquals(listOf("ab", "de"), "abcdef".cs().slidingWindow(2, step = 3).toList())
    }

    @Test
    fun testForwardCustomStepDropTrailing() {
        assertEquals(listOf("ab", "bc", "cd", "de", "ef"), "abcdef".slidingWindow(2, step = 1, dropTrailing = true).toList())
        assertEquals(listOf("ab", "bc", "cd", "de", "ef"), "abcdef".cs().slidingWindow(2, step = 1, dropTrailing = true).toList())
        assertEquals(listOf("ab", "de"), "abcdef".slidingWindow(2, step = 3, dropTrailing = true).toList())
        assertEquals(listOf("ab", "de"), "abcdef".cs().slidingWindow(2, step = 3, dropTrailing = true).toList())
    }

    @Test
    fun testSimpleBackward() {
        assertEquals(listOf("c", "b", "a"), "abc".slidingWindowBackward(1).toList())
        assertEquals(listOf("c", "b", "a"), "abc".cs().slidingWindowBackward(1).toList())

        assertEquals(listOf("bc", "a"), "abc".slidingWindowBackward(2).toList())
        assertEquals(listOf("bc", "a"), "abc".cs().slidingWindowBackward(2).toList())

        assertEquals(listOf("abc"), "abc".slidingWindowBackward(3).toList())
        assertEquals(listOf("abc"), "abc".cs().slidingWindowBackward(3).toList())

        assertEquals(listOf("abc"), "abc".slidingWindowBackward(4).toList())
        assertEquals(listOf("abc"), "abc".cs().slidingWindowBackward(4).toList())
    }

    @Test
    fun testBackwardDropTrailing() {
        assertEquals(listOf("c", "b", "a"), "abc".slidingWindowBackward(1, dropTrailing = true).toList())
        assertEquals(listOf("c", "b", "a"), "abc".cs().slidingWindowBackward(1, dropTrailing = true).toList())

        assertEquals(listOf("bc"), "abc".slidingWindowBackward(2, dropTrailing = true).toList())
        assertEquals(listOf("bc"), "abc".cs().slidingWindowBackward(2, dropTrailing = true).toList())

        assertEquals(listOf("abc"), "abc".slidingWindowBackward(3, dropTrailing = true).toList())
        assertEquals(listOf("abc"), "abc".cs().slidingWindowBackward(3, dropTrailing = true).toList())

        assertEquals(emptyList(), "abc".slidingWindowBackward(4, dropTrailing = true).toList())
        assertEquals(emptyList(), "abc".cs().slidingWindowBackward(4, dropTrailing = true).toList())
    }

    @Test
    fun testBackwardCustomStep() {
        assertEquals(listOf("ef", "de", "cd", "bc", "ab", "a"), "abcdef".slidingWindowBackward(2, step = 1).toList())
        assertEquals(listOf("ef", "de", "cd", "bc", "ab", "a"), "abcdef".cs().slidingWindowBackward(2, step = 1).toList())
        assertEquals(listOf("ef", "bc"), "abcdef".slidingWindowBackward(2, step = 3).toList())
        assertEquals(listOf("ef", "bc"), "abcdef".cs().slidingWindowBackward(2, step = 3).toList())
    }

    @Test
    fun testBackwardCustomStepDropTrailing() {
        assertEquals(listOf("ef", "de", "cd", "bc", "ab"), "abcdef".slidingWindowBackward(2, step = 1, dropTrailing = true).toList())
        assertEquals(listOf("ef", "de", "cd", "bc", "ab"), "abcdef".cs().slidingWindowBackward(2, step = 1, dropTrailing = true).toList())

        assertEquals(listOf("ef", "bc"), "abcdef".slidingWindowBackward(2, step = 3, dropTrailing = true).toList())
        assertEquals(listOf("ef", "bc"), "abcdef".cs().slidingWindowBackward(2, step = 3, dropTrailing = true).toList())
    }

    @Test
    fun testForwardEmpty() {
        assertEquals(emptyList(), "".slidingWindow(1).toList())
        assertEquals(emptyList(), "".cs().slidingWindow(1).toList())
        assertEquals(emptyList(), "".slidingWindow(2).toList())
        assertEquals(emptyList(), "".cs().slidingWindow(2).toList())
        assertEquals(emptyList(), "".slidingWindow(2, step = 1).toList())
        assertEquals(emptyList(), "".cs().slidingWindow(2, step = 1).toList())
        assertEquals(emptyList(), "".slidingWindow(2, step = 2).toList())
        assertEquals(emptyList(), "".cs().slidingWindow(2, step = 2).toList())
        assertEquals(emptyList(), "".slidingWindow(2, step = 3).toList())
        assertEquals(emptyList(), "".cs().slidingWindow(2, step = 3).toList())

        assertEquals(listOf("", "", ""), "abc".slidingWindow(0).toList())
        assertEquals(listOf("", "", ""), "abc".cs().slidingWindow(0).toList())
        assertEquals(listOf("", ""), "abc".slidingWindow(0, step = 2).toList())
        assertEquals(listOf("", ""), "abc".cs().slidingWindow(0, step = 2).toList())
        assertEquals(listOf(""), "abc".slidingWindow(0, step = 3).toList())
        assertEquals(listOf(""), "abc".cs().slidingWindow(0, step = 3).toList())
    }

    @Test
    fun testBackwardEmpty() {
        for (size in 1..2) {
            for (step in 1..3) {
                assertEquals(emptyList(), "".slidingWindowBackward(size, step).toList())
                assertEquals(emptyList(), "".cs().slidingWindowBackward(size, step).toList())
            }
        }

        assertEquals(listOf("", "", ""), "abc".slidingWindowBackward(0).toList())
        assertEquals(listOf("", "", ""), "abc".cs().slidingWindowBackward(0).toList())

        assertEquals(listOf("", ""), "abc".slidingWindowBackward(0, step = 2).toList())
        assertEquals(listOf("", ""), "abc".cs().slidingWindowBackward(0, step = 2).toList())
        assertEquals(listOf(""), "abc".slidingWindowBackward(0, step = 3).toList())
        assertEquals(listOf(""), "abc".cs().slidingWindowBackward(0, step = 3).toList())
    }

    @Test
    fun testSomeText() {
        val part = "The quick brown fox jumps over the lazy dog. "

        for (repeat in 1..30) {
            val text = part.repeat(repeat)

            for (windowSize in 1..part.length) {
                val concat = text.slidingWindow(windowSize).joinToString("")

                assertEquals(text, concat)
            }

            for (windowSize in 1..part.length) {
                val concat = text.cs().slidingWindow(windowSize).joinToString("")

                assertEquals(text, concat)
            }
        }
    }

    private fun String.repeat(times: Int) = buildString {
        for (i in 1 .. times) {
            append(this@repeat)
        }
    }

    private fun String.cs(): CharSequence = this
}
