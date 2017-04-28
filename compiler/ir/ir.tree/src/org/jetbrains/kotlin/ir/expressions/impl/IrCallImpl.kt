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

package org.jetbrains.kotlin.ir.expressions.impl

import org.jetbrains.kotlin.descriptors.CallableDescriptor
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.TypeParameterDescriptor
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrCallWithShallowCopy
import org.jetbrains.kotlin.ir.expressions.IrStatementOrigin
import org.jetbrains.kotlin.ir.visitors.IrElementVisitor
import org.jetbrains.kotlin.types.KotlinType

class IrCallImpl(
        startOffset: Int,
        endOffset: Int,
        type: KotlinType,
        override val descriptor: CallableDescriptor,
        typeArguments: Map<TypeParameterDescriptor, KotlinType>?,
        override val origin: IrStatementOrigin? = null,
        override val superQualifier: ClassDescriptor? = null
) : IrCallWithIndexedArgumentsBase(startOffset, endOffset, type, descriptor.valueParameters.size, typeArguments), IrCallWithShallowCopy {
    constructor(
            startOffset: Int, endOffset: Int, descriptor: CallableDescriptor,
            typeArguments: Map<TypeParameterDescriptor, KotlinType>? = null,
            origin: IrStatementOrigin? = null,
            superQualifier: ClassDescriptor? = null
    ) : this(startOffset, endOffset, descriptor.returnType!!, descriptor, typeArguments, origin, superQualifier)

    override fun <R, D> accept(visitor: IrElementVisitor<R, D>, data: D): R =
            visitor.visitCall(this, data)

    override fun shallowCopy(newOrigin: IrStatementOrigin?, newCallee: CallableDescriptor, newSuperQualifier: ClassDescriptor?): IrCall =
            IrCallImpl(startOffset, endOffset, type, newCallee, typeArguments, newOrigin, newSuperQualifier)
}