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

package org.jetbrains.kotlin.idea.debugger.render

import com.intellij.debugger.DebuggerBundle
import com.intellij.debugger.engine.JavaValue
import com.intellij.debugger.engine.evaluation.EvaluateException
import com.intellij.debugger.engine.evaluation.EvaluationContext
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.intellij.debugger.impl.DebuggerContextImpl
import com.intellij.debugger.ui.impl.watch.DebuggerTreeNodeExpression
import com.intellij.debugger.ui.impl.watch.FieldDescriptorImpl
import com.intellij.debugger.ui.impl.watch.ValueDescriptorImpl
import com.intellij.debugger.ui.tree.DebuggerTreeNode
import com.intellij.debugger.ui.tree.FieldDescriptor
import com.intellij.debugger.ui.tree.NodeDescriptorFactory
import com.intellij.debugger.ui.tree.render.ClassRenderer
import com.intellij.debugger.ui.tree.render.NodeRenderer
import com.intellij.openapi.project.Project
import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.PsiUtil
import com.intellij.util.IncorrectOperationException
import com.sun.jdi.Field
import com.sun.jdi.ObjectReference
import com.sun.jdi.Value
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtPsiFactory
import com.sun.jdi.Type as JdiType
import org.jetbrains.org.objectweb.asm.Type as AsmType

class KotlinClassRenderer : ClassRenderer() {
    override fun createFieldDescriptor(
            parentDescriptor: ValueDescriptorImpl?,
            nodeDescriptorFactory: NodeDescriptorFactory?,
            objRef: ObjectReference?, field: Field?,
            evaluationContext: EvaluationContext?
    ): FieldDescriptor {
        if (evaluationContext is EvaluationContextImpl && parentDescriptor != null && objRef != null && field != null) {
            val sourcePosition = evaluationContext.debugProcess.debuggerContext.sourcePosition
            if (sourcePosition.file is KtFile) {
                return KotlinFieldDescriptor(parentDescriptor.project, objRef, field)
            }
        }
        return super.createFieldDescriptor(parentDescriptor, nodeDescriptorFactory, objRef, field, evaluationContext)
    }
}

class KotlinFieldDescriptor(project: Project, objRef: ObjectReference, field: Field) : FieldDescriptorImpl(project, objRef, field) {
    override fun getTreeEvaluation(value: JavaValue, context: DebuggerContextImpl): PsiElement? {
        val parent = value.parent
        if (parent != null) {
            val vDescriptor = parent.descriptor
            var parentEvaluation = vDescriptor.getTreeEvaluation(parent, context) ?: return null

            if (parentEvaluation !is KtExpression) {
                parentEvaluation = KtPsiFactory(project).createExpression(parentEvaluation.text)
            }

            var expressionWithThis: PsiElement? = vDescriptor.getRenderer(context.debugProcess).getChildValueExpression(DebuggerTreeNodeMock(value), context)
            if (expressionWithThis != null && expressionWithThis !is KtExpression) {
                expressionWithThis = KtPsiFactory(project).createExpression(expressionWithThis.text)
            }
            return substituteThis(
                    expressionWithThis as? KtExpression,
                    parentEvaluation, vDescriptor.value)
        }

        return getDescriptorEvaluation(context)
    }

    @Throws(EvaluateException::class)
    fun substituteThis(expressionWithThis: KtExpression?, howToEvaluateThis: KtExpression, howToEvaluateThisValue: Value): PsiExpression? {
        if (expressionWithThis == null) return null
        val result = expressionWithThis.copy() as PsiExpression

        val thisClass = PsiTreeUtil.getContextOfType(result, PsiClass::class.java, true)

        var castNeeded = true

        if (thisClass != null) {
            /*val type = howToEvaluateThis.type
            if (type != null) {
                if (type is PsiClassType) {
                    val psiClass = type.resolve()
                    if (psiClass != null && (psiClass === thisClass || psiClass.isInheritor(thisClass, true))) {
                        castNeeded = false
                    }
                }
                else if (type is PsiArrayType) {
                    val languageLevel = PsiUtil.getLanguageLevel(expressionWithThis)
                    if (thisClass === JavaPsiFacade.getInstance(expressionWithThis.project).elementFactory.getArrayClass(languageLevel)) {
                        castNeeded = false
                    }
                }
            }*/
        }

        if (castNeeded) {
//            howToEvaluateThis = DebuggerTreeNodeExpression.castToRuntimeType(howToEvaluateThis, howToEvaluateThisValue)
        }

        /*ChangeContextUtil.encodeContextInfo(result, false)
        val psiExpression: KtExpression
        try {
            psiExpression = ChangeContextUtil.decodeContextInfo(result, thisClass, howToEvaluateThis) as KtExpression
        }
        catch (e: IncorrectOperationException) {
            throw EvaluateException(
                    DebuggerBundle.message("evaluation.error.invalid.this.expression", result.text, howToEvaluateThis.text), null)
        }*/

        try {
            val res = JavaPsiFacade.getInstance(howToEvaluateThis.project).elementFactory.createExpressionFromText(howToEvaluateThis.text, howToEvaluateThis.context)
            res.putUserData(DebuggerTreeNodeExpression.ADDITIONAL_IMPORTS_KEY, howToEvaluateThis.getUserData<Set<String>>(DebuggerTreeNodeExpression.ADDITIONAL_IMPORTS_KEY))
            return res
        }
        catch (e: IncorrectOperationException) {
            throw EvaluateException(e.message, e)
        }

    }

    private class DebuggerTreeNodeMock(private val value: JavaValue) : DebuggerTreeNode {

        override fun getParent(): DebuggerTreeNode {
            return DebuggerTreeNodeMock(value.parent)
        }

        override fun getDescriptor(): ValueDescriptorImpl {
            return value.descriptor
        }

        override fun getProject(): Project {
            return value.project
        }

        override fun setRenderer(renderer: NodeRenderer) {
        }
    }


}
