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

package org.jetbrains.jet.lang.resolve.java.new

import org.jetbrains.jet.analyzer.new.AnalyzerFacade
import org.jetbrains.jet.analyzer.new.ResolverForModule
import org.jetbrains.jet.lang.resolve.lazy.ResolveSession
import org.jetbrains.jet.analyzer.new.PlatformModuleParameters
import org.jetbrains.jet.analyzer.new.ResolverForProject
import org.jetbrains.jet.lang.resolve.java.AnalyzerFacadeForJVM
import org.jetbrains.jet.lang.resolve.java.mapping.JavaToKotlinClassMap
import org.jetbrains.jet.lang.resolve.lazy.declarations.DeclarationProviderFactoryService
import org.jetbrains.jet.di.InjectorForModuleAwareLazyResolveWithJava
import org.jetbrains.jet.lang.resolve.BindingTraceContext
import com.intellij.openapi.project.Project
import org.jetbrains.jet.context.GlobalContext
import org.jetbrains.jet.lang.psi.JetFile
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.jet.lang.descriptors.impl.CompositePackageFragmentProvider
import org.jetbrains.jet.lang.resolve.java.structure.JavaClass
import org.jetbrains.jet.lang.descriptors.ModuleDescriptor
import org.jetbrains.jet.lang.resolve.java.JavaDescriptorResolver
import org.jetbrains.jet.lang.resolve.java.lazy.ModuleClassResolverImpl
import org.jetbrains.jet.lang.descriptors.impl.ModuleDescriptorImpl
import org.jetbrains.jet.analyzer.new.ResolverForProject

public class JvmResolverForModule(
        public override val lazyResolveSession: ResolveSession,
        public val javaDescriptorResolver: JavaDescriptorResolver
) : ResolverForModule

public class JvmPlatformParameters<M>(
        syntheticFiles: Collection<JetFile>,
        moduleScope: GlobalSearchScope,
        public val moduleByJavaClass: (JavaClass) -> M
) : PlatformModuleParameters(syntheticFiles, moduleScope)

public object JvmAnalyzerFacade : AnalyzerFacade<JvmResolverForModule, JvmPlatformParameters<*>> {
    override fun <M> createResolverForModule(
            project: Project, globalContext: GlobalContext, moduleDescriptor: ModuleDescriptorImpl,
            platformParameters: JvmPlatformParameters<*>, setup: ResolverForProject<M, JvmResolverForModule>
    ): JvmResolverForModule {
        val declarationProviderFactory = DeclarationProviderFactoryService.createDeclarationProviderFactory(
                project, globalContext.storageManager, platformParameters.syntheticFiles, platformParameters.moduleScope
        )

        val moduleByJavaClass = (platformParameters as JvmPlatformParameters<M>).moduleByJavaClass
        val moduleClassResolver = ModuleClassResolverImpl(moduleByJavaClass * {(m: M) -> setup.descriptorByModule[m]!! } *
                                                                  {(d: ModuleDescriptor) -> setup.analyzerByModuleDescriptor[d]!!.javaDescriptorResolver })
        val injector = InjectorForModuleAwareLazyResolveWithJava(
                project, globalContext, declarationProviderFactory, BindingTraceContext(), platformParameters.moduleScope, moduleClassResolver, moduleDescriptor
        )


        val resolveSession = injector.getResolveSession()!!
        val javaDescriptorResolver = injector.getJavaDescriptorResolver()!!
        val providersForSources = listOf(resolveSession.getPackageFragmentProvider(), javaDescriptorResolver.getPackageFragmentProvider())
        moduleDescriptor.initialize(CompositePackageFragmentProvider(providersForSources))
        return JvmResolverForModule(injector.getResolveSession()!!, injector.getJavaDescriptorResolver()!!)

    }

    override val defaultImports = AnalyzerFacadeForJVM.DEFAULT_IMPORTS
    override val platformToKotlinClassMap = JavaToKotlinClassMap.getInstance()

}

fun <M, P, K> Function1<M, P>.times(other: (P) -> K): (M) -> K = { p -> other(this(p)) }
