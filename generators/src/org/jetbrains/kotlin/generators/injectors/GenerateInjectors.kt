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

package org.jetbrains.kotlin.generators.injectors

import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.context.GlobalContext
import org.jetbrains.kotlin.descriptors.impl.ModuleDescriptorImpl
import org.jetbrains.kotlin.generators.di.*
import org.jetbrains.kotlin.js.resolve.KotlinJsCheckerProvider
import org.jetbrains.kotlin.resolve.BindingTrace
import org.jetbrains.kotlin.resolve.LazyTopDownAnalyzerForTopLevel
import org.jetbrains.kotlin.resolve.lazy.ResolveSession
import org.jetbrains.kotlin.resolve.lazy.ScopeProvider
import org.jetbrains.kotlin.resolve.lazy.declarations.DeclarationProviderFactory
import org.jetbrains.kotlin.types.DynamicTypesAllowed

// NOTE: After making changes, you need to re-generate the injectors.
//       To do that, you can run main in this file.
public fun main(args: Array<String>) {
    for (generator in createInjectorGenerators()) {
        try {
            generator.generate()
        }
        catch (e: Throwable) {
            System.err.println(generator.getOutputFile())
            throw e
        }
    }
}

private val DI_DEFAULT_PACKAGE = "org.jetbrains.kotlin.di"

public fun createInjectorGenerators(): List<DependencyInjectorGenerator> =
        listOf(
                generatorForTopDownAnalyzerForJs()
        )

private fun generatorForTopDownAnalyzerForJs() =
        generator("js/js.frontend/src", DI_DEFAULT_PACKAGE, "InjectorForTopDownAnalyzerForJs") {
            commonForResolveSessionBased()

            publicField<LazyTopDownAnalyzerForTopLevel>()

            field<KotlinJsCheckerProvider>(useAsContext = true)
            field<DynamicTypesAllowed>()
        }

private fun DependencyInjectorGenerator.commonForResolveSessionBased() {
    parameter<Project>()
    parameter<GlobalContext>(useAsContext = true)
    parameter<BindingTrace>()
    publicParameter<ModuleDescriptorImpl>(name = "module", useAsContext = true)
    parameter<DeclarationProviderFactory>()

    publicField<ResolveSession>()
    field<ScopeProvider>()
}

private fun generator(
        targetSourceRoot: String,
        injectorPackageName: String,
        injectorClassName: String,
        body: DependencyInjectorGenerator.() -> Unit
) = generator(targetSourceRoot, injectorPackageName, injectorClassName, "org.jetbrains.kotlin.generators.injectors.InjectorsPackage", body)
