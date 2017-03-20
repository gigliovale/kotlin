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

import org.jetbrains.kotlin.descriptors.ValueParameterDescriptor
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.org.objectweb.asm.Type
import org.jetbrains.org.objectweb.asm.commons.InstructionAdapter


enum class LazyArgumentKind {
    DISPATCH_RECEIVER,
    EXTENSION_RECEIVER,
    VALUE_PARAMETER,
    CAPTURED_PARAM,
    DEFAULT_MASK_PART,
    CONSTRUCTOR_MARKER,
    DEFAULT_CONSTRUCTOR_MARKER,
    CONTINUATION,
    EXPLICITLY_ADDED,
    COMPLEX_OPERATION_ORIGINAL,
    COMPLEX_OPERATION_DUP,
    RECEIVER_LIKE_IN_STACKVALUE;

}

sealed class GeneratedArgument(val stackValue: StackValue, val type: Type, val kind: LazyArgumentKind) {
    fun put(v: InstructionAdapter) {
        stackValue.put(type, v)
    }

    abstract fun copyWithNewStackValue(stackValue: StackValue): GeneratedArgument
}

class NonValueArgument(stackValue: StackValue, type: Type, kind: LazyArgumentKind):GeneratedArgument(stackValue, type, kind) {
    override fun copyWithNewStackValue(stackValue: StackValue) = NonValueArgument(stackValue, type, kind)
}

class CapturedParameter @JvmOverloads constructor(
        stackValue: StackValue, val index: Int, type: Type = stackValue.type
) : GeneratedArgument(stackValue, type, LazyArgumentKind.CAPTURED_PARAM)
{
    override fun copyWithNewStackValue(stackValue: StackValue): GeneratedArgument {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }
}

class GeneratedValueArgument @JvmOverloads constructor(
        stackValue: StackValue, type: Type = stackValue.type, val descriptor: ValueParameterDescriptor?, val declIndex: Int, val expression: KtExpression? = null
): GeneratedArgument(stackValue, type, LazyArgumentKind.VALUE_PARAMETER) {
    override fun copyWithNewStackValue(stackValue: StackValue): GeneratedArgument {
        return GeneratedValueArgument(stackValue, type, descriptor, declIndex, expression)
    }
}

class ComplexOperationDup(stackValue: StackValue, val original: StackValue) :
        GeneratedArgument(stackValue, stackValue.type, LazyArgumentKind.COMPLEX_OPERATION_DUP) {

    override fun copyWithNewStackValue(stackValue: StackValue): GeneratedArgument {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }
}

class LazyArguments @JvmOverloads constructor(val list: ArrayList<GeneratedArgument> = arrayListOf<GeneratedArgument>()) {

    fun addParameter(arg: GeneratedArgument) {
        //TODO don't add nones
        if (arg.stackValue == StackValue.none()) return
        list.add(arg)
    }


    fun addParameter(arg: StackValue, kind: LazyArgumentKind) {
        addParameter(arg, arg.type, kind)
    }

    fun addParameter(arg: StackValue, type: Type, kind: LazyArgumentKind) {
        addParameter(NonValueArgument(arg, type, kind))
    }

    fun generateAllDirectlyTo(v: InstructionAdapter) {
        generateDirectlyTo(list, v)
    }

    fun generateAllDirectlyTo(v: InstructionAdapter, type: Type?) {
        generateDirectlyTo(list, v, type)
    }

    fun generateDirectlyTo(args: List<GeneratedArgument>, v: InstructionAdapter, castTo: Type?) {
        val first = args.getOrNull(0)
        val second = args.getOrNull(1)
        var lastType: Type? = null
        if (LazyArgumentKind.EXTENSION_RECEIVER == first?.kind && LazyArgumentKind.DISPATCH_RECEIVER == second?.kind) {
            //safe call
            first.stackValue.put(v)
            second.stackValue.put(v)
            AsmUtil.swap(v, second.type, first.type)
            lastType = first.type
            args.drop(2).forEach {
                it.put(v)
                lastType = it.type
            }
        }
        else {
            args.forEach {
                it.put(v)
                lastType = it.type
            }
        }

        if (castTo != null) {
            StackValue.coerce(lastType!!, castTo, v)
        }
        //TODO: reordering
    }

    fun generateDirectlyTo(args: List<GeneratedArgument>, v: InstructionAdapter) {
        generateDirectlyTo(args, v, null)
    }

    val isEmpty = list.isEmpty()
}
