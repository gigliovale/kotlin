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

package org.jetbrains.kotlin.idea.stubindex

import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SimpleModificationTracker
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.*
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFS
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFSImpl
import com.intellij.pom.PomManager
import com.intellij.pom.PomModelAspect
import com.intellij.pom.event.PomModelEvent
import com.intellij.pom.event.PomModelListener
import com.intellij.pom.tree.TreeAspect
import com.intellij.pom.tree.events.TreeChangeEvent
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.StubIndex
import com.intellij.psi.util.CachedValueProvider
import com.intellij.util.containers.ContainerUtil
import org.jetbrains.kotlin.idea.core.util.CachedValue
import org.jetbrains.kotlin.idea.core.util.getValue
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtPackageDirective
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType
import java.util.concurrent.ConcurrentMap


class PackageIndexUtilCacheModificationListener(
        project: Project,
        private val treeAspect: TreeAspect
) {
    init {
        val model = PomManager.getModel(project)

        model.addModelListener(object : PomModelListener {
            override fun isAspectChangeInteresting(aspect: PomModelAspect): Boolean {
                return aspect == treeAspect
            }

            override fun modelChanged(event: PomModelEvent) {
                val changeSet = event.getChangeSet(treeAspect) as TreeChangeEvent? ?: return

                if (changeSet.changedElements.any { it.psi?.getParentOfType<KtPackageDirective>(false) != null })
                    PackageModificationTracker.getInstance(project).incModificationCount()
            }
        })


        val connection = project.messageBus.connect()
        fun isRelevantVFSEvent(event: VFileEvent): Boolean =
                event is VFileCreateEvent ||
                event is VFileDeleteEvent ||
                event is VFileMoveEvent ||
                event is VFileCopyEvent

        val scope = GlobalSearchScope.projectScope(project)

        connection.subscribe(VirtualFileManager.VFS_CHANGES, object : BulkFileListener.Adapter() {
            override fun after(events: List<VFileEvent>) {
                events.filter(::isRelevantVFSEvent).filter { it.file != null && it.file!! in scope }.let { createEvents ->
                    if (createEvents.isNotEmpty()) {
                        FileLocationModificationTracker.getInstance(project).incModificationCount()
                    }
                }
            }
        })
    }
}

class PackageIndexUtilCacheService(project: Project) {


    val globalInSourceAndClassFiles by CachedValue(project, {
        CachedValueProvider.Result(ContainerUtil.createConcurrentSoftMap<String, Boolean>(),
                                   PackageModificationTracker.getInstance(project))
    })

    val perScope by CachedValue(project, {
        CachedValueProvider.Result(ContainerUtil.createConcurrentSoftMap<String, ConcurrentMap<GlobalSearchScope, Boolean>>(),
                                   PackageModificationTracker.getInstance(project),
                                   FileLocationModificationTracker.getInstance(project))
    })

    fun isPackageExists(packageFqName: FqName,
                        searchScope: GlobalSearchScope,
                        project: Project): Boolean {

        val fqn = packageFqName.asString()
        if (searchScope is KotlinSourceFilterScope && searchScope.isSourceAndClassFiles) {
            val inSource = globalInSourceAndClassFiles.getOrPut(fqn, {
                PackageIndexUtil.packageExistsNoCache(packageFqName, KotlinSourceFilterScope.sourceAndClassFiles(GlobalSearchScope.allScope(project), project), project)
            })
            if (!inSource)
                return false
        }
        val isExistsInScope = perScope.getOrPut(fqn) {
            ContainerUtil.createConcurrentWeakMap()
        }.getOrPut(searchScope) {
            PackageIndexUtil.packageExistsNoCache(packageFqName, searchScope, project)
        }
        return isExistsInScope
    }


    companion object {
        fun getInstance(project: Project): PackageIndexUtilCacheService {
            return ServiceManager.getService(project, PackageIndexUtilCacheService::class.java)
        }

        fun isPackageExists(packageFqName: FqName, searchScope: GlobalSearchScope, project: Project) =
                getInstance(project).isPackageExists(packageFqName, searchScope, project)
    }
}



class PackageModificationTracker : SimpleModificationTracker() {
    companion object {
        fun getInstance(project: Project): PackageModificationTracker {
            return ServiceManager.getService(project, PackageModificationTracker::class.java)
        }
    }
}

class FileLocationModificationTracker : SimpleModificationTracker() {
    companion object {
        fun getInstance(project: Project): FileLocationModificationTracker {
            return ServiceManager.getService(project, FileLocationModificationTracker::class.java)
        }
    }
}

object PackageIndexUtil {
    @JvmStatic fun getSubPackageFqNames(
            packageFqName: FqName,
            scope: GlobalSearchScope,
            project: Project,
            nameFilter: (Name) -> Boolean
    ): Collection<FqName> {
        return SubpackagesIndexService.getInstance(project).getSubpackages(packageFqName, scope, nameFilter)
    }

    @JvmStatic fun findFilesWithExactPackage(
            packageFqName: FqName,
            searchScope: GlobalSearchScope,
            project: Project
    ): Collection<KtFile> {
        return KotlinExactPackagesIndex.getInstance().get(packageFqName.asString(), project, searchScope)
    }

    @JvmStatic fun packageExists(
            packageFqName: FqName,
            searchScope: GlobalSearchScope,
            project: Project
    ) = PackageIndexUtilCacheService.isPackageExists(packageFqName, searchScope, project)

    @JvmStatic
    internal fun packageExistsNoCache(
            packageFqName: FqName,
            searchScope: GlobalSearchScope,
            project: Project
    ): Boolean {

        val subpackagesIndex = SubpackagesIndexService.getInstance(project)
        if (!subpackagesIndex.packageExists(packageFqName)) {
            return false
        }

        return containsFilesWithExactPackage(packageFqName, searchScope, project) ||
               subpackagesIndex.hasSubpackages(packageFqName, searchScope)
    }

    @JvmStatic fun containsFilesWithExactPackage(
            packageFqName: FqName,
            searchScope: GlobalSearchScope,
            project: Project
    ): Boolean {
        val ids = StubIndex.getInstance().getContainingIds(KotlinExactPackagesIndex.getInstance().key,
                                                           packageFqName.asString(),
                                                           project,
                                                           searchScope)
        val fs = PersistentFS.getInstance() as PersistentFSImpl
        while (ids.hasNext()) {
            val file = fs.findFileByIdIfCached(ids.next())
            if (file != null && file in searchScope) {
                return true
            }
        }
        return false
    }
}
