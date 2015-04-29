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
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.kotlin.context.GlobalContext
import org.jetbrains.kotlin.context.LazyResolveToken
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.descriptors.impl.ModuleDescriptorImpl
import org.jetbrains.kotlin.generators.di.*
import org.jetbrains.kotlin.js.resolve.KotlinJsCheckerProvider
import org.jetbrains.kotlin.load.java.JavaClassFinderImpl
import org.jetbrains.kotlin.load.java.JavaFlexibleTypeCapabilitiesProvider
import org.jetbrains.kotlin.load.java.components.*
import org.jetbrains.kotlin.load.java.lazy.ModuleClassResolver
import org.jetbrains.kotlin.load.java.lazy.SingleModuleClassResolver
import org.jetbrains.kotlin.load.java.reflect.ReflectJavaClassFinder
import org.jetbrains.kotlin.load.java.sam.SamConversionResolverImpl
import org.jetbrains.kotlin.load.java.structure.JavaPropertyInitializerEvaluator
import org.jetbrains.kotlin.load.java.structure.impl.JavaPropertyInitializerEvaluatorImpl
import org.jetbrains.kotlin.load.kotlin.DeserializationComponentsForJava
import org.jetbrains.kotlin.load.kotlin.KotlinJvmCheckerProvider
import org.jetbrains.kotlin.load.kotlin.VirtualFileFinder
import org.jetbrains.kotlin.load.kotlin.VirtualFileFinderFactory
import org.jetbrains.kotlin.load.kotlin.reflect.ReflectKotlinClassFinder
import org.jetbrains.kotlin.resolve.*
import org.jetbrains.kotlin.resolve.jvm.JavaDescriptorResolver
import org.jetbrains.kotlin.resolve.jvm.JavaLazyAnalyzerPostConstruct
import org.jetbrains.kotlin.resolve.lazy.ResolveSession
import org.jetbrains.kotlin.resolve.lazy.ScopeProvider
import org.jetbrains.kotlin.resolve.lazy.declarations.DeclarationProviderFactory
import org.jetbrains.kotlin.storage.LockBasedStorageManager
import org.jetbrains.kotlin.types.DynamicTypesAllowed
import org.jetbrains.kotlin.types.expressions.ExpressionTypingServices
import org.jetbrains.kotlin.types.expressions.FakeCallResolver

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
                generatorForTopDownAnalyzerForJvm(),
                generatorForRuntimeDescriptorLoader(),
                generatorForLazyResolveWithJava(),
                generatorForTopDownAnalyzerForJs(),
                generatorForReplWithJava()
        )

private fun generatorForTopDownAnalyzerForJs() =
        generator("js/js.frontend/src", DI_DEFAULT_PACKAGE, "InjectorForTopDownAnalyzerForJs") {
            commonForResolveSessionBased()

            publicField<LazyTopDownAnalyzerForTopLevel>()

            field<KotlinJsCheckerProvider>(useAsContext = true)
            field<DynamicTypesAllowed>()
        }

private fun generatorForTopDownAnalyzerForJvm() =
        generator("compiler/frontend.java/src", DI_DEFAULT_PACKAGE, "InjectorForTopDownAnalyzerForJvm") {
            commonForJavaTopDownAnalyzer()
        }

private fun generatorForRuntimeDescriptorLoader() =
        generator("core/descriptors.runtime/src", DI_DEFAULT_PACKAGE, "InjectorForRuntimeDescriptorLoader") {
            parameter<ClassLoader>()
            publicParameter<ModuleDescriptor>()

            publicField<JavaDescriptorResolver>()
            publicField<DeserializationComponentsForJava>()

            field<ExternalSignatureResolver>(init = GetSingleton.byField(javaClass<ExternalSignatureResolver>(), "DO_NOTHING"))
            field<MethodSignatureChecker>(init = GetSingleton.byField(javaClass<MethodSignatureChecker>(), "DO_NOTHING"))
            field<JavaResolverCache>(init = GetSingleton.byField(javaClass<JavaResolverCache>(), "EMPTY"))
            field<ExternalAnnotationResolver>(init = GetSingleton.byField(javaClass<ExternalAnnotationResolver>(), "EMPTY"))
            field<JavaPropertyInitializerEvaluator>(init = GetSingleton.byField(javaClass<JavaPropertyInitializerEvaluator>(), "DO_NOTHING"))
            field<SamConversionResolver>(init = GetSingleton.byField(javaClass<SamConversionResolver>(), "EMPTY"))

            field<RuntimeErrorReporter>()
            field<RuntimeSourceElementFactory>()
            field<SingleModuleClassResolver>()

            field<LockBasedStorageManager>()
            field<ReflectJavaClassFinder>()
            field<ReflectKotlinClassFinder>()
        }

private fun generatorForLazyResolveWithJava() =
        generator("compiler/frontend.java/src", DI_DEFAULT_PACKAGE, "InjectorForLazyResolveWithJava") {
            commonForResolveSessionBased()

            parameter<GlobalSearchScope>(name = "moduleContentScope")
            parameter<ModuleClassResolver>()

            publicField<JavaDescriptorResolver>()

            field<VirtualFileFinder>(
                  init = GivenExpression(javaClass<VirtualFileFinderFactory>().getName()
                                         + ".SERVICE.getInstance(project).create(moduleContentScope)")
            )

            field<JavaClassFinderImpl>()
            field<TraceBasedExternalSignatureResolver>()
            field<LazyResolveBasedCache>()
            field<TraceBasedErrorReporter>()
            field<PsiBasedMethodSignatureChecker>()
            field<PsiBasedExternalAnnotationResolver>()
            field<JavaPropertyInitializerEvaluatorImpl>()
            field<SamConversionResolverImpl>()
            field<JavaSourceElementFactoryImpl>()
            field<JavaFlexibleTypeCapabilitiesProvider>()
            field<LazyResolveToken>()
            field<JavaLazyAnalyzerPostConstruct>()

            field<KotlinJvmCheckerProvider>(useAsContext = true)
        }

private fun generatorForReplWithJava() =
        generator("compiler/frontend.java/src", DI_DEFAULT_PACKAGE, "InjectorForReplWithJava") {
            commonForJavaTopDownAnalyzer()
            parameter<ScopeProvider.AdditionalFileScopeProvider>()
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

private fun DependencyInjectorGenerator.commonForJavaTopDownAnalyzer() {
    commonForResolveSessionBased()

    parameter<GlobalSearchScope>(name = "moduleContentScope")

    publicField<LazyTopDownAnalyzer>()
    publicField<LazyTopDownAnalyzerForTopLevel>()
    publicField<JavaDescriptorResolver>()
    publicField<DeserializationComponentsForJava>()

    field<VirtualFileFinder>(
          init = GivenExpression(javaClass<VirtualFileFinderFactory>().getName()
                                 + ".SERVICE.getInstance(project).create(moduleContentScope)")
    )

    field<JavaClassFinderImpl>()
    field<TraceBasedExternalSignatureResolver>()
    field<LazyResolveBasedCache>()
    field<TraceBasedErrorReporter>()
    field<PsiBasedMethodSignatureChecker>()
    field<PsiBasedExternalAnnotationResolver>()
    field<JavaPropertyInitializerEvaluatorImpl>()
    field<SamConversionResolverImpl>()
    field<JavaSourceElementFactoryImpl>()
    field<SingleModuleClassResolver>()
    field<JavaLazyAnalyzerPostConstruct>()
    field<JavaFlexibleTypeCapabilitiesProvider>()

    field<KotlinJvmCheckerProvider>(useAsContext = true)

    field<VirtualFileFinder>(init = GivenExpression(javaClass<VirtualFileFinder>().getName() + ".SERVICE.getInstance(project)"))
}


private fun generator(
        targetSourceRoot: String,
        injectorPackageName: String,
        injectorClassName: String,
        body: DependencyInjectorGenerator.() -> Unit
) = generator(targetSourceRoot, injectorPackageName, injectorClassName, "org.jetbrains.kotlin.generators.injectors.InjectorsPackage", body)
