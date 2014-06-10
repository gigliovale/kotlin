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

package org.jetbrains.jet.analyzer.new

import org.jetbrains.jet.lang.resolve.lazy.ResolveSession
import org.jetbrains.jet.lang.resolve.name.Name
import org.jetbrains.jet.lang.descriptors.ModuleDescriptor
import java.util.HashMap
import org.jetbrains.jet.lang.resolve.ImportPath
import org.jetbrains.jet.lang.PlatformToKotlinClassMap
import com.intellij.openapi.project.Project
import org.jetbrains.jet.context.GlobalContext
import org.jetbrains.jet.lang.types.lang.KotlinBuiltIns
import org.jetbrains.jet.lang.descriptors.impl.ModuleDescriptorImpl

//TODO: ResolverForModule
public trait Analyzer {
    public val lazyResolveSession: ResolveSession
}

//TODO: ResolverForProject
public trait AnalysisSetup<M, A : Analyzer> {
    public val analyzerByModuleDescriptor: Map<ModuleDescriptor, A>
    public val descriptorByModule: Map<M, ModuleDescriptor>
    public val moduleByDescriptor: Map<ModuleDescriptor, M>
}

public class AnalysisSetupImpl<M, A : Analyzer>(
        public override val descriptorByModule: Map<M, ModuleDescriptorImpl>,
        public override val moduleByDescriptor: Map<ModuleDescriptor, M>
) : AnalysisSetup<M, A> {
    override val analyzerByModuleDescriptor: MutableMap<ModuleDescriptor, A> = HashMap()
}

public trait PlatformModuleParameters

public trait ModuleInfo<T : ModuleInfo<T>> {
    val name: Name
    fun dependencies(): List<ModuleInfo<T>>
}

public trait AnalyzerFacade<A : Analyzer, P : PlatformModuleParameters> {
    public fun <M: ModuleInfo<M>> setupAnalysis(
            globalContext: GlobalContext,
            project: Project,
            modules: Collection<M>,
            platformParameters: (M) -> P
    ): AnalysisSetup<M, A> {

        fun createSetup(): AnalysisSetupImpl<M, A> {
            val descriptorByModule = HashMap<M, ModuleDescriptorImpl>()
            val moduleByDescriptor = HashMap<ModuleDescriptor, M>()
            modules.forEach {
                module ->
                val descriptor = ModuleDescriptorImpl(module.name, defaultImports, platformToKotlinClassMap)
                descriptorByModule[module] = descriptor
                moduleByDescriptor[descriptor] = module
            }
            return AnalysisSetupImpl(descriptorByModule, moduleByDescriptor)
        }

        val analysisSetup = createSetup()

        fun setupModuleDependencies() {
            modules.forEach {
                module ->
                val currentModule = analysisSetup.descriptorByModule[module]!!
                module.dependencies().forEach {
                    dependency ->
                    val dependencyDescriptor = analysisSetup.descriptorByModule[dependency]!!
                    currentModule.addDependencyOnModule(dependencyDescriptor)
                }
                //TODO:
                currentModule.addDependencyOnModule(KotlinBuiltIns.getInstance().getBuiltInsModule())
            }
        }

        setupModuleDependencies()

        fun initializeAnalysisSetup() {
            modules.forEach {
                module ->
                val descriptor = analysisSetup.descriptorByModule[module]!!
                //TODO: communicate information that package fragment provider for sources has to be set
                val analyzer = createAnalyzerForModule(project, globalContext, descriptor, platformParameters(module), analysisSetup)
                analysisSetup.analyzerByModuleDescriptor[descriptor] = analyzer
            }
        }

        initializeAnalysisSetup()
        return analysisSetup
    }

    protected fun <M> createAnalyzerForModule(project: Project, globalContext: GlobalContext, moduleDescriptor: ModuleDescriptorImpl,
                                              platformParameters: P, setup: AnalysisSetup<M, A>): A

    public val defaultImports: List<ImportPath>
    public val platformToKotlinClassMap: PlatformToKotlinClassMap
}

