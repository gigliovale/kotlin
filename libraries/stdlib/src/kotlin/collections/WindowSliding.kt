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

package kotlin.collections

import java.util.*

internal fun windowImpl(sourceSize: Int, size: Int, step: Int, dropTrailing: Boolean): Sequence<IntRange> {
    require(size >= 0) { "size should not be negative" }
    require(step != 0) { "step shouldn't be zero" }

    if (sourceSize == 0 || (size > sourceSize && dropTrailing)) {
        return emptySequence()
    }
    if (size == 0) {
        return when {
            step > 0 -> (0 .. sourceSize - 1 step step)
            else -> (sourceSize - 1 downTo 0 step -step)
        }.asSequence().map { it .. it - 1 } // empty ranges with valid start
    }

    var currentIndex = when {
        step > 0 -> 0
        else -> sourceSize - size
    }

    return generateSequence {
        val startIndex = currentIndex
        val endExclusive = currentIndex + size

        when {
            startIndex >= sourceSize -> null
            endExclusive > sourceSize && dropTrailing -> null
            startIndex < 0 && dropTrailing -> null
            step < 0 && endExclusive <= 0 -> null
            else -> {
                currentIndex += step
                startIndex.coerceAtLeast(0) .. endExclusive.coerceAtMost(sourceSize) - 1
            }
        }
    }
}

internal fun <T> windowForwardOnlySequenceImpl(iterator: Iterator<T>, size: Int, step: Int, dropTrailing: Boolean): Sequence<List<T>> {
    require(size >= 0) { "size should not be negative" }
    require(step > 0) { "step should be positive non zero" }

    return if (step >= size) {
        windowForwardWithGap(iterator, size, step, dropTrailing)
    } else {
        windowForwardWithOverlap(iterator, size, step, dropTrailing)
    }
}

private fun <T> windowForwardWithGap(iterator: Iterator<T>, size: Int, step: Int, dropTrailing: Boolean): Sequence<List<T>> {
    require(step >= size)
    var first = true
    val gap = step - size

    return generateSequence {
        if (first) {
            first = false
        } else {
            for (skip in 1..gap) {
                if (!iterator.hasNext()) {
                    break
                }
                iterator.next()
            }
        }

        val buffer = ArrayList<T>(size)
        for (i in 1..size) {
            if (!iterator.hasNext()) {
                break
            }
            buffer.add(iterator.next())
        }

        when {
            buffer.isEmpty() && !iterator.hasNext() -> null
            buffer.size < size && dropTrailing -> null
            else -> buffer
        }
    }
}

private fun <T> windowForwardWithOverlap(iterator: Iterator<T>, size: Int, step: Int, dropTrailing: Boolean): Sequence<List<T>> {
    require(step < size)

    val buffer = RingBuffer<T>(size)

    return generateSequence {
        if (buffer.size >= step) {
            buffer.removeFirst(step)
        }

        while (!buffer.isFull() && iterator.hasNext()) {
            buffer.add(iterator.next())
        }

        when {
            buffer.isEmpty() && !iterator.hasNext() -> null
            !buffer.isFull() && dropTrailing -> null
            else -> buffer.toList()
        }
    }
}