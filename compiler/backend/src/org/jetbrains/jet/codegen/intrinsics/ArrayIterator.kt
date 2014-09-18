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
import org.jetbrains.jet.lang.descriptors.ClassDescriptor
import org.jetbrains.jet.lang.descriptors.FunctionDescriptor
import org.jetbrains.jet.lang.psi.JetCallExpression
import org.jetbrains.jet.lang.psi.JetExpression
import org.jetbrains.jet.lang.psi.JetSimpleNameExpression
import org.jetbrains.jet.lang.resolve.BindingContext
import org.jetbrains.jet.lang.resolve.java.JvmPrimitiveType
import org.jetbrains.jet.lang.resolve.name.FqName
import org.jetbrains.jet.lang.types.lang.KotlinBuiltIns
import org.jetbrains.jet.lang.types.lang.PrimitiveType
import org.jetbrains.org.objectweb.asm.Type
import org.jetbrains.org.objectweb.asm.commons.InstructionAdapter

import org.jetbrains.jet.codegen.AsmUtil.asmDescByFqNameWithoutInnerClasses
import org.jetbrains.jet.lang.resolve.java.AsmTypeConstants.getType
import org.jetbrains.jet.lang.resolve.java.mapping.PrimitiveTypesUtil.asmTypeForPrimitive
import org.jetbrains.jet.lang.types.lang.KotlinBuiltIns.BUILT_INS_PACKAGE_FQ_NAME
import org.jetbrains.jet.codegen.state.GenerationState
import org.jetbrains.jet.lang.descriptors.DeclarationDescriptor
import org.jetbrains.jet.codegen.context.CodegenContext
import org.jetbrains.jet.codegen.ExtendedCallable

public class ArrayIterator : IntrinsicMethod() {
    override fun generateImpl(codegen: ExpressionCodegen, v: InstructionAdapter, returnType: Type, element: PsiElement?, arguments: List<JetExpression>?, receiver: StackValue?): Type {
        receiver!!.put(receiver.`type`, v)
        val call = element as JetCallExpression
        val funDescriptor = codegen.getBindingContext().get(BindingContext.REFERENCE_TARGET, call.getCalleeExpression() as JetSimpleNameExpression) as FunctionDescriptor
        assert(funDescriptor != null)
        val containingDeclaration = funDescriptor.getContainingDeclaration().getOriginal() as ClassDescriptor
        if (containingDeclaration == KotlinBuiltIns.getInstance().getArray()) {
            v.invokestatic("kotlin/jvm/internal/InternalPackage", "iterator", "([Ljava/lang/Object;)Ljava/util/Iterator;", false)
            return getType(javaClass<Iterator<Any>>())
        }

        for (jvmPrimitiveType in JvmPrimitiveType.values()) {
            val primitiveType = jvmPrimitiveType.getPrimitiveType()
            val arrayClass = KotlinBuiltIns.getInstance().getPrimitiveArrayClassDescriptor(primitiveType)
            if (containingDeclaration == arrayClass) {
                val fqName = FqName(BUILT_INS_PACKAGE_FQ_NAME.toString() + "." + primitiveType.getTypeName() + "Iterator")
                val iteratorDesc = asmDescByFqNameWithoutInnerClasses(fqName)
                val methodSignature = "([" + asmTypeForPrimitive(jvmPrimitiveType) + ")" + iteratorDesc
                v.invokestatic("kotlin/jvm/internal/InternalPackage", "iterator", methodSignature, false)
                return Type.getType(iteratorDesc)
            }
        }

        throw UnsupportedOperationException(containingDeclaration.toString())
    }


    override fun supportCallable(): Boolean {
        return true
    }

    override fun toCallable(state: GenerationState, fd: FunctionDescriptor, isSuperCall: Boolean, context: CodegenContext<out DeclarationDescriptor?>): ExtendedCallable {
        val intrinsicCallable = state.getTypeMapper().mapToCallableMethod(fd, isSuperCall, context)
        val containingDeclaration = fd.getContainingDeclaration().getOriginal() as ClassDescriptor

        var returnType: Type? = null;
        if (containingDeclaration == KotlinBuiltIns.getInstance().getArray()) {
            returnType = getType(javaClass<Iterator<Any>>())
        } else {
            for (jvmPrimitiveType in JvmPrimitiveType.values()) {
                val primitiveType = jvmPrimitiveType.getPrimitiveType()
                val arrayClass = KotlinBuiltIns.getInstance().getPrimitiveArrayClassDescriptor(primitiveType)
                if (containingDeclaration == arrayClass) {
                    val fqName = FqName(BUILT_INS_PACKAGE_FQ_NAME.toString() + "." + primitiveType.getTypeName() + "Iterator")
                    val iteratorDesc = asmDescByFqNameWithoutInnerClasses(fqName)
                    returnType = Type.getType(iteratorDesc)
                    break;
                }
            }
        }

        if (returnType == null) {
            throw UnsupportedOperationException(containingDeclaration.toString())
        }

        return UnaryIntrinsic(intrinsicCallable, returnType) @lambda {
            UnaryIntrinsic.(it: InstructionAdapter) : Unit ->
            if (containingDeclaration == KotlinBuiltIns.getInstance().getArray()) {
                it.invokestatic("kotlin/jvm/internal/InternalPackage", "iterator", "([Ljava/lang/Object;)Ljava/util/Iterator;", false)
                return@lambda
            }

            for (jvmPrimitiveType in JvmPrimitiveType.values()) {
                val primitiveType = jvmPrimitiveType.getPrimitiveType()
                val arrayClass = KotlinBuiltIns.getInstance().getPrimitiveArrayClassDescriptor(primitiveType)
                if (containingDeclaration == arrayClass) {
                    val fqName = FqName(BUILT_INS_PACKAGE_FQ_NAME.toString() + "." + primitiveType.getTypeName() + "Iterator")
                    val iteratorDesc = asmDescByFqNameWithoutInnerClasses(fqName)
                    val methodSignature = "([" + asmTypeForPrimitive(jvmPrimitiveType) + ")" + iteratorDesc
                    it.invokestatic("kotlin/jvm/internal/InternalPackage", "iterator", methodSignature, false)
                    return@lambda
                }
            }

            throw UnsupportedOperationException(containingDeclaration.toString())
        }
    }
}
