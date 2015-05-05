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

package org.jetbrains.kotlin.frontend.js.di

import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.context.GlobalContext
import org.jetbrains.kotlin.descriptors.impl.ModuleDescriptorImpl
import org.jetbrains.kotlin.di.createContainer
import org.jetbrains.kotlin.di.get
import org.jetbrains.kotlin.di.useImpl
import org.jetbrains.kotlin.di.useInstance
import org.jetbrains.kotlin.frontend.di.configureModule
import org.jetbrains.kotlin.js.resolve.KotlinJsCheckerProvider
import org.jetbrains.kotlin.resolve.BindingTrace
import org.jetbrains.kotlin.resolve.LazyTopDownAnalyzerForTopLevel
import org.jetbrains.kotlin.resolve.lazy.ResolveSession
import org.jetbrains.kotlin.resolve.lazy.ScopeProvider
import org.jetbrains.kotlin.resolve.lazy.declarations.DeclarationProviderFactory
import org.jetbrains.kotlin.types.DynamicTypesAllowed

public fun createTopDownAnalyzerForJs(
        project: Project, globalContext: GlobalContext, bindingTrace: BindingTrace,
        module: ModuleDescriptorImpl, declarationProviderFactory: DeclarationProviderFactory
): LazyTopDownAnalyzerForTopLevel {
    val storageComponentContainer = createContainer("REPL") { //TODO: name
        configureModule(project, globalContext, module, bindingTrace, KotlinJsCheckerProvider)

        useInstance(declarationProviderFactory)
        useImpl<ScopeProvider>()
        useImpl<ResolveSession>()
        useImpl<LazyTopDownAnalyzerForTopLevel>()
        useImpl<DynamicTypesAllowed>()
    }
    return storageComponentContainer.get<LazyTopDownAnalyzerForTopLevel>()
}

