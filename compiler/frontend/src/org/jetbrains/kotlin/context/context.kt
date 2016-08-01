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

package org.jetbrains.kotlin.context

import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.builtins.KotlinBuiltIns
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.descriptors.ModuleParameters
import org.jetbrains.kotlin.descriptors.PackageFragmentProvider
import org.jetbrains.kotlin.descriptors.impl.ModuleDescriptorImpl
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.platform.PlatformToKotlinClassMap
import org.jetbrains.kotlin.resolve.TargetPlatform
import org.jetbrains.kotlin.resolve.createModule
import org.jetbrains.kotlin.storage.*
import java.util.concurrent.locks.Lock

interface GlobalContext {
    val storageManager: StorageManager
    val lazyResolveStorageManager: LazyResolveStorageManager
    val exceptionTracker: ExceptionTracker
}

interface ProjectContext : GlobalContext {
    val project: Project
}

interface ModuleContext : ProjectContext {
    val module: ModuleDescriptor

    val platformToKotlinClassMap: PlatformToKotlinClassMap
        get() = module.platformToKotlinClassMap

    val builtIns: KotlinBuiltIns
        get() = module.builtIns
}

interface MutableModuleContext: ModuleContext {
    override val module: ModuleDescriptorImpl

    fun setDependencies(vararg dependencies: ModuleDescriptorImpl) {
        module.setDependencies(*dependencies)
    }

    fun setDependencies(dependencies: List<ModuleDescriptorImpl>) {
        module.setDependencies(dependencies)
    }

    fun initializeModuleContents(packageFragmentProvider: PackageFragmentProvider) {
        module.initialize(packageFragmentProvider)
    }
}

open class GlobalContextImpl(
        override val storageManager: StorageManager,
        override val exceptionTracker: ExceptionTracker,
        override val lazyResolveStorageManager: LazyResolveStorageManager
) : GlobalContext

class ProjectContextImpl(
        override val project: Project,
        private val globalContext: GlobalContext
) : ProjectContext, GlobalContext by globalContext

class ModuleContextImpl(
        override val module: ModuleDescriptor,
        projectContext: ProjectContext
) : ModuleContext, ProjectContext by projectContext

class MutableModuleContextImpl(
        override val module: ModuleDescriptorImpl,
        projectContext: ProjectContext
) : MutableModuleContext, ProjectContext by projectContext

fun GlobalContext(lockless: Boolean = false): GlobalContextImpl {
    val tracker = ExceptionTracker()

    if (lockless) {
        return GlobalContextImpl(LocklessStorageManager(), tracker, LazyResolveStorageManager.EMPTY)
    }
    val storageManager = LockBasedStorageManager.createWithExceptionHandling(tracker)
    return GlobalContextImpl(storageManager, tracker, LockBasedLazyResolveStorageManager(storageManager))
}

@JvmOverloads
fun ProjectContext(project: Project, lockless: Boolean = false): ProjectContext =
        ProjectContextImpl(project, GlobalContext(lockless))

fun ModuleContext(module: ModuleDescriptor, project: Project): ModuleContext =
        ModuleContextImpl(module, ProjectContext(project))

fun GlobalContext.withProject(project: Project): ProjectContext = ProjectContextImpl(project, this)
fun ProjectContext.withModule(module: ModuleDescriptor): ModuleContext = ModuleContextImpl(module, this)

fun ContextForNewModule(
        project: Project,
        moduleName: Name,
        parameters: ModuleParameters,
        builtIns: KotlinBuiltIns
): MutableModuleContext {
    val projectContext = ProjectContext(project)
    val module = ModuleDescriptorImpl(moduleName, projectContext.storageManager, parameters, builtIns)
    return MutableModuleContextImpl(module, projectContext)
}

fun ContextForNewModule(
        projectContext: ProjectContext,
        moduleName: Name,
        targetPlatform: TargetPlatform,
        builtIns: KotlinBuiltIns
): MutableModuleContext {
    val module = targetPlatform.createModule(moduleName, projectContext.storageManager, builtIns)
    return MutableModuleContextImpl(module, projectContext)
}


@Deprecated("Used temporarily while we are in transition from to lazy resolve")
open class TypeLazinessToken {
    @Deprecated("Used temporarily while we are in transition from to lazy resolve")
    open fun isLazy(): Boolean = false
}

@Deprecated("Used temporarily while we are in transition from to lazy resolve") class LazyResolveToken : TypeLazinessToken() {
    @Deprecated("Used temporarily while we are in transition from to lazy resolve")
    override fun isLazy() = true
}
