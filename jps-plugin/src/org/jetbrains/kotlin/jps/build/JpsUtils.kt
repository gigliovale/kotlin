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

package org.jetbrains.kotlin.jps.build

import org.jetbrains.jps.incremental.ModuleBuildTarget
import org.jetbrains.jps.model.java.JpsJavaClasspathKind
import org.jetbrains.jps.model.java.JpsJavaDependenciesEnumerator
import org.jetbrains.jps.model.java.JpsJavaExtensionService
import org.jetbrains.jps.model.library.JpsOrderRootType
import org.jetbrains.jps.model.module.JpsModule
import org.jetbrains.jps.util.JpsPathUtil
import org.jetbrains.kotlin.utils.LibraryUtils
import java.util.*
import java.util.concurrent.ConcurrentHashMap

internal object JpsUtils {

    private val IS_KOTLIN_JS_MODULE_CACHE = createMapForCaching<ModuleBuildTarget, Boolean>()
    private val IS_KOTLIN_JS_STDLIB_JAR_CACHE = createMapForCaching<String, Boolean>()

    fun getRelatedProductionModule(module: JpsModule) =
            JpsJavaExtensionService.getInstance().getTestModuleProperties(module)?.productionModule

    fun getAllDependencies(target: ModuleBuildTarget): JpsJavaDependenciesEnumerator {
        return JpsJavaExtensionService.dependencies(target.module).recursively().exportedOnly().includedIn(JpsJavaClasspathKind.compile(target.isTests))
    }

    fun isJsKotlinModule(target: ModuleBuildTarget): Boolean {
        val cachedValue = IS_KOTLIN_JS_MODULE_CACHE[target]
        if (cachedValue != null) return cachedValue

        val isKotlinJsModule = isJsKotlinModuleImpl(target)
        IS_KOTLIN_JS_MODULE_CACHE.put(target, isKotlinJsModule)

        return isKotlinJsModule
    }

    private fun isJsKotlinModuleImpl(target: ModuleBuildTarget): Boolean {
        val libraries = getAllDependencies(target).libraries
        for (library in libraries) {
            for (root in library.getRoots(JpsOrderRootType.COMPILED)) {
                val url = root.url

                val cachedValue = IS_KOTLIN_JS_STDLIB_JAR_CACHE[url]
                if (cachedValue != null) return cachedValue

                val isKotlinJavascriptStdLibrary = LibraryUtils.isKotlinJavascriptStdLibrary(JpsPathUtil.urlToFile(url))
                IS_KOTLIN_JS_STDLIB_JAR_CACHE.put(url, isKotlinJavascriptStdLibrary)
                if (isKotlinJavascriptStdLibrary) return true
            }
        }
        return false
    }

    private fun <K, V> createMapForCaching(): MutableMap<K, V> {
        if ("true".equals(System.getProperty("kotlin.jps.tests"), ignoreCase = true)) {
            return object : AbstractMap<K, V>() {
                override val entries = hashSetOf<MutableMap.MutableEntry<K, V>>()
                override fun put(key: K?, value: V?): V? = null
            }
        }

        return ConcurrentHashMap()
    }
}
