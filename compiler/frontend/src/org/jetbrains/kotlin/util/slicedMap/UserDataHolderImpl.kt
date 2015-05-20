/*
 * Copyright 2010-2015 JetBrains s.r.o.
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

package org.jetbrains.kotlin.util.slicedMap

import com.intellij.openapi.util.Key
import com.intellij.util.containers.SLRUCache
import org.jetbrains.kotlin.util.userDataHolder.UserDataHolderBase
import org.jetbrains.kotlin.util.userDataHolder.keyFMap.KeyFMap
import org.jetbrains.kotlin.util.userDataHolder.keyFMap.OneElementFMap
import java.lang.ref.Reference
import java.lang.ref.ReferenceQueue
import java.lang.ref.SoftReference
import kotlin.platform.platformName

class UserDataHolderImpl : UserDataHolderBase() {
    val keys: Array<Key<*>>
        get() = getUserMap().getKeys()

    override fun changeUserMap(oldMap: KeyFMap?, newMap: KeyFMap?): Boolean {
        val mapToAdd =
                if (newMap is OneElementFMap<*> && newMap.getValue() !is Collection<*>) {
                    cache[newMap]
                }
                else {
                    newMap
                }

        return super.changeUserMap(oldMap, mapToAdd)
    }

    companion object {
        private val cache = ReferenceSLRUCacheOfKeyFMap(protectedSize = 2000, probationalSize = 2000)
    }
}

private class ReferenceSLRUCacheOfKeyFMap(protectedSize: Int, probationalSize: Int) : ReferenceSLRUCache<KeyFMap>(protectedSize, probationalSize) {
    override fun createReference(element: KeyFMap, queue: ReferenceQueue<KeyFMap>) = ReferenceToKeyFMap(element, queue)
}

private abstract class ReferenceSLRUCache<T : Any>(protectedSize: Int, probationalSize: Int) {
    private val queue = ReferenceQueue<T>()

    protected abstract fun createReference(element: T, queue: ReferenceQueue<T>): Reference<out T>

    public fun get(element: T): T {
        val ref = createReference(element, queue)
        val cached = cache.get(ref).get()
        if (cached != null) return cached

        cache.put(ref, ref)

        return element
    }

    private val cache = object : SLRUCache<Reference<out T>, Reference<out T>>(protectedSize, probationalSize) {

        override fun put(key: Reference<out T>, value: Reference<out T>) {
            cleanupReferences()
            super.put(key, value)
        }

        override fun putToProtectedQueue(key: Reference<out T>, value: Reference<out T>) {
            cleanupReferences()
            super.putToProtectedQueue(key, value)
        }

        private fun cleanupReferences() {
            while (true) {
                val ref = queue.poll() ?: break
                remove(ref)
            }
        }

        override fun createValue(key: Reference<out T>?): Reference<out T>? = key
    }
}

private class ReferenceToKeyFMap(referent: KeyFMap, queue: ReferenceQueue<KeyFMap>? = null) : SoftReference<KeyFMap>(referent, queue) {
    private val hashCode = computeHashCode(referent)

    private fun computeHashCode(o: KeyFMap) = if (o is OneElementFMap<*>) o.hashCode() else 0

    override fun hashCode(): Int = hashCode

    override fun equals(other: Any?): Boolean {
        if (other === this) return true

        if (other !is ReferenceToKeyFMap || hashCode != other.hashCode) return false

        val o1 = get()
        val o2 = other.get()
        if (o1 == null || o2 == null) return false

        if (o1 is OneElementFMap<*>) {
            return o1.equals(o2)
        }

        return false
    }
}
