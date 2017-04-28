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

package org.jetbrains.kotlin.ir.declarations

import org.jetbrains.kotlin.descriptors.FunctionDescriptor
import org.jetbrains.kotlin.descriptors.ValueParameterDescriptor
import org.jetbrains.kotlin.ir.expressions.IrBody
import org.jetbrains.kotlin.ir.expressions.IrExpressionBody

interface IrFunction : IrDeclaration {
    override val descriptor: FunctionDescriptor

    val typeParameters: MutableList<IrTypeParameter>

    val valueParameters: MutableList<IrValueParameter>

    var body: IrBody?

    override val declarationKind: IrDeclarationKind
        get() = IrDeclarationKind.FUNCTION

    fun putDefault(parameter: ValueParameterDescriptor, expressionBody: IrExpressionBody)
    fun getDefault(parameter: ValueParameterDescriptor): IrExpressionBody?
}

