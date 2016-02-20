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

    val buffer = LinkedList<T>()

    fun rollBuffer() {
        if (step >= buffer.size) {
            val bufferSize = buffer.size
            buffer.clear()

            for (i in bufferSize .. step - 1) {
                if (!iterator.hasNext())
                    break
                iterator.next()
            }
        } else {
            repeat(step) {
                buffer.removeAt(0)
            }
        }
    }

    return generateSequence {
        while (buffer.size < size && iterator.hasNext()) {
            buffer.add(iterator.next())
        }

        when {
            !iterator.hasNext() && buffer.size < size && dropTrailing -> null
            !iterator.hasNext() && buffer.isEmpty() -> null
            else -> {
                val part = buffer.toList()

                rollBuffer()

                part
            }
        }
    }
}
