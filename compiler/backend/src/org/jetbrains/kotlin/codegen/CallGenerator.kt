/*
 * Copyright 2010-2015 JetBrains s.r.o.
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

import org.jetbrains.kotlin.codegen.coroutines.AFTER_SUSPENSION_POINT_MARKER_NAME
import org.jetbrains.kotlin.codegen.coroutines.BEFORE_SUSPENSION_POINT_MARKER_NAME
import org.jetbrains.kotlin.codegen.coroutines.COROUTINE_MARKER_OWNER
import org.jetbrains.kotlin.codegen.coroutines.isSuspensionPointInStateMachine
import org.jetbrains.kotlin.codegen.inline.InlineCodegenUtil.addInlineMarker
import org.jetbrains.kotlin.resolve.calls.model.ResolvedCall
import org.jetbrains.org.objectweb.asm.Type
import org.jetbrains.org.objectweb.asm.commons.InstructionAdapter

abstract class CallGenerator {

    internal class DefaultCallGenerator(private val codegen: ExpressionCodegen) : CallGenerator() {

        override fun genCallInner(
                callableMethod: Callable,
                resolvedCall: ResolvedCall<*>?,
                callDefault: Boolean,
                codegen: ExpressionCodegen) {
            if (!callDefault) {
                callableMethod.genInvokeInstruction(codegen.v)
            }
            else {
                (callableMethod as CallableMethod).genInvokeDefaultInstruction(codegen.v)
            }
        }

        override fun putValueIfNeeded(
                parameterType: Type, value: StackValue) {
            value.put(parameterType, codegen.v)
        }

        override fun reorderArgumentsIfNeeded(argumentDeclIndex: List<Int>, valueParameterTypes: List<Type>) {
            val mark = codegen.myFrameMap.mark()
            val reordered = argumentDeclIndex.withIndex().dropWhile {
                it.value == it.index
            }

            reordered.reversed().map {
                val argumentAndDeclIndex = it.value
                val type = valueParameterTypes.get(argumentAndDeclIndex)
                val stackValue = StackValue.local(codegen.frameMap.enterTemp(type), type)
                stackValue.store(StackValue.onStack(type), codegen.v)
                Pair(argumentAndDeclIndex, stackValue)
            }.sortedBy {
                it.first
            }.forEach {
                it.second.put(valueParameterTypes.get(it.first), codegen.v)
            }
            mark.dropTo()
        }
    }

    open fun putValueParameters(lazyArguments: LazyArguments, v: InstructionAdapter) {
        //TODO: could contain captured one for inline case or not?
        val valueParameters = extractValueParameters(lazyArguments)

        valueParameters.forEach {
            it.stackValue.put(it.type, v)
        }
    }

    fun genCall(callableMethod: Callable, resolvedCall: ResolvedCall<*>?, lazyArguments: LazyArguments, codegen: ExpressionCodegen) {
        val isSuspensionPoint = resolvedCall?.isSuspensionPointInStateMachine(codegen.bindingContext) ?: false

        if (isSuspensionPoint) {
            //process safe calls
            // Inline markers are used to spill the stack before coroutine suspension
            addInlineMarker(codegen.v, true)
        }
        val v = codegen.v
        //safe call
        lazyArguments.list.takeWhile{ it.kind != LazyArgumentKind.VALUE_PARAMETER }.forEach {
            it.stackValue.put(it.type, v)
            if (it.kind == LazyArgumentKind.DISPATCH_RECEIVER) {
                callableMethod.afterReceiverGeneration(v)
            }
        }

        putValueParameters(lazyArguments, v)

        val valueParameters = extractValueParameters(lazyArguments)
        reorderArgumentsIfNeeded(valueParameters.map { it.declIndex }, callableMethod.valueParameterTypes)

        if (!valueParameters.isEmpty()) {
            lazyArguments.list.takeLastWhile { it.kind != LazyArgumentKind.VALUE_PARAMETER }.forEach { putValueIfNeeded(it.type, it.stackValue) }
        }

        if (isSuspensionPoint) {
            v.invokestatic(COROUTINE_MARKER_OWNER, BEFORE_SUSPENSION_POINT_MARKER_NAME, "()V", false)
        }

        if (resolvedCall != null) {
            val calleeExpression = resolvedCall.call.calleeExpression
            if (calleeExpression != null) {
                codegen.markStartLineNumber(calleeExpression)
            }
        }

        val callDefault = lazyArguments.list.any {it.kind == LazyArgumentKind.DEFAULT_MASK_PART}
        genCallInner(callableMethod, resolvedCall, callDefault, codegen)

        if (isSuspensionPoint) {
            v.invokestatic(
                    COROUTINE_MARKER_OWNER,
                    AFTER_SUSPENSION_POINT_MARKER_NAME, "()V", false)
            addInlineMarker(v, false)
        }

    }

    protected fun extractValueParameters(lazyArguments: LazyArguments): List<GeneratedValueArgument> {
        val valueParameters = lazyArguments.list.dropWhile { it.kind != LazyArgumentKind.VALUE_PARAMETER }.
                dropLastWhile { it.kind != LazyArgumentKind.VALUE_PARAMETER } as List<GeneratedValueArgument>
        return valueParameters
    }

    abstract fun genCallInner(callableMethod: Callable, resolvedCall: ResolvedCall<*>?, callDefault: Boolean, codegen: ExpressionCodegen)

    abstract fun putValueIfNeeded(
            parameterType: Type,
            value: StackValue)

    abstract fun reorderArgumentsIfNeeded(argumentDeclIndex: List<Int>, valueParameterTypes: List<Type>)
}
