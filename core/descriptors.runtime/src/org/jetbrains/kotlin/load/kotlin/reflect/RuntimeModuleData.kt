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

package org.jetbrains.kotlin.load.kotlin.reflect

import org.jetbrains.container.*
import org.jetbrains.kotlin.builtins.KotlinBuiltIns
import org.jetbrains.kotlin.builtins.ReflectionTypes
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.descriptors.impl.ModuleDescriptorImpl
import org.jetbrains.kotlin.di.*
import org.jetbrains.kotlin.load.java.components.*
import org.jetbrains.kotlin.load.java.lazy.GlobalJavaResolverContext
import org.jetbrains.kotlin.load.java.lazy.LazyJavaPackageFragmentProvider
import org.jetbrains.kotlin.load.java.lazy.SingleModuleClassResolver
import org.jetbrains.kotlin.load.java.reflect.ReflectJavaClassFinder
import org.jetbrains.kotlin.load.java.structure.JavaPropertyInitializerEvaluator
import org.jetbrains.kotlin.load.kotlin.DeserializationComponentsForJava
import org.jetbrains.kotlin.load.kotlin.DeserializedDescriptorResolver
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.platform.JavaToKotlinClassMap
import org.jetbrains.kotlin.resolve.jvm.JavaDescriptorResolver
import org.jetbrains.kotlin.serialization.deserialization.LocalClassResolver
import org.jetbrains.kotlin.storage.LockBasedStorageManager

public class RuntimeModuleData private(public val module: ModuleDescriptor, public val localClassResolver: LocalClassResolver) {

    companion object {
        public fun create(classLoader: ClassLoader): RuntimeModuleData {
            val module = ModuleDescriptorImpl(Name.special("<runtime module for $classLoader>"), listOf(), JavaToKotlinClassMap.INSTANCE)
            module.addDependencyOnModule(module)
            module.addDependencyOnModule(KotlinBuiltIns.getInstance().getBuiltInsModule())

            val container = createContainer("RuntimeDescriptorLoader") {
                useInstance(classLoader)
                useInstance(module)

                useInstance(ExternalSignatureResolver.DO_NOTHING)
                useInstance(MethodSignatureChecker.DO_NOTHING)
                useInstance(JavaResolverCache.EMPTY)
                useInstance(ExternalAnnotationResolver.EMPTY)
                useInstance(JavaPropertyInitializerEvaluator.DO_NOTHING)
                useInstance(SamConversionResolver.EMPTY)
                useInstance(RuntimeSourceElementFactory)
                useInstance(RuntimeErrorReporter)

                useImpl<DeserializedDescriptorResolver>()
                useImpl<SingleModuleClassResolver>()
                useImpl<LockBasedStorageManager>()
                useImpl<ReflectJavaClassFinder>()
                useImpl<ReflectKotlinClassFinder>()
            }

            val descriptorResolver = container.get<JavaDescriptorResolver>()
            val localClassResolver = container.get<DeserializationComponentsForJava>().components.localClassResolver

            module.initialize(descriptorResolver.packageFragmentProvider)
            return RuntimeModuleData(module, localClassResolver)
        }
    }
}
