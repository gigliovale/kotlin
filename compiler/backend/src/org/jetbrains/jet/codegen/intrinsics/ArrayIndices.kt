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

import org.jetbrains.jet.lang.resolve.java.AsmTypeConstants.INT_RANGE_TYPE
import org.jetbrains.org.objectweb.asm.Type.INT_TYPE
import org.jetbrains.jet.codegen.state.GenerationState
import org.jetbrains.jet.lang.descriptors.FunctionDescriptor
import org.jetbrains.jet.codegen.context.CodegenContext
import org.jetbrains.jet.codegen.ExtendedCallable
import org.jetbrains.jet.codegen.CallableMethod

public class ArrayIndices : IntrinsicMethod() {
    override fun generateImpl(codegen: ExpressionCodegen, v: InstructionAdapter, returnType: Type, element: PsiElement?, arguments: List<JetExpression>?, receiver: StackValue?): Type {
        receiver!!.put(receiver.`type`, v)
        v.arraylength()
        v.invokestatic("kotlin/jvm/internal/Intrinsics", "arrayIndices", Type.getMethodDescriptor(INT_RANGE_TYPE, INT_TYPE), false)
        return INT_RANGE_TYPE
    }

    override fun supportCallable(): Boolean {
        return true
    }


    override fun toCallable(method: CallableMethod): IntrinsicCallable {
        return UnaryIntrinsic(method) {
            it.arraylength()
            it.invokestatic("kotlin/jvm/internal/Intrinsics", "arrayIndices", Type.getMethodDescriptor(INT_RANGE_TYPE, INT_TYPE), false)
        }
    }
}
