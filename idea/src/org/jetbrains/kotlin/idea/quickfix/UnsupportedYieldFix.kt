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

package org.jetbrains.kotlin.idea.quickfix

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.diagnostics.Errors
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtPsiFactory

class UnsupportedYieldFix(val psiElement: PsiElement): KotlinQuickFixAction<PsiElement>(psiElement), CleanupFix {
    override fun getFamilyName(): String = "Migrate unsupported yield syntax"
    override fun getText(): String  = familyName

    override fun invoke(project: Project, editor: Editor?, file: KtFile) {
        if (psiElement is KtCallExpression) {
            val ktExpression = psiElement.calleeExpression ?: return

            // Add after "yield" reference in call
            psiElement.addAfter(KtPsiFactory(psiElement).createCallArguments("()"), ktExpression)
        }

        if (psiElement.node.elementType == KtTokens.IDENTIFIER) {
            psiElement.replace(KtPsiFactory(psiElement).createIdentifier("`yield`"))
        }
    }

    companion object : KotlinSingleIntentionActionFactory() {
        override fun createAction(diagnostic: Diagnostic): IntentionAction? {
            if (diagnostic.psiElement.text != "yield") return null

            val message = Errors.UNSUPPORTED.cast(diagnostic).a
            if (message.startsWith("yield block/lambda")) {
                // Identifier -> Expression -> Call (normal call) or Identifier -> Operation Reference -> Binary Expression (for infix usage)
                val grand = diagnostic.psiElement.parent.parent
                if (grand is KtBinaryExpression || grand is KtCallExpression) {
                    return UnsupportedYieldFix(grand)
                }
            }

            if (message.startsWith("yield identifier")) {
                return UnsupportedYieldFix(diagnostic.psiElement)
            }

            return null
        }
    }
}