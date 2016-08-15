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

package org.jetbrains.upsource.kotlin.cache

import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.caches.resolve.KotlinCacheService
import org.jetbrains.kotlin.idea.caches.resolve.KotlinCacheServiceImpl
import org.jetbrains.kotlin.psi.KtElement
import java.util.*
import java.util.concurrent.ConcurrentHashMap

class PooledCacheService(private val project: Project) : KotlinCacheService {
    companion object {
        // increasing this value well lead to less lock contention and more memory consumption, should probably be tied to number of cores on the machine or number of threads that are running resolutions tasks
        private val POOL_SIZE = 8
    }

    private val pool = ArrayList<KotlinCacheService>(POOL_SIZE).apply {
        repeat(POOL_SIZE) {
            add(KotlinCacheServiceImpl(project))
        }
    }

    // it would be beneficial to assign the same service to the threads processing the same file, provided this solution actually works
    private val threadIdToService = ConcurrentHashMap<Long, KotlinCacheService>()
    private var current = 0

    private fun getCache(): KotlinCacheService {
        val threadId = Thread.currentThread().id
        val result = threadIdToService[threadId] ?: getNextCache()
        return threadIdToService.putIfAbsent(threadId, result) ?: result
    }

    @Synchronized private fun getNextCache(): KotlinCacheService {
        val result = pool[current]
        current = (current + 1) % POOL_SIZE
        return result
    }

    override fun getResolutionFacade(elements: List<KtElement>) = getCache().getResolutionFacade(elements)

    override fun getSuppressionCache() = getCache().getSuppressionCache()
}