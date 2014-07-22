/*
 * Copyright 2010-2014 JetBrains s.r.o.
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

package org.jetbrains.jet.plugin.caches.resolve

import org.jetbrains.jet.lang.resolve.java.JavaDescriptorResolver
import org.jetbrains.jet.plugin.project.TargetPlatform
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.jet.lang.resolve.java.new.JvmResolverForModule

object JavaResolveExtension : CacheExtension<(PsiElement) -> JavaDescriptorResolver> {
    override val platform: TargetPlatform = TargetPlatform.JVM

    override fun getData(setup: ModuleSetup): (PsiElement) -> JavaDescriptorResolver {
        return {
            (setup.setupByModule(it.getModuleInfo()!!) as JvmResolverForModule).javaDescriptorResolver
        }
    }

    public fun get(project: Project): (PsiElement) -> JavaDescriptorResolver = KotlinCacheService.getInstance(project)[this]
}