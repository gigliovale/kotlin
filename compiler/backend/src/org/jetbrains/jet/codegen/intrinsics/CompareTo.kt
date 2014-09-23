/*
 * Copyright 2010-2014 JetBrains s.r.o.
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

package org.jetbrains.jet.codegen.intrinsics

import com.intellij.psi.PsiElement
import org.jetbrains.jet.codegen.ExpressionCodegen
import org.jetbrains.jet.codegen.StackValue
import org.jetbrains.jet.lang.psi.JetExpression
import org.jetbrains.org.objectweb.asm.Type
import org.jetbrains.org.objectweb.asm.commons.InstructionAdapter

import org.jetbrains.jet.codegen.AsmUtil.comparisonOperandType
import org.jetbrains.jet.codegen.CallableMethod
import org.jetbrains.jet.codegen.ExtendedCallable

public class CompareTo : IntrinsicMethod() {
    override fun generateImpl(codegen: ExpressionCodegen, v: InstructionAdapter, returnType: Type, element: PsiElement?, arguments: List<JetExpression>?, receiver: StackValue?): Type {
        var receiver = receiver
        val argument: JetExpression
        assert(arguments != null)
        if (arguments!!.size() == 1) {
            argument = arguments.get(0)
        }
        else if (arguments.size() == 2) {
            receiver = codegen.gen(arguments.get(0))
            argument = arguments.get(1)
        }
        else {
            throw IllegalStateException("Invalid arguments to compareTo: " + arguments)
        }
        val `type` = comparisonOperandType(receiver!!.`type`, codegen.expressionType(argument))

        receiver!!.put(`type`, v)
        codegen.gen(argument, `type`)

        if (`type` == Type.INT_TYPE) {
            v.invokestatic("kotlin/jvm/internal/Intrinsics", "compare", "(II)I", false)
        }
        else if (`type` == Type.LONG_TYPE) {
            v.invokestatic("kotlin/jvm/internal/Intrinsics", "compare", "(JJ)I", false)
        }
        else if (`type` == Type.FLOAT_TYPE) {
            v.invokestatic("java/lang/Float", "compare", "(FF)I", false)
        }
        else if (`type` == Type.DOUBLE_TYPE) {
            v.invokestatic("java/lang/Double", "compare", "(DD)I", false)
        }
        else {
            throw UnsupportedOperationException()
        }

        return Type.INT_TYPE
    }


    override fun supportCallable(): Boolean {
        return true
    }

    override fun toCallable(method: CallableMethod): IntrinsicCallable {
        val operandType = comparisonOperandType(method.calcReceiverType(), method.getValueParameterTypes().head)
        return IntrinsicCallable.binaryIntrinsic(method, operandType, receiverTransformer = {operandType}) {
            val `type` = comparisonOperandType(calcReceiverType()!!, method.getValueParameterTypes().head)
            if (`type` == Type.INT_TYPE) {
                it.invokestatic("kotlin/jvm/internal/Intrinsics", "compare", "(II)I", false)
            }
            else if (`type` == Type.LONG_TYPE) {
                it.invokestatic("kotlin/jvm/internal/Intrinsics", "compare", "(JJ)I", false)
            }
            else if (`type` == Type.FLOAT_TYPE) {
                it.invokestatic("java/lang/Float", "compare", "(FF)I", false)
            }
            else if (`type` == Type.DOUBLE_TYPE) {
                it.invokestatic("java/lang/Double", "compare", "(DD)I", false)
            }
            else {
                throw UnsupportedOperationException()
            }
        }
    }
}
