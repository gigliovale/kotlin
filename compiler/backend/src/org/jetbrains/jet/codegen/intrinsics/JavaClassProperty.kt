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

import org.jetbrains.jet.codegen.AsmUtil.boxType
import org.jetbrains.jet.codegen.AsmUtil.isPrimitive
import org.jetbrains.jet.lang.resolve.java.AsmTypeConstants.getType
import org.jetbrains.jet.codegen.CallableMethod
import org.jetbrains.jet.codegen.ExtendedCallable

public class JavaClassProperty : IntrinsicMethod() {
    override fun generateImpl(codegen: ExpressionCodegen, v: InstructionAdapter, returnType: Type, element: PsiElement?, arguments: List<JetExpression>?, receiver: StackValue?): Type {
        val `type` = receiver!!.`type`
        if (isPrimitive(`type`)) {
            v.getstatic(boxType(`type`).getInternalName(), "TYPE", "Ljava/lang/Class;")
        }
        else {
            receiver.put(`type`, v)
            v.invokevirtual("java/lang/Object", "getClass", "()Ljava/lang/Class;", false)
        }

        return getType(javaClass<Class<Any>>())
    }


    override fun supportCallable(): Boolean {
        return true
    }


    override fun toCallable(method: CallableMethod): IntrinsicCallable {
        return MappedCallable(method) {
            if (isPrimitive(calcReceiverType())) {
                it.getstatic(boxType(calcReceiverType()!!).getInternalName(), "TYPE", "Ljava/lang/Class;")
            }
            else {
                it.invokevirtual("java/lang/Object", "getClass", "()Ljava/lang/Class;", false)
            }
        }
    }
}
