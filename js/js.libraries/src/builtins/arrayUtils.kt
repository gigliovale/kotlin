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

// a package is omitted to get declarations directly under the module

@PublishedApi
external internal fun <T> Array(size: Int): Array<T>

@JsName("newArray")
fun <T> newArray(size: Int, initValue: T) = fillArrayVal(Array<T>(size), initValue)

@JsName("newArrayF")
inline fun <T> arrayWithFun(size: Int, init: (Int) -> T) = fillArrayFun(Array<T>(size), init)

@JsName("fillArray")
inline fun <T> fillArrayFun(array: Array<T>, init: (Int) -> T): Array<T> {
    for (i in 0..array.size - 1) {
        array[i] = init(i)
    }
    return array
}

@JsName("booleanArray")
fun booleanArray(size: Int, init: Boolean = true): Array<Boolean> {
    val result = newBooleanArray(size)
    if (init) {
        fillArrayVal(result, false)
    }
    return result
}

@JsName("booleanArrayF")
inline fun booleanArrayWithFun(size: Int, init: (Int) -> Boolean) = fillArrayFun(newBooleanArray(size), init)

@JsName("charArray")
@Suppress("UNUSED_PARAMETER")
fun charArray(size: Int): Array<Char> {
    val result = js("new Uint16Array(size)")
    result.`$type$` = "CharArray"
    return result
}

@JsName("charArrayF")
inline fun charArrayWithFun(size: Int, init: (Int) -> Char) = fillArrayFun(charArray(size), init)

@JsName("longArray")
fun longArray(size: Int, init: Boolean = true): Array<Long> {
    val result: dynamic = newLongArray(size)
    if (init) {
        fillArrayVal(result, 0L)
    }
    return result
}

@JsName("longArrayF")
inline fun longArrayWithFun(size: Int, init: (Int) -> Boolean) = fillArrayFun(newLongArray(size), init)

inline fun newBooleanArray(size: Int): dynamic {
    val result: dynamic = Array<Boolean>(size)
    result.`$type$` = "BooleanArray"
    return result
}

inline fun newLongArray(size: Int): dynamic {
    val result: dynamic = Array<Long>(size)
    result.`$type$` = "LongArray"
    return result
}

private fun <T> fillArrayVal(array: Array<T>, initValue: T): Array<T> {
    for (i in 0..array.size - 1) {
        array[i] = initValue
    }
    return array
}