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
    RECEIVER_LIKE_IN_STACKVALUE
}

sealed class GeneratedArgument(val stackValue: StackValue, val type: Type, val kind: LazyArgumentKind)

class NonValueArgument(stackValue: StackValue, type: Type, kind: LazyArgumentKind):GeneratedArgument(stackValue, type, kind)

class CapturedParameter @JvmOverloads constructor(
        stackValue: StackValue, val index: Int, type: Type = stackValue.type
) : GeneratedArgument(stackValue, type, LazyArgumentKind.CAPTURED_PARAM)

class GeneratedValueArgument(
        stackValue: StackValue, type: Type = stackValue.type, val descriptor: ValueParameterDescriptor?, val declIndex: Int, val expression: KtExpression? = null
): GeneratedArgument(stackValue, type, LazyArgumentKind.VALUE_PARAMETER)

class ComplexOperationDup(stackValue: StackValue, val original: StackValue) :
        GeneratedArgument(stackValue, stackValue.type, LazyArgumentKind.COMPLEX_OPERATION_DUP)

class LazyArguments {

    val list = arrayListOf<GeneratedArgument>()

    fun addParameter(arg: GeneratedArgument) {
        list.add(arg)
    }


    fun addParameter(arg: StackValue, kind: LazyArgumentKind) {
        addParameter(arg, arg.type, kind)
    }

    fun addParameter(arg: StackValue, type: Type, kind: LazyArgumentKind) {
        list.add(NonValueArgument(arg, type, kind))
    }

    fun generateAllDirectlyTo(v: InstructionAdapter) {
        list.forEach {
            it.stackValue.put(it.type, v)
        }
    }
}
