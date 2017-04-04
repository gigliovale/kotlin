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

package org.jetbrains.kotlin.idea.intentions

import com.intellij.codeInsight.intention.LowPriorityAction
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.codeStyle.CodeStyleManager
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtParameterList
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import org.jetbrains.kotlin.psi.psiUtil.siblings
import org.jetbrains.kotlin.psi.psiUtil.startOffset

class ChopParameterListIntention : SelfTargetingOffsetIndependentIntention<KtParameterList>(
        KtParameterList::class.java,
        "Put parameters on separate lines"
), LowPriorityAction {
    override fun isApplicableTo(element: KtParameterList): Boolean {
        val parameters = element.parameters
        if (parameters.size <= 1) return false
        if (parameters.dropLast(1).all { hasLineBreakAfter(it) }) return false
        return true
    }

    override fun applyTo(element: KtParameterList, editor: Editor?) {
        val project = element.project
        val document = editor!!.document
        val startOffset = element.startOffset

        if (!hasLineBreakAfter(element.parameters.last())) {
            element.rightParenthesis?.startOffset?.let { document.insertString(it, "\n") }
        }

        for (parameter in element.parameters.asReversed()) {
            if (!hasLineBreakBefore(parameter)) {
                document.insertString(parameter.startOffset, "\n")
            }
        }

        val documentManager = PsiDocumentManager.getInstance(project)
        documentManager.commitDocument(document)
        val psiFile = documentManager.getPsiFile(document)!!
        val newParameterList = psiFile.findElementAt(startOffset)!!.getStrictParentOfType<KtParameterList>()!!
        CodeStyleManager.getInstance(project).adjustLineIndent(psiFile, newParameterList.textRange)
    }

    private fun hasLineBreakAfter(parameter: KtParameter): Boolean {
        return parameter
                .siblings(withItself = false)
                .takeWhile { it !is KtParameter }
                .any { it is PsiWhiteSpace && it.textContains('\n') }
    }

    private fun hasLineBreakBefore(parameter: KtParameter): Boolean {
        return parameter
                .siblings(withItself = false, forward = false)
                .takeWhile { it !is KtParameter }
                .any { it is PsiWhiteSpace && it.textContains('\n') }
    }
}
