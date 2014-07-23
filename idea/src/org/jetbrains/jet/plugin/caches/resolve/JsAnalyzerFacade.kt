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

import org.jetbrains.jet.lang.resolve.lazy.ResolveSession
import org.jetbrains.jet.analyzer.ResolverForModule
import org.jetbrains.jet.lang.psi.JetFile
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.jet.analyzer.PlatformModuleParameters
import org.jetbrains.jet.analyzer.AnalyzerFacade
import com.intellij.openapi.project.Project
import org.jetbrains.jet.context.GlobalContext
import org.jetbrains.jet.lang.descriptors.ModuleDescriptorBase
import org.jetbrains.jet.analyzer.ResolverForProject
import org.jetbrains.k2js.analyze.AnalyzerFacadeForJS
import org.jetbrains.jet.lang.PlatformToKotlinClassMap
import org.jetbrains.jet.lang.resolve.lazy.declarations.DeclarationProviderFactoryService
import org.jetbrains.jet.lang.resolve.BindingTraceContext
import org.jetbrains.jet.di.InjectorForLazyResolve

public class JsResolverForModule(
        override val lazyResolveSession: ResolveSession
) : ResolverForModule

//TODO: is it needed?
public class JsPlatformParameters<M>(syntheticFiles: Collection<JetFile>, moduleScope: GlobalSearchScope) :
        PlatformModuleParameters(syntheticFiles, moduleScope)

public object JsAnalyzerFacade : AnalyzerFacade<JsResolverForModule, PlatformModuleParameters> {

    override fun <M> createResolverForModule(
            project: Project,
            globalContext: GlobalContext,
            moduleDescriptor: ModuleDescriptorBase,
            platformParameters: PlatformModuleParameters,
            setup: ResolverForProject<M, JsResolverForModule>
    ): JsResolverForModule {
        val declarationProviderFactory = DeclarationProviderFactoryService.createDeclarationProviderFactory(
                project, globalContext.storageManager, platformParameters.syntheticFiles, platformParameters.moduleScope
        )

        val injector = InjectorForLazyResolve(project, globalContext, moduleDescriptor, declarationProviderFactory, BindingTraceContext())
        val resolveSession = injector.getResolveSession()!!
        moduleDescriptor.setPackageFragmentProviderForSources(resolveSession.getPackageFragmentProvider())
        return JsResolverForModule(resolveSession)
    }

    override val defaultImports = AnalyzerFacadeForJS.DEFAULT_IMPORTS
    override val platformToKotlinClassMap = PlatformToKotlinClassMap.EMPTY

}
