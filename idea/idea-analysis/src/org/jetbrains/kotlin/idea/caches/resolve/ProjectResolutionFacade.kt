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

package org.jetbrains.kotlin.idea.caches.resolve

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.util.containers.SLRUCache
import org.jetbrains.kotlin.analyzer.AnalysisResult
import org.jetbrains.kotlin.container.get
import org.jetbrains.kotlin.container.getService
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.descriptors.DeclarationDescriptorWithSource
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.descriptors.SourceElement
import org.jetbrains.kotlin.idea.decompiler.KtClsFileBase
import org.jetbrains.kotlin.idea.project.ResolveElementCache
import org.jetbrains.kotlin.idea.resolve.ResolutionFacade
import org.jetbrains.kotlin.incremental.components.NoLookupLocation
import org.jetbrains.kotlin.load.kotlin.KotlinJvmBinarySourceElement
import org.jetbrains.kotlin.load.kotlin.header.KotlinClassHeader
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getNonStrictParentOfType
import org.jetbrains.kotlin.psi.psiUtil.parentsWithSelf
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.CompositeBindingContext
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode
import org.jetbrains.kotlin.resolve.lazy.ResolveSession
import org.jetbrains.kotlin.serialization.deserialization.DeserializedMemberSourceElement

internal class ProjectResolutionFacade(
        val project: Project,
        computeModuleResolverProvider: () -> CachedValueProvider.Result<ModuleResolverProvider>
) {
    private val resolverCache = SynchronizedCachedValue(project, computeModuleResolverProvider, trackValue = false)

    val moduleResolverProvider: ModuleResolverProvider
        get() = resolverCache.getValue()

    fun resolverForModuleInfo(moduleInfo: IdeaModuleInfo) = moduleResolverProvider.resolverForProject.resolverForModule(moduleInfo)
    fun resolverForDescriptor(moduleDescriptor: ModuleDescriptor) = moduleResolverProvider.resolverForProject.resolverForModuleDescriptor(moduleDescriptor)

    fun findModuleDescriptor(ideaModuleInfo: IdeaModuleInfo): ModuleDescriptor {
        return moduleResolverProvider.resolverForProject.descriptorForModule(ideaModuleInfo)
    }

    private val analysisResults = CachedValuesManager.getManager(project).createCachedValue(
            {
                val resolverProvider = moduleResolverProvider
                val results = object : SLRUCache<KtFile, PerFileAnalysisCache>(2, 3) {
                    override fun createValue(file: KtFile?): PerFileAnalysisCache {
                        return PerFileAnalysisCache(file!!, resolverProvider.resolverForProject.resolverForModule(file.getModuleInfo()).componentProvider)
                    }
                }
                CachedValueProvider.Result(results, PsiModificationTracker.MODIFICATION_COUNT, resolverProvider.exceptionTracker)
            }, false)

    fun getAnalysisResultsForElements(elements: Collection<KtElement>): AnalysisResult {
        assert(elements.isNotEmpty()) { "elements collection should not be empty" }
        val slruCache = synchronized(analysisResults) {
            analysisResults.getValue()!!
        }
        val results = elements.map {
            val perFileCache = synchronized(slruCache) {
                slruCache[it.getContainingKtFile()]
            }
            perFileCache.getAnalysisResults(it)
        }
        val withError = results.firstOrNull { it.isError() }
        val bindingContext = CompositeBindingContext.create(results.map { it.bindingContext })
        return if (withError != null)
            AnalysisResult.error(bindingContext, withError.error)
        else
        //TODO: (module refactoring) several elements are passed here in debugger
            AnalysisResult.success(bindingContext, findModuleDescriptor(elements.first().getModuleInfo()))
    }
}

internal class ResolutionFacadeImpl(
        private val projectFacade: ProjectResolutionFacade,
        private val moduleInfo: IdeaModuleInfo
) : ResolutionFacade {
    override val project: Project
        get() = projectFacade.project

    //TODO: ideally we would like to store moduleDescriptor once and for all
    // but there are some usages that use resolutionFacade and mutate the psi leading to recomputation of underlying structures
    override val moduleDescriptor: ModuleDescriptor
        get() = projectFacade.findModuleDescriptor(moduleInfo)

    override fun analyze(element: KtElement, bodyResolveMode: BodyResolveMode): BindingContext {
        val resolveElementCache = getFrontendService(element, javaClass<ResolveElementCache>())
        return resolveElementCache.resolveToElement(element, bodyResolveMode)
    }

    override fun analyzeFullyAndGetResult(elements: Collection<KtElement>): AnalysisResult
            = projectFacade.getAnalysisResultsForElements(elements)

    override fun resolveToDescriptor(declaration: KtDeclaration): DeclarationDescriptor {
        if (declaration.getContainingJetFile().isCompiled) {
            return resolveDecompiledDeclarationToDescriptor(declaration)
        }
        val resolveSession = projectFacade.resolverForModuleInfo(declaration.getModuleInfo()).componentProvider.get<ResolveSession>()
        return resolveSession.resolveToDescriptor(declaration)
    }

    private fun resolveDecompiledDeclarationToDescriptor(declaration: KtDeclaration): DeclarationDescriptor {
        val decompiledFile = declaration.getContainingJetFile() as KtClsFileBase
        val moduleInfo = getModuleInfoByVirtualFile(declaration.project, decompiledFile.virtualFile, false)
        val moduleDescriptor = projectFacade.findModuleDescriptor(moduleInfo)
        val packageView = moduleDescriptor.getPackage(decompiledFile.packageFqName)
        val declarations = declaration.parentsWithSelf.filterIsInstance<KtNamedDeclaration>().toList().asReversed()
        var scope = packageView.memberScope
        declarations.forEach { declaration ->
            val declarationName = declaration.nameAsName!!
            when (declaration) {
                is KtClass -> {
                    val classifier = scope.getContributedClassifier(declarationName, location = NoLookupLocation.FROM_IDE)!!
                    scope = classifier.defaultType.memberScope
                }
                is KtNamedFunction -> {
                    val functions = scope.getContributedFunctions(declaration.nameAsName!!, location = NoLookupLocation.FROM_IDE)
                    return chooseBySourceElement(declaration, functions)
                }
                is KtProperty -> {
                    val properties = scope.getContributedVariables(declaration.nameAsName!!, location = NoLookupLocation.FROM_IDE)
                    return chooseBySourceElement(declaration, properties)
                }
            }
        }
        TODO()
    }

    private fun getDeclarationIndex(declaration: KtNamedDeclaration): Int {
        val declarationContainer: KtDeclarationContainer = declaration.getNonStrictParentOfType<KtClassBody>() ?: declaration.getContainingJetFile()
        return filterRelevantDeclarations(
                declarationContainer, declaration
        ).indexOf(declaration)
    }

    private fun filterRelevantDeclarations(declarationContainer: KtDeclarationContainer, declaration: KtNamedDeclaration): List<KtNamedDeclaration> {
        return when (declaration) {
            is KtProperty -> filterByName<KtProperty>(declaration, declarationContainer)
            is KtFunction -> filterByName<KtFunction>(declaration, declarationContainer)
            is KtSecondaryConstructor -> TODO()
            is KtPrimaryConstructor -> TODO()
            else -> TODO()
        }
    }

    private inline fun <reified T : KtNamedDeclaration> filterByName(
            declaration: KtNamedDeclaration,
            declarationContainer: KtDeclarationContainer
    ): List<T> {
        val declarationName = declaration.nameAsName
        return declarationContainer.declarations.filterIsInstance<T>().filter {
            it.nameAsName == declarationName
        }
    }

    private fun chooseBySourceElement(
            declaration: KtNamedDeclaration,
            descriptors: Collection<DeclarationDescriptor>
    ): DeclarationDescriptor {
        val index = getDeclarationIndex(declaration)
        val fileName = declaration.getContainingJetFile().virtualFile.nameWithoutExtension
        val descriptorsForThisFile = descriptors.filter {
            val memberSourceElement = (it.original as? DeclarationDescriptorWithSource)?.source as? DeserializedMemberSourceElement
            val parentSource = memberSourceElement!!.parentSource
            getFileNameBySource(parentSource) == fileName
        }

        return descriptorsForThisFile[index]
    }

    private fun getFileNameBySource(sourceElement: SourceElement): String? {
        val binaryClass = (sourceElement as? KotlinJvmBinarySourceElement)?.binaryClass ?:return null
        return when (binaryClass.classHeader.kind) {
            KotlinClassHeader.Kind.MULTIFILE_CLASS_PART -> binaryClass.classHeader.multifileClassName!!.substringAfterLast('/')
            else -> binaryClass.classId.relativeClassName.pathSegments().first().asString()
        }
    }

    override fun <T : Any> getFrontendService(serviceClass: Class<T>): T  = getFrontendService(moduleInfo, serviceClass)

    override fun <T : Any> getIdeService(serviceClass: Class<T>): T {
        return projectFacade.resolverForModuleInfo(moduleInfo).componentProvider.create(serviceClass)
    }

    override fun <T : Any> getFrontendService(element: PsiElement, serviceClass: Class<T>): T {
        return getFrontendService(element.getModuleInfo(), serviceClass)
    }

    fun <T : Any> getFrontendService(ideaModuleInfo: IdeaModuleInfo, serviceClass: Class<T>): T {
        return projectFacade.resolverForModuleInfo(ideaModuleInfo).componentProvider.getService(serviceClass)
    }

    override fun <T : Any> getFrontendService(moduleDescriptor: ModuleDescriptor, serviceClass: Class<T>): T {
        return projectFacade.resolverForDescriptor(moduleDescriptor).componentProvider.getService(serviceClass)
    }

}
