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

package org.jetbrains.kotlin.ir.declarations.impl

import org.jetbrains.kotlin.descriptors.ValueParameterDescriptor
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.expressions.IrBody
import org.jetbrains.kotlin.ir.expressions.IrExpressionBody
import org.jetbrains.kotlin.ir.visitors.IrElementTransformer
import org.jetbrains.kotlin.ir.visitors.IrElementVisitor
import java.util.*

abstract class IrFunctionBase(
        startOffset: Int,
        endOffset: Int,
        origin: IrDeclarationOrigin
) : IrDeclarationBase(startOffset, endOffset, origin), IrFunction {
    private val defaults = LinkedHashMap<ValueParameterDescriptor, IrExpressionBody>()

    final override var body: IrBody? = null

    override fun getDefault(parameter: ValueParameterDescriptor): IrExpressionBody? =
            defaults[parameter]

    override fun putDefault(parameter: ValueParameterDescriptor, expressionBody: IrExpressionBody) {
        defaults[parameter] = expressionBody
    }

    override fun <D> acceptChildren(visitor: IrElementVisitor<Unit, D>, data: D) {
        defaults.values.forEach { it.accept(visitor, data) }
        body?.accept(visitor, data)
    }

    override fun <D> transformChildren(transformer: IrElementTransformer<D>, data: D) {
        for ((valueParameter, defaultValue) in defaults) {
            putDefault(valueParameter, defaultValue.transform(transformer, data))
        }
        body = body?.transform(transformer, data)
    }
}