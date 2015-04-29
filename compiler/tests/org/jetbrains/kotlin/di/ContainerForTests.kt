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

package org.jetbrains.kotlin.tests.di

import com.intellij.openapi.project.Project
import org.jetbrains.container.StorageComponentContainer
import org.jetbrains.kotlin.context.GlobalContext
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.di.createContainer
import org.jetbrains.kotlin.di.useImpl
import org.jetbrains.kotlin.di.useInstance
import org.jetbrains.kotlin.load.kotlin.KotlinJvmCheckerProvider
import org.jetbrains.kotlin.resolve.*
import org.jetbrains.kotlin.types.expressions.ExpressionTypingServices
import org.jetbrains.kotlin.types.expressions.FakeCallResolver
import kotlin.properties.ReadOnlyProperty

public fun createContainerForTests(project: Project, module: ModuleDescriptor): ContainerForTests {
    return ContainerForTests(createContainer("Macros") {
        useInstance(project)
        useInstance(module)

        val globalContext = GlobalContext()
        useInstance(globalContext)
        useInstance(globalContext.storageManager)
        useInstance(module.builtIns)
        useInstance(module.platformToKotlinClassMap)

        useInstance(KotlinJvmCheckerProvider)
        useInstance(KotlinJvmCheckerProvider.symbolUsageValidator)

        useImpl<ExpressionTypingServices>()
    })
}

class ContainerForTests(container: StorageComponentContainer) {
    val descriptorResolver: DescriptorResolver by injected(container)
    val functionDescriptorResolver: FunctionDescriptorResolver by injected(container)
    val typeResolver: TypeResolver by injected(container)
    val fakeCallResolver: FakeCallResolver by injected(container)
    val expressionTypingServices: ExpressionTypingServices by injected(container)
    val qualifiedExpressionResolver: QualifiedExpressionResolver by injected(container)
    val additionalCheckerProvider: AdditionalCheckerProvider by injected(container)
}

public class InjectedProperty<T>(
        private val container: StorageComponentContainer, private val requestedComponent: Class<T>
) : ReadOnlyProperty<Any?, T> {
    override fun get(thisRef: Any?, desc: PropertyMetadata): T {
        return container.resolve(requestedComponent)!!.getValue() as T
    }
}

inline fun <reified T> injected(container: StorageComponentContainer) = InjectedProperty(container, javaClass<T>())