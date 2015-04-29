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

package org.jetbrains.kotlin.frontend.java.di

import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.container.StorageComponentContainer
import org.jetbrains.kotlin.context.GlobalContext
import org.jetbrains.kotlin.descriptors.impl.ModuleDescriptorImpl
import org.jetbrains.kotlin.di.*
import org.jetbrains.kotlin.frontend.di.configureModule
import org.jetbrains.kotlin.load.java.JavaClassFinderImpl
import org.jetbrains.kotlin.load.java.lazy.SingleModuleClassResolver
import org.jetbrains.kotlin.load.kotlin.DeserializationComponentsForJava
import org.jetbrains.kotlin.load.kotlin.KotlinJvmCheckerProvider
import org.jetbrains.kotlin.resolve.BindingTrace
import org.jetbrains.kotlin.resolve.LazyTopDownAnalyzerForTopLevel
import org.jetbrains.kotlin.resolve.jvm.JavaClassFinderPostConstruct
import org.jetbrains.kotlin.resolve.jvm.JavaDescriptorResolver
import org.jetbrains.kotlin.resolve.lazy.ScopeProvider
import org.jetbrains.kotlin.resolve.lazy.declarations.DeclarationProviderFactory

public fun createContainerForTopDownAnalyzerForJvm(
        project: Project, globalContext: GlobalContext, bindingTrace: BindingTrace,
        module: ModuleDescriptorImpl, declarationProviderFactory: DeclarationProviderFactory,
        moduleContentScope: GlobalSearchScope
): ContainerForTopDownAnalyzerForJvm = createContainer("REPL") { //TODO: name
    configureModule(project, globalContext, module, bindingTrace, KotlinJvmCheckerProvider)
    configureJavaTopDownAnalysis(moduleContentScope, project)
    useInstance(declarationProviderFactory)

    useImpl<SingleModuleClassResolver>()
    useImpl<ScopeProvider>()
}.let {
    it.get<JavaClassFinderImpl>().initialize()
    it.get<JavaClassFinderPostConstruct>().postCreate()

    ContainerForTopDownAnalyzerForJvm(it)
}

public class ContainerForTopDownAnalyzerForJvm(container: StorageComponentContainer) {
    val lazyTopDownAnalyzerForTopLevel: LazyTopDownAnalyzerForTopLevel by injected(container)
    val javaDescriptorResolver: JavaDescriptorResolver by injected(container)
    val deserializationComponentsForJava: DeserializationComponentsForJava by injected(container)
}

