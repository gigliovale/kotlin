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

package org.jetbrains.kotlin.load.kotlin.incremental

import org.jetbrains.kotlin.descriptors.PackagePartProvider
import org.jetbrains.kotlin.load.kotlin.ModuleMapping
import org.jetbrains.kotlin.load.kotlin.incremental.components.IncrementalCache
import org.jetbrains.kotlin.load.kotlin.incremental.components.IncrementalCompilationComponents

internal class IncrementalPackagePartProvider(
        val parent: PackagePartProvider,
        private val incrementalCaches: List<IncrementalCache>
) : PackagePartProvider {
    private val moduleMappings by lazy { incrementalCaches.map { ModuleMapping(it.getModuleMappingData()) } }

    override fun findPackageParts(packageFqName: String) =
            (moduleMappings.map { it.findPackageParts(packageFqName) }.filterNotNull().flatMap { it.parts } + parent.findPackageParts(packageFqName)).toSet().toList()
}

fun IncrementalPackagePartProvider(
        parent: PackagePartProvider,
        moduleIds: List<String>?,
        incrementalCompilationComponents: IncrementalCompilationComponents?
): PackagePartProvider {
    if (moduleIds == null || incrementalCompilationComponents == null) return parent

    val incrementalCaches = moduleIds.map { incrementalCompilationComponents.getIncrementalCache(it) }

    return IncrementalPackagePartProvider(parent, incrementalCaches)
}
