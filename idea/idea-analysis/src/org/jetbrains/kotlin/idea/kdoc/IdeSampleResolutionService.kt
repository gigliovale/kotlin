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

package org.jetbrains.kotlin.idea.kdoc

import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.descriptors.annotations.Annotations
import org.jetbrains.kotlin.idea.caches.resolve.resolveToDescriptor
import org.jetbrains.kotlin.idea.caches.resolve.resolveToDescriptorIfAny
import org.jetbrains.kotlin.idea.resolve.ResolutionFacade
import org.jetbrains.kotlin.idea.stubindex.KotlinClassShortNameIndex
import org.jetbrains.kotlin.idea.stubindex.KotlinFunctionShortNameIndex
import org.jetbrains.kotlin.idea.stubindex.PackageIndexUtil
import org.jetbrains.kotlin.incremental.components.LookupLocation
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.name.isChildOf
import org.jetbrains.kotlin.name.isOneSegmentFQN
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode
import org.jetbrains.kotlin.resolve.scopes.DescriptorKindFilter
import org.jetbrains.kotlin.resolve.scopes.MemberScope
import org.jetbrains.kotlin.utils.Printer

class IdeSampleResolutionService(val project: Project) : SampleResolutionService {

    override fun resolveSample(context: BindingContext, fromDescriptor: DeclarationDescriptor, resolutionFacade: ResolutionFacade, qualifiedName: List<String>): Collection<DeclarationDescriptor> {

        val allScope = GlobalSearchScope.projectScope(project)

        val shortName = qualifiedName.lastOrNull() ?: return emptyList()

        val targetFqName = FqName.fromSegments(qualifiedName)

        val functions = KotlinFunctionShortNameIndex.getInstance().get(shortName, project, allScope).asSequence()
        val classes = KotlinClassShortNameIndex.getInstance().get(shortName, project, allScope).asSequence()

        val descriptors = (functions + classes)
                .filter { it.fqName == targetFqName }
                .map { it.resolveToDescriptor(BodyResolveMode.PARTIAL) } // TODO Filter out not visible due dependencies config descriptors
                .toList()
        if (descriptors.isNotEmpty())
            return descriptors

        if (!targetFqName.isRoot && PackageIndexUtil.packageExists(targetFqName, GlobalSearchScope.allScope(project), project))
            return listOf(GlobalSyntheticPackageViewDescriptor(targetFqName, project))
        return emptyList()
    }
}


private class GlobalSyntheticPackageViewDescriptor(override val fqName: FqName, private val project: Project) : PackageViewDescriptor {
    override fun getContainingDeclaration(): PackageViewDescriptor? =
            if (fqName.isOneSegmentFQN()) null else GlobalSyntheticPackageViewDescriptor(fqName.parent(), project)


    private val allScope = GlobalSearchScope.allScope(project)

    override val memberScope: MemberScope = object : MemberScope {

        override fun getContributedVariables(name: Name, location: LookupLocation): Collection<PropertyDescriptor> {
            throw UnsupportedOperationException("Synthetic PVD for KDoc link resolution")
        }

        override fun getContributedFunctions(name: Name, location: LookupLocation): Collection<SimpleFunctionDescriptor> {
            throw UnsupportedOperationException("Synthetic PVD for KDoc link resolution")
        }

        override fun getFunctionNames(): Set<Name> {
            throw UnsupportedOperationException("Synthetic PVD for KDoc link resolution")
        }

        override fun getVariableNames(): Set<Name> {
            throw UnsupportedOperationException("Synthetic PVD for KDoc link resolution")
        }

        override fun getContributedClassifier(name: Name, location: LookupLocation): ClassifierDescriptor? {
            throw UnsupportedOperationException("Synthetic PVD for KDoc link resolution")
        }

        override fun printScopeStructure(p: Printer) {
            p.printIndent()
            p.print("GlobalSyntheticPackageViewDescriptorMemberScope (INDEX)")
        }


        fun getClassesByNameFilter(nameFilter: (Name) -> Boolean) = KotlinClassShortNameIndex.getInstance()
                .getAllKeys(project)
                .asSequence()
                .filter { nameFilter(Name.identifier(it)) }
                .flatMap { KotlinClassShortNameIndex.getInstance()[it, project, allScope].asSequence() }
                .filter { it.fqName?.isChildOf(fqName) == true }
                .map { it.resolveToDescriptorIfAny() }

        fun getFunctionsByNameFilter(nameFilter: (Name) -> Boolean) = KotlinFunctionShortNameIndex.getInstance()
                .getAllKeys(project)
                .asSequence()
                .filter { nameFilter(Name.identifier(it)) }
                .flatMap { KotlinFunctionShortNameIndex.getInstance()[it, project, allScope].asSequence() }
                .filter { it.fqName?.isChildOf(fqName) == true }
                .map { it.resolveToDescriptorIfAny() }

        fun getSubpackages(nameFilter: (Name) -> Boolean) =
                PackageIndexUtil.getSubPackageFqNames(fqName, allScope, project, nameFilter).map {
                    GlobalSyntheticPackageViewDescriptor(it, project)
                }

        override fun getContributedDescriptors(kindFilter: DescriptorKindFilter, nameFilter: (Name) -> Boolean): Collection<DeclarationDescriptor> {
            return (getClassesByNameFilter(nameFilter)
                    + getFunctionsByNameFilter(nameFilter)
                    + getSubpackages(nameFilter)).filterIsInstance<DeclarationDescriptor>().toList()
        }

    }
    override val module: ModuleDescriptor
        get() = throw UnsupportedOperationException("Synthetic PVD for KDoc link resolution")
    override val fragments: List<PackageFragmentDescriptor>
        get() = throw UnsupportedOperationException("Synthetic PVD for KDoc link resolution")

    override fun getOriginal() = this

    private val name = fqName.shortName()
    override fun getName(): Name = name

    override fun <R : Any?, D : Any?> accept(visitor: DeclarationDescriptorVisitor<R, D>?, data: D): R {
        throw UnsupportedOperationException("Synthetic PVD for KDoc link resolution")
    }

    override fun acceptVoid(visitor: DeclarationDescriptorVisitor<Void, Void>?) {
        throw UnsupportedOperationException("Synthetic PVD for KDoc link resolution")
    }

    override val annotations = Annotations.EMPTY
}