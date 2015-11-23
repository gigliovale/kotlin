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

package org.jetbrains.kotlin.codegen.intrinsics

import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.codegen.*
import org.jetbrains.kotlin.codegen.AsmUtil.genInvokeAppendMethod
import org.jetbrains.kotlin.codegen.AsmUtil.genStringBuilderConstructor
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtCallableReferenceExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.resolve.calls.model.ResolvedCall
import org.jetbrains.kotlin.resolve.jvm.AsmTypes
import org.jetbrains.kotlin.resolve.jvm.AsmTypes.JAVA_STRING_TYPE
import org.jetbrains.org.objectweb.asm.Type
import org.jetbrains.org.objectweb.asm.commons.InstructionAdapter

public class Concat : IntrinsicMethod() {
    fun generateImpl(
            codegen: ExpressionCodegen,
            v: InstructionAdapter,
            returnType: Type,
            element: PsiElement?,
            arguments: List<KtExpression>,
            receiver: StackValue
    ): Type {
        if (element is KtBinaryExpression && element.operationReference.getReferencedNameElementType() == KtTokens.PLUS) {
            // LHS + RHS
            genStringBuilderConstructor(v)
            codegen.invokeAppend(element.left)
            codegen.invokeAppend(element.right)
        }
        else {
            // LHS?.plus(RHS)
            receiver.put(AsmTypes.OBJECT_TYPE, v)
            genStringBuilderConstructor(v)
            v.swap()
            genInvokeAppendMethod(v, returnType)
            codegen.invokeAppend(arguments.get(0))
        }

        v.invokevirtual("java/lang/StringBuilder", "toString", "()Ljava/lang/String;", false)
        return JAVA_STRING_TYPE
    }

    override fun toCallable(method: CallableMethod): Callable =
            object : IntrinsicCallable(method) {
                override fun invokeMethodWithArguments(
                        resolvedCall: ResolvedCall<*>,
                        receiver: StackValue,
                        codegen: ExpressionCodegen
                ): StackValue {
                    if (resolvedCall.call.callElement.parent is KtCallableReferenceExpression) {
                        // NB we come here only in case of inlined callable reference to String::plus. See KT-10131.
                        // This will map arguments properly, invoking callbacks defined in Callable.
                        return super.invokeMethodWithArguments(resolvedCall, receiver, codegen)
                    }
                    return StackValue.operation(returnType) {
                        val arguments = resolvedCall.call.valueArguments.map { it.getArgumentExpression()!! }
                        val actualType = generateImpl(
                                codegen, it, returnType,
                                resolvedCall.call.callElement,
                                arguments,
                                StackValue.receiver(resolvedCall, receiver, codegen, this)
                        )
                        StackValue.coerce(actualType, returnType, it)
                    }
                }

                override fun afterReceiverGeneration(v: InstructionAdapter) {
                    genStringBuilderConstructor(v)
                    v.swap()
                    AsmUtil.genInvokeAppendMethod(v, AsmTypes.OBJECT_TYPE)
                }

                override fun invokeIntrinsic(v: InstructionAdapter) {
                    AsmUtil.genInvokeAppendMethod(v, AsmTypes.OBJECT_TYPE)
                    v.invokevirtual("java/lang/StringBuilder", "toString", "()Ljava/lang/String;", false)
                }
            }
}
