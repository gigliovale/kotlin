/*
 * Copyright 2010-2017 JetBrains s.r.o.
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

/*
 * Copyright 2010-2017 JetBrains s.r.o.
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

package org.jetbrains.kotlin.codegen

import org.jetbrains.kotlin.descriptors.CallableDescriptor
import org.jetbrains.kotlin.descriptors.ValueParameterDescriptor
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.resolve.inline.InlineUtil
import org.jetbrains.org.objectweb.asm.Type
import org.jetbrains.org.objectweb.asm.commons.InstructionAdapter

abstract class StackValueBase {
    abstract fun put(type: Type, v: InstructionAdapter)

    open fun putWithArguments(type: Type, v: InstructionAdapter, arguments: LazyArguments?) {
        arguments?.generateAllDirectlyTo(v)
        put(type, v)
    }

    open fun genArgs(arguments: LazyArguments, isRead: Boolean) {

    }

    abstract fun store(value: StackValue, v: InstructionAdapter)

    open fun storeWithArguments(value: StackValue, v: InstructionAdapter, arguments: LazyArguments?) {
        arguments?.generateAllDirectlyTo(v)
        store(value, v)
    }
}

fun StackValue.put(v: InstructionAdapter) {
    this.put(type, v)
}

fun StackValue.complexReceiver(codegen: ExpressionCodegen, /*ordered by stack*/vararg isReadOperations: Boolean): Array<LazyArguments> {
    if (this is StackValue.Delegate) {
        //TODO need to support
        throwUnsupportedComplexOperation(this.variableDescriptor)
    }

    val values = isReadOperations.map { LazyArguments().apply { genArgs(this, it) } }

    /*todo add count*/
    val inlineUsageCount = hashMapOf<KtExpression, Int>()

    val newArgss = isReadOperations.map { LazyArguments() }

    //TODO add optimization with dups, now only
    values.forEach {
        it.list.filterIsInstance<GeneratedValueArgument>().forEach {
            val expression = it.expression ?: return@forEach
            val descriptor = it.descriptor ?: return@forEach
            if (codegen.isInline(descriptor.containingDeclaration) &&
                (InlineUtil.isInlineLambdaParameter(descriptor) && InlineUtil.canBeInlineArgument(expression))) {
                inlineUsageCount.put(expression, inlineUsageCount.getOrPut(expression, {0} ) + 1)
            }
        }
    }
    val v = codegen.v
    val frameMap = codegen.frameMap
    val mark = frameMap.mark()

    val expressionToLocal = hashMapOf<Any, StackValue>()

    fun GeneratedArgument.needDuplication() = this is GeneratedValueArgument || this.kind == LazyArgumentKind.DISPATCH_RECEIVER || this.kind == LazyArgumentKind.EXTENSION_RECEIVER

    //TODO proper inlining
    values[0].list.forEach {
        val isInlinedEverywhere = (it as? GeneratedValueArgument)?.expression?.let { inlineUsageCount.get(it) == isReadOperations.size } ?: false
        if (!isInlinedEverywhere) {
            it.put(v)
            if (it.needDuplication()) {
                if (it.type != Type.VOID_TYPE) {
                    val index = frameMap.enterTemp(it.type)
                    val local = StackValue.local(index, it.type)
                    local.store(StackValue.onStack(it.type), v)
                    local.put(v)
                    if (it is GeneratedValueArgument && it.expression != null) {
                        expressionToLocal.put(it.expression, local)
                    }
                    else if (it.kind == LazyArgumentKind.DISPATCH_RECEIVER || it.kind == LazyArgumentKind.EXTENSION_RECEIVER) {
                        //TODO in general case there could be different receivers for getter and setter
                        expressionToLocal.put(it.kind, local)
                    }
                }
                else {
                    //TODO in general case there could be different receivers for getter and setter
                    assert(it.kind == LazyArgumentKind.DISPATCH_RECEIVER || it.kind == LazyArgumentKind.EXTENSION_RECEIVER)
                    expressionToLocal.put(it.kind, StackValue.none())
                }
            }
            newArgss[0].addParameter(it.copyWithNewStackValue(StackValue.onStack(it.type)))
        }
        else {
            newArgss[0].addParameter(it)
        }
    }

    //TODO inline and
    values.drop(1).forEachIndexed {
        index, it ->
        val newArgs = newArgss[index + 1]
        it.list.forEach {
            val value =
                    if (it.needDuplication() && (it !is GeneratedValueArgument ||  it.expression != null)) {
                expressionToLocal.getOrElse(if (it is GeneratedValueArgument) it.expression!! else it.kind, {it.stackValue})
            }
            else it.stackValue
            value.put(v)
            newArgs.addParameter(it.copyWithNewStackValue(StackValue.onStack(it.type)))
        }
    }

    mark.dropTo()
    return newArgss.toTypedArray()
}


private fun throwUnsupportedComplexOperation(
        descriptor: CallableDescriptor
) {
    throw RuntimeException(
            "Augment assignment and increment are not supported for local delegated properties ans inline properties: " + descriptor)
}


