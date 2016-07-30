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

package org.jetbrains.kotlin.storage

import org.jetbrains.kotlin.storage.StorageUtils.NotValue

class LocklessStorageManager(val recursionFallThrough: Boolean = false) : StorageManager {
    override fun <K, V : Any> createMemoizedFunction(compute: (K) -> V) = LocklessMemoizedFunctionToNotNull<K, V>(compute)

    override fun <K, V : Any> createMemoizedFunctionWithNullableValues(compute: (K) -> V?) = LocklessMemoizedFunctionToNullable<K, V>(compute)

    override fun <K, V : Any> createCacheWithNullableValues(): CacheWithNullableValues<K, V> = LocklessCacheWithNullableValues()

    override fun <K, V : Any> createCacheWithNotNullValues(): CacheWithNotNullValues<K, V> = LocklessCacheWithNotNullValues()

    override fun <T : Any> createLazyValue(computable: () -> T) = LocklessLazyValue(computable, recursionFallThrough)

    override fun <T : Any> createRecursionTolerantLazyValue(computable: () -> T, onRecursiveCall: T): NotNullLazyValue<T> =
        LocklessRecursionTolerantLazyValue<T>(computable, onRecursiveCall)

    override fun <T : Any> createLazyValueWithPostCompute(computable: () -> T, onRecursiveCall: ((Boolean) -> T)?, postCompute: (T) -> Unit): NotNullLazyValue<T> =
        LocklessLazyValueWithPostCompute(computable, onRecursiveCall, postCompute, recursionFallThrough)

    override fun <T : Any> createNullableLazyValue(computable: () -> T?): NullableLazyValue<T> = LocklessNullableLazyValue(computable, recursionFallThrough)

    override fun <T : Any> createRecursionTolerantNullableLazyValue(computable: () -> T?, onRecursiveCall: T?): NullableLazyValue<T> =
        LocklessRecursionTolerantNullableLazyValue<T>(computable, onRecursiveCall)

    override fun <T : Any> createNullableLazyValueWithPostCompute(computable: () -> T?, postCompute: (T?) -> Unit): NullableLazyValue<T> =
        LocklessNullableLazyValueWithPostCompute(computable, postCompute)

    override fun <T> compute(computable: () -> T) = computable()
}

class LocklessMemoizedFunctionToNullable<in P, out R : Any>(private val compute: (P) -> R?) : MemoizedFunctionToNullable<P, R> {
    private val map = hashMapOf<P, Any>()

    override fun isComputed(key: P): Boolean {
        val value = map[key]
        return value != null && value != NotValue.COMPUTING
    }

    override fun invoke(key: P): R? {
        val value = map[key]
        if (value != null) {
            if (value == NotValue.COMPUTING) {
                throw StorageUtils.sanitizeStackTrace(AssertionError("Recursion detected on input: $key"))
            }
            if (value == NotValue.NULL) return null
            return value as R
        }
        map[key] = NotValue.COMPUTING
        val newValue = compute(key)
        map[key] = newValue ?: NotValue.NULL
        return newValue
    }
}

class LocklessMemoizedFunctionToNotNull<in P, out R : Any>(private val compute: (P) -> R) : MemoizedFunctionToNotNull<P, R> {
    private val map = hashMapOf<P, Any>()

    override fun isComputed(key: P): Boolean {
        val value = map[key]
        return value != null && value != NotValue.COMPUTING
    }

    override fun invoke(key: P): R {
        val value = map[key]
        if (value != null) {
            if (value == NotValue.COMPUTING) {
                throw StorageUtils.sanitizeStackTrace(AssertionError("Recursion detected on input: $key"))
            }
            return value as R
        }
        map[key] = NotValue.COMPUTING
        val newValue = compute(key)
        map[key] = newValue
        return newValue
    }
}

class LocklessLazyValue<T : Any>(private val computable: () -> T, private val recursionFallThrough: Boolean) : NotNullLazyValue<T> {
    private var value: Any = NotValue.NOT_COMPUTED

    override fun invoke(): T {
        if (value !is NotValue) {
            return value as T
        }
        if (value == NotValue.COMPUTING && !recursionFallThrough) {
            throw StorageUtils.sanitizeStackTrace(IllegalStateException("Recursion detected when calculating lazy value: $computable"))
        }
        value = NotValue.COMPUTING
        val newValue = computable()
        value = newValue
        return value as T
    }

    override fun isComputed() = value !is NotValue

    override fun isComputing() = value == NotValue.COMPUTING
}

private class LocklessNullableLazyValue<T : Any>(private val computable: () -> T?, private val recursionFallThrough: Boolean) : NullableLazyValue<T> {
    private var value: Any? = NotValue.NOT_COMPUTED

    override fun invoke(): T? {
        if (value !is NotValue) {
            return value as T?
        }
        if (value == NotValue.COMPUTING && !recursionFallThrough) {
            throw StorageUtils.sanitizeStackTrace(IllegalStateException("Recursion detected when calculating lazy value: $computable"))
        }
        value = NotValue.COMPUTING
        val newValue = computable()
        value = newValue
        return value as T?
    }

    override fun isComputed() = value !is NotValue

    override fun isComputing() = value == NotValue.COMPUTING
}

class LocklessRecursionTolerantLazyValue<T : Any>(private val computable: () -> T,
                                                  private val onRecursiveCall: T) : NotNullLazyValue<T> {
    private var value: Any = NotValue.NOT_COMPUTED

    override fun invoke(): T {
        if (value !is NotValue) {
            return value as T
        }
        if (value == NotValue.COMPUTING) {
            return onRecursiveCall
        }
        value = NotValue.COMPUTING
        val newValue = computable()
        value = newValue
        return value as T
    }

    override fun isComputed() = value !is NotValue

    override fun isComputing() = value == NotValue.COMPUTING
}


private class LocklessRecursionTolerantNullableLazyValue<T : Any>(private val computable: () -> T?,
                                                                  private val onRecursiveCall: T?) : NullableLazyValue<T> {
    private var value: Any? = NotValue.NOT_COMPUTED

    override fun invoke(): T? {
        if (value !is NotValue) {
            return value as T?
        }
        if (value == NotValue.COMPUTING) {
            return onRecursiveCall
        }
        value = NotValue.COMPUTING
        val newValue = computable()
        value = newValue
        return value as T?
    }

    override fun isComputed() = value !is NotValue

    override fun isComputing() = value == NotValue.COMPUTING
}

class LocklessLazyValueWithPostCompute<T : Any>(val computable: () -> T,
                                                val onRecursiveCall: ((Boolean) -> T)?,
                                                val postCompute: (T) -> Unit,
                                                val recursionFallThrough: Boolean) : NotNullLazyValue<T> {
    private var value: Any = NotValue.NOT_COMPUTED

    override fun invoke(): T {
        if (value !is NotValue) {
            return value as T
        }
        if (value == NotValue.COMPUTING) {
            if (onRecursiveCall == null && !recursionFallThrough) {
                throw StorageUtils.sanitizeStackTrace(IllegalStateException("Recursion detected when calculating lazy value: $computable"))
            }
            value = NotValue.RECURSION_WAS_DETECTED
            return onRecursiveCall!!(/* firstTime = */ true)
        }
        if (value == NotValue.RECURSION_WAS_DETECTED) {
            return onRecursiveCall!!(/* firstTime = */ false)
        }

        value = NotValue.COMPUTING
        val newValue = computable()
        value = newValue
        postCompute(newValue)
        return value as T
    }

    override fun isComputed() = value !is NotValue

    override fun isComputing() = value == NotValue.COMPUTING
}

class LocklessNullableLazyValueWithPostCompute<T : Any>(val computable: () -> T?,
                                                        val postCompute: (T?) -> Unit) : NullableLazyValue<T> {
    private var value: Any? = NotValue.NOT_COMPUTED

    override fun invoke(): T? {
        if (value !is NotValue) {
            return value as T?
        }
        if (value == NotValue.COMPUTING) {
            throw StorageUtils.sanitizeStackTrace(IllegalStateException("Recursion detected when calculating lazy value: $computable"))
        }

        value = NotValue.COMPUTING
        val newValue = computable()
        postCompute(newValue)
        value = newValue
        return value as T?
    }

    override fun isComputed() = value !is NotValue

    override fun isComputing() = value == NotValue.COMPUTING
}

private class LocklessCacheWithNullableValues<K, V : Any> : CacheWithNullableValues<K, V> {
    private val fn = LocklessMemoizedFunctionToNullable<StorageUtils.KeyWithComputation<K, V?>, V>({k -> k.computation()})

    override fun computeIfAbsent(key: K, computation: () -> V?): V? = fn(StorageUtils.KeyWithComputation(key, computation))
}

private class LocklessCacheWithNotNullValues<K, V : Any> : CacheWithNotNullValues<K, V> {
    private val fn = LocklessMemoizedFunctionToNotNull<StorageUtils.KeyWithComputation<K, V>, V>({k -> k.computation()})

    override fun computeIfAbsent(key: K, computation: () -> V): V = fn(StorageUtils.KeyWithComputation(key, computation))
}
