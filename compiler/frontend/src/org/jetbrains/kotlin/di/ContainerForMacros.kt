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

package org.jetbrains.kotlin.frontend.di

import com.intellij.openapi.project.Project
import org.jetbrains.container.StorageComponentContainer
import org.jetbrains.kotlin.context.GlobalContext
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.di.createContainer
import org.jetbrains.kotlin.di.useImpl
import org.jetbrains.kotlin.di.useInstance
import org.jetbrains.kotlin.resolve.AdditionalCheckerProvider
import org.jetbrains.kotlin.resolve.TypeResolver
import org.jetbrains.kotlin.resolve.calls.CallResolver
import org.jetbrains.kotlin.types.expressions.ExpressionTypingComponents
import org.jetbrains.kotlin.types.expressions.ExpressionTypingServices

public fun createContainerForMacros(project: Project, module: ModuleDescriptor): StorageComponentContainer = createContainer("Macros") {

    //TODO_R: extract this logic

    useInstance(project)
    useInstance(module)

    val globalContext = GlobalContext()
    useInstance(globalContext)
    useInstance(globalContext.storageManager)
    useInstance(module.builtIns)
    useInstance(module.platformToKotlinClassMap)

    useInstance(AdditionalCheckerProvider.DefaultProvider)
    useInstance(AdditionalCheckerProvider.DefaultProvider.symbolUsageValidator)

    useImpl<ExpressionTypingServices>()
}