/*
 * Copyright 2010-2016 JetBrains s.r.o.
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
import org.jetbrains.kotlin.analyzer.AnalysisResult
import org.jetbrains.kotlin.builtins.KotlinBuiltIns
import org.jetbrains.kotlin.container.get
import org.jetbrains.kotlin.container.getService
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.descriptors.DeclarationDescriptorWithSource
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.descriptors.SourceElement
import org.jetbrains.kotlin.idea.decompiler.KtDecompiledFile
import org.jetbrains.kotlin.idea.decompiler.classFile.KtClsFile
import org.jetbrains.kotlin.idea.project.ResolveElementCache
import org.jetbrains.kotlin.idea.resolve.ResolutionFacade
import org.jetbrains.kotlin.incremental.components.NoLookupLocation
import org.jetbrains.kotlin.load.java.lazy.LazyJavaPackageFragmentProvider
import org.jetbrains.kotlin.load.kotlin.JvmPackagePartSource
import org.jetbrains.kotlin.load.kotlin.KotlinJvmBinarySourceElement
import org.jetbrains.kotlin.platform.JvmBuiltIns
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getNonStrictParentOfType
import org.jetbrains.kotlin.psi.psiUtil.parentsWithSelf
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.jvm.platform.JvmPlatform
import org.jetbrains.kotlin.resolve.lazy.AbsentDescriptorHandler
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode
import org.jetbrains.kotlin.resolve.lazy.ResolveSession
import org.jetbrains.kotlin.resolve.scopes.MemberScope
import org.jetbrains.kotlin.serialization.deserialization.descriptors.DeserializedMemberSource

internal class ResolutionFacadeImpl(
        private val projectFacade: ProjectResolutionFacade,
        private val moduleInfo: IdeaModuleInfo
) : ResolutionFacade {
    override val project: Project
        get() = projectFacade.project

    //TODO: ideally we would like to store moduleDescriptor once and for all
    // but there are some usages that use resolutionFacade and mutate the psi leading to recomputation of underlying structures
    override val moduleDescriptor: ModuleDescriptor
        get() = findModuleDescriptor(moduleInfo)

    fun findModuleDescriptor(ideaModuleInfo: IdeaModuleInfo) = projectFacade.findModuleDescriptor(ideaModuleInfo)

    override fun analyze(element: KtElement, bodyResolveMode: BodyResolveMode): BindingContext {
        return analyze(listOf(element), bodyResolveMode)
    }

    override fun analyze(elements: Collection<KtElement>, bodyResolveMode: BodyResolveMode): BindingContext {
        if (elements.isEmpty()) return BindingContext.EMPTY
        val resolveElementCache = getFrontendService(elements.first(), ResolveElementCache::class.java)
        return resolveElementCache.resolveToElements(elements, bodyResolveMode)
    }

    override fun analyzeFullyAndGetResult(elements: Collection<KtElement>): AnalysisResult
            = projectFacade.getAnalysisResultsForElements(elements)

    override fun resolveToDescriptor(declaration: KtDeclaration, bodyResolveMode: BodyResolveMode): DeclarationDescriptor {
        if (declaration.containingKtFile.isCompiled) {
            return resolveDecompiledDeclarationToDescriptor(this, declaration)
        }
        if (KtPsiUtil.isLocal(declaration)) {
            val bindingContext = analyze(declaration, bodyResolveMode)
            return bindingContext[BindingContext.DECLARATION_TO_DESCRIPTOR, declaration]
                   ?: getFrontendService(moduleInfo, AbsentDescriptorHandler::class.java).diagnoseDescriptorNotFound(declaration)
        }
        else {
            val resolveSession = projectFacade.resolverForModuleInfo(declaration.getModuleInfo()).componentProvider.get<ResolveSession>()
            return resolveSession.resolveToDescriptor(declaration)
        }
    }

    override fun <T : Any> getFrontendService(serviceClass: Class<T>): T = getFrontendService(moduleInfo, serviceClass)

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

fun ResolutionFacade.findModuleDescriptor(ideaModuleInfo: IdeaModuleInfo): ModuleDescriptor? {
    return (this as? ResolutionFacadeImpl)?.findModuleDescriptor(ideaModuleInfo)
}


private fun resolveDecompiledDeclarationToDescriptor(resolutionFacade: ResolutionFacade, declaration: KtDeclaration): DeclarationDescriptor {
    val decompiledFile = declaration.containingKtFile as KtDecompiledFile
    val moduleInfo = getModuleInfoByVirtualFile(declaration.project, decompiledFile.virtualFile, false)
    val moduleDescriptor = resolutionFacade.findModuleDescriptor(moduleInfo) ?: error("")
    val packageFqName = decompiledFile.packageFqName

    val packageFragment =
            when (decompiledFile.virtualFile.extension) {
                "class" -> {
                    val packageFragmentProvider = resolutionFacade.getFrontendService<LazyJavaPackageFragmentProvider>(moduleDescriptor, LazyJavaPackageFragmentProvider::class.java)
                    packageFragmentProvider.getPackageFragments(packageFqName).single()
                }
                "kotlin_builtins" -> {
                    moduleDescriptor.builtIns.builtInsModule.packageFragmentProvider.getPackageFragments(packageFqName).single()
                }
                else -> TODO()
            }

    val declarations = declaration.parentsWithSelf.filterIsInstance<KtNamedDeclaration>().toList().asReversed()
    var scope: MemberScope = packageFragment.getMemberScope()
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
            else -> TODO()
        }
    }
    TODO()
}

private fun getDeclarationIndex(declaration: KtNamedDeclaration): Int {
    val declarationContainer: KtDeclarationContainer = declaration.getNonStrictParentOfType<KtClassBody>() ?: declaration.containingKtFile
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
    val fileName = declaration.containingKtFile.virtualFile.nameWithoutExtension
    val descriptorsForThisFile = descriptors.filter {
        val memberSourceElement = (it.original as? DeclarationDescriptorWithSource)?.source as? DeserializedMemberSource
        val parentSource = memberSourceElement!!.containerSource
        getFileNameBySource(parentSource)?.equals(fileName) ?: true
    }

    if (descriptorsForThisFile.isEmpty()) {
        error("${declaration.text}")
    }

    return descriptorsForThisFile[index]
}

private fun getFileNameBySource(sourceElement: SourceElement): String? {
    return when (sourceElement) {
        is JvmPackagePartSource -> (sourceElement.facadeClassName ?: sourceElement.className).internalName.substringAfterLast('/')
        is KotlinJvmBinarySourceElement -> sourceElement.binaryClass.classId.shortClassName.asString()
        else -> null // TODO:
    }
}
