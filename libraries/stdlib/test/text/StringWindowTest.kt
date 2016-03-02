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
        val windowForString: Sequence<String> = "".window(1)
        val windowBackwardForString: Sequence<String> = "".windowBackward(1)

        val windowForCharSequence: Sequence<CharSequence> = "".cs().window(1)
        val windowBackwardForCharSequence: Sequence<CharSequence> = "".cs().windowBackward(1)

        assertTrue { true }
    }

    @Test
    fun testInvalidArguments() {
        assertFails {
            "".window(-1)
        }
        assertFails {
            "".cs().window(-1)
        }
        assertFails {
            "".window(0, -1)
        }
        assertFails {
            "".cs().window(0, -1)
        }
        assertFails {
            "".window(0, 0)
        }
        assertFails {
            "".cs().window(0, 0)
        }
        assertFails {
            "".windowBackward(-1)
        }
        assertFails {
            "".cs().windowBackward(-1)
        }
        assertFails {
            "".windowBackward(0, -1)
        }
        assertFails {
            "".cs().windowBackward(0, -1)
        }
        assertFails {
            "".windowBackward(0, 0)
        }
        assertFails {
            "".cs().windowBackward(0, 0)
        }
        assertTrue { true } // it is required for JS tests to have at least one assertion
    }

    @Test
    fun testSimpleForward() {
        assertEquals(listOf("a", "b", "c"), "abc".window(1).toList())
        assertEquals(listOf("a", "b", "c"), "abc".cs().window(1).toList())
        assertEquals(listOf("ab", "c"), "abc".window(2).toList())
        assertEquals(listOf("ab", "c"), "abc".cs().window(2).toList())
        assertEquals(listOf("abc"), "abc".window(3).toList())
        assertEquals(listOf("abc"), "abc".cs().window(3).toList())
        assertEquals(listOf("abc"), "abc".window(4).toList())
        assertEquals(listOf("abc"), "abc".cs().window(4).toList())
    }

    @Test
    fun testForwardDropTrailing() {
        assertEquals(listOf("a", "b", "c"), "abc".window(1, dropTrailing = true).toList())
        assertEquals(listOf("a", "b", "c"), "abc".cs().window(1, dropTrailing = true).toList())
        assertEquals(listOf("ab"), "abc".window(2, dropTrailing = true).toList())
        assertEquals(listOf("ab"), "abc".cs().window(2, dropTrailing = true).toList())
        assertEquals(listOf("abc"), "abc".window(3, dropTrailing = true).toList())
        assertEquals(listOf("abc"), "abc".cs().window(3, dropTrailing = true).toList())
        assertEquals(emptyList(), "abc".window(4, dropTrailing = true).toList())
        assertEquals(emptyList(), "abc".cs().window(4, dropTrailing = true).toList())
    }

    @Test
    fun testForwardCustomStep() {
        assertEquals(listOf("ab", "bc", "cd", "de", "ef", "f"), "abcdef".window(2, step = 1).toList())
        assertEquals(listOf("ab", "bc", "cd", "de", "ef", "f"), "abcdef".cs().window(2, step = 1).toList())
        assertEquals(listOf("ab", "de"), "abcdef".window(2, step = 3).toList())
        assertEquals(listOf("ab", "de"), "abcdef".cs().window(2, step = 3).toList())
    }

    @Test
    fun testForwardCustomStepDropTrailing() {
        assertEquals(listOf("ab", "bc", "cd", "de", "ef"), "abcdef".window(2, step = 1, dropTrailing = true).toList())
        assertEquals(listOf("ab", "bc", "cd", "de", "ef"), "abcdef".cs().window(2, step = 1, dropTrailing = true).toList())
        assertEquals(listOf("ab", "de"), "abcdef".window(2, step = 3, dropTrailing = true).toList())
        assertEquals(listOf("ab", "de"), "abcdef".cs().window(2, step = 3, dropTrailing = true).toList())
    }

    @Test
    fun testSimpleBackward() {
        assertEquals(listOf("c", "b", "a"), "abc".windowBackward(1).toList())
        assertEquals(listOf("c", "b", "a"), "abc".cs().windowBackward(1).toList())

        assertEquals(listOf("bc", "a"), "abc".windowBackward(2).toList())
        assertEquals(listOf("bc", "a"), "abc".cs().windowBackward(2).toList())

        assertEquals(listOf("abc"), "abc".windowBackward(3).toList())
        assertEquals(listOf("abc"), "abc".cs().windowBackward(3).toList())

        assertEquals(listOf("abc"), "abc".windowBackward(4).toList())
        assertEquals(listOf("abc"), "abc".cs().windowBackward(4).toList())
    }

    @Test
    fun testBackwardDropTrailing() {
        assertEquals(listOf("c", "b", "a"), "abc".windowBackward(1, dropTrailing = true).toList())
        assertEquals(listOf("c", "b", "a"), "abc".cs().windowBackward(1, dropTrailing = true).toList())

        assertEquals(listOf("bc"), "abc".windowBackward(2, dropTrailing = true).toList())
        assertEquals(listOf("bc"), "abc".cs().windowBackward(2, dropTrailing = true).toList())

        assertEquals(listOf("abc"), "abc".windowBackward(3, dropTrailing = true).toList())
        assertEquals(listOf("abc"), "abc".cs().windowBackward(3, dropTrailing = true).toList())

        assertEquals(emptyList(), "abc".windowBackward(4, dropTrailing = true).toList())
        assertEquals(emptyList(), "abc".cs().windowBackward(4, dropTrailing = true).toList())
    }

    @Test
    fun testBackwardCustomStep() {
        assertEquals(listOf("ef", "de", "cd", "bc", "ab", "a"), "abcdef".windowBackward(2, step = 1).toList())
        assertEquals(listOf("ef", "de", "cd", "bc", "ab", "a"), "abcdef".cs().windowBackward(2, step = 1).toList())
        assertEquals(listOf("ef", "bc"), "abcdef".windowBackward(2, step = 3).toList())
        assertEquals(listOf("ef", "bc"), "abcdef".cs().windowBackward(2, step = 3).toList())
    }

    @Test
    fun testBackwardCustomStepDropTrailing() {
        assertEquals(listOf("ef", "de", "cd", "bc", "ab"), "abcdef".windowBackward(2, step = 1, dropTrailing = true).toList())
        assertEquals(listOf("ef", "de", "cd", "bc", "ab"), "abcdef".cs().windowBackward(2, step = 1, dropTrailing = true).toList())

        assertEquals(listOf("ef", "bc"), "abcdef".windowBackward(2, step = 3, dropTrailing = true).toList())
        assertEquals(listOf("ef", "bc"), "abcdef".cs().windowBackward(2, step = 3, dropTrailing = true).toList())
    }

    @Test
    fun testForwardEmpty() {
        assertEquals(emptyList(), "".window(1).toList())
        assertEquals(emptyList(), "".cs().window(1).toList())
        assertEquals(emptyList(), "".window(2).toList())
        assertEquals(emptyList(), "".cs().window(2).toList())
        assertEquals(emptyList(), "".window(2, step = 1).toList())
        assertEquals(emptyList(), "".cs().window(2, step = 1).toList())
        assertEquals(emptyList(), "".window(2, step = 2).toList())
        assertEquals(emptyList(), "".cs().window(2, step = 2).toList())
        assertEquals(emptyList(), "".window(2, step = 3).toList())
        assertEquals(emptyList(), "".cs().window(2, step = 3).toList())

        assertEquals(listOf("", "", ""), "abc".window(0).toList())
        assertEquals(listOf("", "", ""), "abc".cs().window(0).toList())
        assertEquals(listOf("", ""), "abc".window(0, step = 2).toList())
        assertEquals(listOf("", ""), "abc".cs().window(0, step = 2).toList())
        assertEquals(listOf(""), "abc".window(0, step = 3).toList())
        assertEquals(listOf(""), "abc".cs().window(0, step = 3).toList())
    }

    @Test
    fun testBackwardEmpty() {
        for (size in 1..2) {
            for (step in 1..3) {
                assertEquals(emptyList(), "".windowBackward(size, step).toList())
                assertEquals(emptyList(), "".cs().windowBackward(size, step).toList())
            }
        }

        assertEquals(listOf("", "", ""), "abc".windowBackward(0).toList())
        assertEquals(listOf("", "", ""), "abc".cs().windowBackward(0).toList())

        assertEquals(listOf("", ""), "abc".windowBackward(0, step = 2).toList())
        assertEquals(listOf("", ""), "abc".cs().windowBackward(0, step = 2).toList())
        assertEquals(listOf(""), "abc".windowBackward(0, step = 3).toList())
        assertEquals(listOf(""), "abc".cs().windowBackward(0, step = 3).toList())
    }

    @Test
    fun testSomeText() {
        val part = "The quick brown fox jumps over the lazy dog. "

        for (repeat in 1..30) {
            val text = part.repeat(repeat)

            for (windowSize in 1..part.length) {
                val concat = text.window(windowSize).joinToString("")

                assertEquals(text, concat)
            }

            for (windowSize in 1..part.length) {
                val concat = text.cs().window(windowSize).joinToString("")

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
