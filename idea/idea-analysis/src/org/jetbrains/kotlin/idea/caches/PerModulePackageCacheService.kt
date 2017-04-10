/*
 * Copyright 2010-2017 JetBrains s.r.o.
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

package org.jetbrains.kotlin.idea.caches

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.SimpleModificationTracker
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.*
import com.intellij.psi.impl.PsiTreeChangeEventImpl
import com.intellij.psi.impl.PsiTreeChangePreprocessor
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.CachedValue
import com.intellij.util.containers.ContainerUtil
import org.jetbrains.kotlin.analyzer.ModuleInfo
import org.jetbrains.kotlin.idea.caches.resolve.ModuleSourceInfo
import org.jetbrains.kotlin.idea.caches.resolve.getModuleInfoByVirtualFile
import org.jetbrains.kotlin.idea.caches.resolve.getNullableModuleInfo
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtPackageDirective
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType
import java.util.concurrent.ConcurrentMap

class KotlinPackageContentModificationListener(
        private val project: Project
) {
    init {
        val connection = project.messageBus.connect()

        val scope = GlobalSearchScope.projectScope(project)

        connection.subscribe(VirtualFileManager.VFS_CHANGES, object : BulkFileListener.Adapter() {
            override fun before(events: MutableList<out VFileEvent>) = onEvents(events) { it is VFileDeleteEvent || it is VFileMoveEvent }
            override fun after(events: List<VFileEvent>) = onEvents(events) { it is VFileMoveEvent || it is VFileCreateEvent || it is VFileCopyEvent }

            fun onEvents(events: List<VFileEvent>, filter: (VFileEvent) -> Boolean) = events.asSequence()
                    .filter(filter)
                    .map { it.file }
                    .filterNotNull()
                    .filter { it in scope }
                    .forEach { file ->
                        PerModulePackageCacheService.notifyPackageChange(file, project)
                    }
        })
    }
}

class KotlinPackageStatementPsiTreeChangePreprocessor : PsiTreeChangePreprocessor {
    override fun treeChanged(event: PsiTreeChangeEventImpl) {
        val file = event.file as? KtFile ?: return

        when (event.code) {
            PsiTreeChangeEventImpl.PsiEventType.CHILD_ADDED, PsiTreeChangeEventImpl.PsiEventType.CHILD_MOVED, PsiTreeChangeEventImpl.PsiEventType.CHILD_REPLACED, PsiTreeChangeEventImpl.PsiEventType.CHILD_REMOVED -> {
                val child = event.child ?: return
                if (child.getParentOfType<KtPackageDirective>(false) != null)
                    PerModulePackageCacheService.notifyPackageChange(file)
            }
            else -> {
            }
        }
    }
}

object PerModulePackageCacheService {

    val PER_MODULE_PACKAGE_CACHE = Key.create<ConcurrentMap<ModuleInfo, CachedValue<Set<FqName>>>>("per_module_package_cache")

    val moduleModificationTrackers = ContainerUtil.createConcurrentWeakMap<ModuleInfo, SimpleModificationTracker>()

    fun getModificationTracker(moduleInfo: ModuleInfo): SimpleModificationTracker = moduleModificationTrackers.getOrPut(moduleInfo, ::SimpleModificationTracker)


    internal fun notifyPackageChange(file: KtFile): Unit {
        (file.getNullableModuleInfo() as? ModuleSourceInfo)?.let(this::onChangeInModuleSource)
    }

    internal fun onChangeInModuleSource(moduleSourceInfo: ModuleSourceInfo) {
        moduleModificationTrackers[moduleSourceInfo]?.incModificationCount()
    }

    internal fun notifyPackageChange(file: VirtualFile, project: Project): Unit {
        (getModuleInfoByVirtualFile(project, file) as? ModuleSourceInfo)?.let(this::onChangeInModuleSource)
    }
}