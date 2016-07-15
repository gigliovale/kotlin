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

package org.jetbrains.kotlin.idea.refactoring.inline

import com.intellij.lang.Language
import com.intellij.lang.refactoring.InlineActionHandler
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.WindowManager
import com.intellij.psi.PsiElement
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.refactoring.HelpID
import com.intellij.refactoring.RefactoringBundle
import com.intellij.refactoring.util.CommonRefactoringUtil
import com.intellij.usageView.UsageInfo
import org.jetbrains.kotlin.descriptors.TypeAliasDescriptor
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.caches.resolve.resolveToDescriptor
import org.jetbrains.kotlin.idea.codeInsight.shorten.performDelayedShortening
import org.jetbrains.kotlin.idea.imports.importableFqName
import org.jetbrains.kotlin.idea.refactoring.move.ContainerChangeInfo
import org.jetbrains.kotlin.idea.refactoring.move.ContainerInfo
import org.jetbrains.kotlin.idea.refactoring.move.lazilyProcessInternalReferencesToUpdateOnPackageNameChange
import org.jetbrains.kotlin.idea.references.KtSimpleNameReference
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.idea.util.IdeDescriptorRenderers
import org.jetbrains.kotlin.idea.util.application.executeWriteCommand
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getNonStrictParentOfType
import org.jetbrains.kotlin.psi.psiUtil.getParentOfTypeAndBranch
import org.jetbrains.kotlin.psi.psiUtil.getQualifiedElementSelector
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.calls.callUtil.getResolvedCall
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode
import org.jetbrains.kotlin.types.TypeProjectionImpl
import org.jetbrains.kotlin.types.TypeSubstitutor
import org.jetbrains.kotlin.types.Variance
import java.util.*

class KotlinInlineTypeAliasHandler : InlineActionHandler() {
    companion object {
        val REFACTORING_NAME = "Inline Type Alias"
    }

    private fun showErrorHint(project: Project, editor: Editor?, message: String) {
        CommonRefactoringUtil.showErrorHint(project, editor, message, REFACTORING_NAME, null)
    }

    override fun isEnabledForLanguage(l: Language?) = l == KotlinLanguage.INSTANCE

    override fun canInlineElement(element: PsiElement?) = element is KtTypeAlias

    override fun inlineElement(project: Project, editor: Editor?, element: PsiElement) {
        val typeAlias = element as? KtTypeAlias ?: return
        val name = typeAlias.name ?: return
        val aliasBody = typeAlias.getTypeReference() ?: return
        val file = typeAlias.getContainingKtFile()

        val typeAliasDescriptor = typeAlias.resolveToDescriptor() as TypeAliasDescriptor
        val typeToInline = typeAliasDescriptor.expandedType
        val typeConstructorsToInline = typeAliasDescriptor.underlyingType.arguments.map { it.type.constructor }

        val usages = ReferencesSearch.search(typeAlias).mapNotNull {
            val refElement = it.element
            refElement.getParentOfTypeAndBranch<KtUserType> { referenceExpression }
            ?: refElement.getNonStrictParentOfType<KtSimpleNameExpression>()
        }

        if (usages.isEmpty()) return showErrorHint(project, editor, "Type alias '$name' is never used")

        val usagesInOriginalFile = usages.filter { it.containingFile == file }
        val isHighlighting = usagesInOriginalFile.isNotEmpty()
        highlightExpressions(project, editor, usagesInOriginalFile)

        if (usagesInOriginalFile.size != usages.size) {
            val targetPackages = usages.mapNotNullTo(LinkedHashSet()) { it.getContainingKtFile().packageFqName }
            for (targetPackage in targetPackages) {
                if (targetPackage == file.packageFqName) continue
                val packageNameInfo = ContainerChangeInfo(ContainerInfo.Package(file.packageFqName), ContainerInfo.Package(targetPackage))
                aliasBody.lazilyProcessInternalReferencesToUpdateOnPackageNameChange(packageNameInfo) { expr, factory ->
                    val infos = expr.internalUsageInfos
                                ?: LinkedHashMap<FqName, (KtSimpleNameExpression) -> UsageInfo?>()
                                        .apply { expr.internalUsageInfos = this }
                    infos[targetPackage] = factory
                }
            }
        }

        if (!showDialog(project,
                        name,
                        REFACTORING_NAME,
                        typeAlias,
                        usages,
                        HelpID.INLINE_VARIABLE)) {
            if (isHighlighting) {
                val statusBar = WindowManager.getInstance().getStatusBar(project)
                statusBar?.info = RefactoringBundle.message("press.escape.to.remove.the.highlighting")
            }
            return
        }

        val psiFactory = KtPsiFactory(project)

        project.executeWriteCommand(RefactoringBundle.message("inline.command", name)) {
            val inlinedElements = usages.mapNotNull { usage ->
                val context = usage.analyze(BodyResolveMode.PARTIAL)

                when (usage) {
                    is KtUserType -> {
                        val argumentTypes = usage
                                .typeArguments
                                .mapNotNull { context[BindingContext.TYPE, it.typeReference]?.let { TypeProjectionImpl(it) } }
                        if (argumentTypes.size != typeConstructorsToInline.size) return@mapNotNull null
                        val substitution = (typeConstructorsToInline zip argumentTypes).toMap()
                        val substitutor = TypeSubstitutor.create(substitution)
                        val expandedType = substitutor.substitute(typeToInline, Variance.INVARIANT) ?: return@mapNotNull null
                        val expandedTypeReference = psiFactory.createType(IdeDescriptorRenderers.SOURCE_CODE.renderType(expandedType))
                        usage.replace(expandedTypeReference.typeElement!!)
                    }

                    is KtReferenceExpression -> {
                        val importDirective = usage.getStrictParentOfType<KtImportDirective>()
                        if (importDirective != null) {
                            val reference = usage.getQualifiedElementSelector()?.mainReference
                            if (reference != null && reference.multiResolve(false).size <= 1) {
                                importDirective.delete()
                            }

                            return@mapNotNull null
                        }

                        val resolvedCall = usage.getResolvedCall(context) ?: return@mapNotNull null
                        val callElement = resolvedCall.call.callElement as? KtCallElement ?: return@mapNotNull null
                        val substitution = resolvedCall.typeArguments
                                .mapKeys { it.key.typeConstructor }
                                .mapValues { TypeProjectionImpl(it.value) }
                        if (substitution.size != typeConstructorsToInline.size) return@mapNotNull null
                        val substitutor = TypeSubstitutor.create(substitution)
                        val expandedType = substitutor.substitute(typeToInline, Variance.INVARIANT) ?: return@mapNotNull null
                        val expandedTypeFqName = expandedType.constructor.declarationDescriptor?.importableFqName ?: return@mapNotNull null
                        val expandedTypeArgumentList = psiFactory.createTypeArguments(
                                expandedType.arguments.joinToString(prefix = "<",
                                                                    postfix = ">") { IdeDescriptorRenderers.SOURCE_CODE.renderType(it.type) }
                        )

                        (usage.mainReference as KtSimpleNameReference).bindToFqName(expandedTypeFqName)
                        val originalTypeArgumentList = callElement.typeArgumentList
                        if (originalTypeArgumentList != null) {
                            originalTypeArgumentList.replace(expandedTypeArgumentList)
                        }
                        else {
                            callElement.addAfter(expandedTypeArgumentList, callElement.calleeExpression)
                        }
                    }

                    else -> null
                }
            }

            if (inlinedElements.isNotEmpty() && isHighlighting) {
                highlightExpressions(project, editor, inlinedElements)
            }

            typeAlias.delete()

            performDelayedShortening(project)
        }
    }
}