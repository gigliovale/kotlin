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

package org.jetbrains.kotlin.backend.jvm.lower

import org.jetbrains.annotations.NotNull
import org.jetbrains.annotations.Nullable
import org.jetbrains.kotlin.codegen.AsmUtil
import org.jetbrains.kotlin.codegen.OwnerKind
import org.jetbrains.kotlin.codegen.context.CodegenContext
import org.jetbrains.kotlin.descriptors.CallableMemberDescriptor
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.descriptors.FunctionDescriptor
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrMemberAccessExpression
import org.jetbrains.org.objectweb.asm.Opcodes

private class StubCodegenContext(
        contextDescriptor: ClassDescriptor,
        parentContext: StubCodegenContext?,
        val classContext: ClassContext
) : CodegenContext<DeclarationDescriptor>(
        contextDescriptor, OwnerKind.IMPLEMENTATION, parentContext, null,
        if (contextDescriptor is FileClassDescriptor) null else contextDescriptor,
        null
)

class SyntheticAccessorLowering : ClassLowerWithContext() {

    private val context2Codegen = hashMapOf<ClassContext, StubCodegenContext>()

    private val ClassContext.codegenContext: StubCodegenContext
        get() = context2Codegen[this]!!


    override fun lowerBefore(irCLass: IrClass, context: ClassContext) {
        val codegenContext = StubCodegenContext(irCLass.descriptor, context.parent?.codegenContext, context)
        context2Codegen.put(context, codegenContext)
    }

    override fun lower(irCLass: IrClass, context: ClassContext) {
//        val codegenContext = StubCodegenContext(irCLass.descriptor, context.codegenContext, context)
//        context2Codegen.put(context, codegenContext)
    }


    override fun visitMemberAccess(expression: IrMemberAccessExpression, data: ClassContext?): IrElement {
        val superResult = super.visitMemberAccess(expression, data)
        val descriptor = expression.descriptor
        if (descriptor is FunctionDescriptor) {
            val accessor = data!!.codegenContext.accessibleDescriptor(descriptor, (descriptor as? IrCall)?.superQualifier)
            if (descriptor != accessor) {
                descriptor.toStatic(descriptor.containingDeclaration as ClassDescriptor, name = accessor.name)
            }
        }
        return superResult
    }
}