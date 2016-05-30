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

package org.jetbrains.kotlin.idea.refactoring.rename

import com.intellij.psi.PsiElement
import com.intellij.refactoring.rename.UnresolvableCollisionUsageInfo
import com.intellij.refactoring.util.MoveRenameUsageInfo
import com.intellij.usageView.UsageInfo
import org.jetbrains.kotlin.asJava.unwrapped
import org.jetbrains.kotlin.descriptors.FunctionDescriptor
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.caches.resolve.resolveToDescriptor
import org.jetbrains.kotlin.idea.refactoring.KotlinRefactoringBundle
import org.jetbrains.kotlin.idea.references.AbstractKtReference
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtSimpleNameExpression
import org.jetbrains.kotlin.resolve.calls.callUtil.getResolvedCall
import org.jetbrains.kotlin.resolve.calls.model.DefaultValueArgument
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode
import org.jetbrains.kotlin.resolve.source.getPsi

fun checkConflictsAndReplaceUsageInfos(
        element: PsiElement,
        allRenames: Map<out PsiElement?, String>,
        result: MutableList<UsageInfo>
) {
    val functionWithInheritedDefaultValues = element.isFunctionWithInheritedDefaultValues(allRenames)

    val usageIterator = result.listIterator()
    while (usageIterator.hasNext()) {
        val usageInfo = usageIterator.next()
        val ref = usageInfo.reference
        if (usageInfo !is MoveRenameUsageInfo || ref !is AbstractKtReference<*>) continue

        val refElement = usageInfo.element ?: continue
        val referencedElement = usageInfo.referencedElement ?: continue

        if (functionWithInheritedDefaultValues && refElement is KtSimpleNameExpression) {
            val resolvedCall = refElement.getResolvedCall(refElement.analyze(BodyResolveMode.PARTIAL)) ?: continue
            if (resolvedCall.valueArgumentsByIndex?.any { it is DefaultValueArgument } ?: false) {
                usageIterator.set(LostDefaultValuesUsageInfo(resolvedCall.call.callElement, referencedElement))
            }
        }

        if (!ref.canRename()) {
            usageIterator.set(UnresolvableConventionViolationUsageInfo(refElement, referencedElement))
        }
    }
}

private fun PsiElement.isFunctionWithInheritedDefaultValues(allRenames: Map<out PsiElement?, String>): Boolean {
    val elementsToRename = allRenames.keys.mapNotNull { it?.unwrapped }
    val function = unwrapped as? KtNamedFunction ?: return false
    val descriptor = function.resolveToDescriptor() as FunctionDescriptor
    val overriddenDescriptors = descriptor.overriddenDescriptors
    return overriddenDescriptors.any {
        it.source.getPsi() !in elementsToRename && it.valueParameters.any { it.declaresDefaultValue() }
    }
}

class UnresolvableConventionViolationUsageInfo(
        element: PsiElement,
        referencedElement: PsiElement
) : UnresolvableCollisionUsageInfo(element, referencedElement) {
    override fun getDescription(): String = KotlinRefactoringBundle.message("naming.convention.will.be.violated.after.rename")
}

class LostDefaultValuesUsageInfo(
        callElement: KtElement,
        referencedElement: PsiElement
) : UnresolvableCollisionUsageInfo(callElement, referencedElement) {
    override fun getDescription(): String = "Default values for parameter(s) won't be available after rename: ${element!!.text}"
}