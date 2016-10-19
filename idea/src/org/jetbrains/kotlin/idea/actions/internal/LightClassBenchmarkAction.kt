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

package org.jetbrains.kotlin.idea.actions.internal

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.ui.Messages
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.kotlin.idea.caches.KotlinShortNamesCache
import kotlin.system.measureTimeMillis

class LightClassBenchmarkAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val shortNamesCache = KotlinShortNamesCache(project)
        val allClassNames = shortNamesCache.allClassNames
        val scope = GlobalSearchScope.projectScope(project)
        var count = 0
        val time = measureTimeMillis {
            allClassNames.flatMap { shortNamesCache.getClassesByName(it, scope).asList() }.flatMap {
                count++
                it.extendsListTypes.asList()
            }
        }
        Messages.showMessageDialog(project, "$count light classes built in $time ms", "Light class benchmark", null)
    }
}
