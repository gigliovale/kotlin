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

import org.jetbrains.jet.codegen.Callable
import org.jetbrains.org.objectweb.asm.Type
import org.jetbrains.jet.lang.resolve.java.jvmSignature.JvmMethodParameterSignature
import org.jetbrains.org.objectweb.asm.commons.InstructionAdapter
import org.jetbrains.jet.codegen.state.GenerationState
import org.jetbrains.jet.lang.descriptors.CallableDescriptor
import org.jetbrains.jet.lang.resolve.calls.model.ResolvedCall
import org.jetbrains.jet.codegen.ExtendedCallable

public open class IntrinsicCallable(val method: IntrinsicMethod, val returnType1: Type, val valueParametersTypes: List<Type>, val thisType1: Type) : ExtendedCallable {

    override fun getValueParameterTypes(): List<Type> {
        return valueParametersTypes
    }

    override fun invokeDefaultWithNotNullAssertion(v: InstructionAdapter, state: GenerationState, resolvedCall: ResolvedCall<out CallableDescriptor>) {
        throw UnsupportedOperationException()
    }

    override fun invokeWithoutAssertions(v: InstructionAdapter) {
        invokeIntrinsic(v)
    }

    override fun invokeWithNotNullAssertion(v: InstructionAdapter, state: GenerationState, resolvedCall: ResolvedCall<out CallableDescriptor>) {
        invokeIntrinsic(v)
    }

    public open fun invokeIntrinsic(v: InstructionAdapter) {

    }


    override fun getGenerateCalleeType(): Type? {
        return null
    }
    override fun getReturnType(): Type {
        return returnType1
    }

    override fun getThisType(): Type? {
        return thisType1
    }
    override fun getReceiverClass(): Type? {
        throw UnsupportedOperationException()
    }

    override fun getOwner(): Type {
        throw UnsupportedOperationException()
    }
}