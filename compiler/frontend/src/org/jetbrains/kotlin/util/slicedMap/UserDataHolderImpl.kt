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
        private val cache = SimpleSLRUCache<KeyFMap>(protectedSize = 2000, probationalSize = 2000)
    }
}

private class SimpleSLRUCache<T : Any>(protectedSize: Int, probationalSize: Int) : SLRUCache<T, T>(protectedSize, probationalSize) {
    override fun createValue(key: T?): T? = key
}
