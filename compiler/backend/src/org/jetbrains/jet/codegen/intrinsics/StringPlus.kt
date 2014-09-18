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

import org.jetbrains.jet.lang.resolve.java.AsmTypeConstants.JAVA_STRING_TYPE
import org.jetbrains.jet.lang.resolve.java.AsmTypeConstants.OBJECT_TYPE
import org.jetbrains.jet.codegen.state.GenerationState
import org.jetbrains.jet.lang.descriptors.FunctionDescriptor
import org.jetbrains.jet.lang.descriptors.DeclarationDescriptor
import org.jetbrains.jet.codegen.context.CodegenContext
import org.jetbrains.jet.codegen.ExtendedCallable
import org.jetbrains.jet.codegen.AsmUtil.genEqualsForExpressionsOnStack
import org.jetbrains.jet.lexer.JetTokens
import org.jetbrains.jet.codegen.CallableMethod

public class StringPlus : IntrinsicMethod() {
    override fun generateImpl(codegen: ExpressionCodegen, v: InstructionAdapter, returnType: Type, element: PsiElement?, arguments: List<JetExpression>?, receiver: StackValue?): Type {
        if (receiver == null || receiver == StackValue.none()) {
            codegen.gen(arguments!!.get(0)).put(JAVA_STRING_TYPE, v)
            codegen.gen(arguments.get(1)).put(OBJECT_TYPE, v)
        }
        else {
            receiver.put(JAVA_STRING_TYPE, v)
            codegen.gen(arguments!!.get(0)).put(OBJECT_TYPE, v)
        }
        v.invokestatic("kotlin/jvm/internal/Intrinsics", "stringPlus", "(Ljava/lang/String;Ljava/lang/Object;)Ljava/lang/String;", false)
        return JAVA_STRING_TYPE
    }

    override fun toCallable(method: CallableMethod): IntrinsicCallable {
        return IntrinsicCallable.create(method) {
            it.invokestatic("kotlin/jvm/internal/Intrinsics", "stringPlus", "(Ljava/lang/String;Ljava/lang/Object;)Ljava/lang/String;", false)
        }
    }

    override fun supportCallable(): Boolean {
        return true
    }
}
