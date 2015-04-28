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

package org.jetbrains.kotlin.idea.liveTemplates

import com.intellij.codeInsight.template.EverywhereContextType
import com.intellij.codeInsight.template.TemplateContextType
import com.intellij.openapi.util.Condition
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.PsiUtilCore
import org.jetbrains.annotations.NonNls
import org.jetbrains.kotlin.idea.JetLanguage
import org.jetbrains.kotlin.lexer.JetTokens
import org.jetbrains.kotlin.psi.*

public abstract class KotlinTemplateContextType protected (NonNls id: String,
                                                           presentableName: String,
                                                           baseContextType: java.lang.Class<out TemplateContextType>?) : TemplateContextType(id, presentableName, baseContextType) {

    override fun isInContext(file: PsiFile, offset: Int): Boolean {
        if (PsiUtilCore.getLanguageAtOffset(file, offset).isKindOf(JetLanguage.INSTANCE)) {
            var element = file.findElementAt(offset)
            if (element == null) {
                element = file.findElementAt(offset - 1)
            }
            if (element is PsiWhiteSpace) {
                return false
            }
            else if (PsiTreeUtil.getParentOfType<PsiComment>(element, javaClass<PsiComment>(), false) != null) {
                return isCommentInContext()
            }
            else if (PsiTreeUtil.getParentOfType<JetPackageDirective>(element, javaClass<JetPackageDirective>()) != null || PsiTreeUtil.getParentOfType<JetImportDirective>(element, javaClass<JetImportDirective>()) != null) {
                return false
            }
            else if (element is LeafPsiElement) {
                val elementType = element.getElementType()
                if (elementType == JetTokens.IDENTIFIER) {
                    if (element.getParent() is JetReferenceExpression) {
                        val parentOfParent = element.getParent().getParent()
                        val qualifiedExpression = PsiTreeUtil.getParentOfType<JetQualifiedExpression>(element, javaClass<JetQualifiedExpression>())
                        if (qualifiedExpression != null && qualifiedExpression.getSelectorExpression() == parentOfParent) {
                            return false
                        }
                    }
                    else {
                        return false
                    }
                }
            }
            return element != null && isInContext(element)
        }

        return false
    }

    protected open fun isCommentInContext(): Boolean {
        return false
    }

    protected abstract fun isInContext(element: PsiElement): Boolean

    public class Generic : KotlinTemplateContextType("KOTLIN", JetLanguage.NAME, javaClass<EverywhereContextType>()) {

        override fun isInContext(element: PsiElement): Boolean {
            return true
        }

        override fun isCommentInContext(): Boolean {
            return true
        }
    }

    public class TopLevel : KotlinTemplateContextType("KOTLIN_TOPLEVEL", "Top-level", javaClass<Generic>()) {

        override fun isInContext(element: PsiElement): Boolean {
            var e: PsiElement? = element
            while (e != null) {
                if (e is JetModifierList) {
                    // skip property/function/class or object which is owner of modifier list
                    e = e.getParent()
                    if (e != null) {
                        e = e.getParent()
                    }
                    continue
                }
                if (e is JetProperty || e is JetNamedFunction || e is JetClassOrObject) {
                    return false
                }
                e = e.getParent()
            }
            return true
        }
    }

    public class Class : KotlinTemplateContextType("KOTLIN_CLASS", "Class", javaClass<Generic>()) {

        override fun isInContext(element: PsiElement): Boolean {
            var e: PsiElement? = element
            while (e != null && e !is JetClassOrObject) {
                if (e is JetModifierList) {
                    // skip property/function/class or object which is owner of modifier list
                    e = e.getParent()
                    if (e != null) {
                        e = e.getParent()
                    }
                    continue
                }
                if (e is JetProperty || e is JetNamedFunction) {
                    return false
                }
                e = e.getParent()
            }
            return e != null
        }
    }

    public class Statement : KotlinTemplateContextType("KOTLIN_STATEMENT", "Statement", javaClass<Generic>()) {

        override fun isInContext(element: PsiElement): Boolean {
            val parentStatement = PsiTreeUtil.findFirstParent(element, object : Condition<PsiElement> {
                override fun value(element: PsiElement): Boolean {
                    return element is JetExpression && (element.getParent() is JetBlockExpression)
                }
            })

            if (parentStatement == null) return false

            // We are in the leftmost position in parentStatement
            return element.getTextOffset() == parentStatement.getTextOffset()
        }
    }

    public class Expression : KotlinTemplateContextType("KOTLIN_EXPRESSION", "Expression", javaClass<Generic>()) {

        override fun isInContext(element: PsiElement): Boolean {
            return element.getParent() is JetExpression &&
                   element.getParent() !is JetConstantExpression &&
                   element.getParent().getParent() !is JetDotQualifiedExpression &&
                   element.getParent() !is JetParameter
        }
    }

    public class Comment : KotlinTemplateContextType("KOTLIN_COMMENT", "Comment", javaClass<Generic>()) {

        override fun isInContext(element: PsiElement): Boolean {
            return false
        }

        override fun isCommentInContext(): Boolean {
            return true
        }
    }
}
