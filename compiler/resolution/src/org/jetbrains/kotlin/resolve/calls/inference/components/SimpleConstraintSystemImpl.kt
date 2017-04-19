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

package org.jetbrains.kotlin.resolve.calls.inference.components

import org.jetbrains.kotlin.descriptors.TypeParameterDescriptor
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.resolve.calls.ASTCallKind
import org.jetbrains.kotlin.resolve.calls.CallContextComponents
import org.jetbrains.kotlin.resolve.calls.inference.ConstraintSystemBuilder
import org.jetbrains.kotlin.resolve.calls.inference.model.NewConstraintSystemImpl
import org.jetbrains.kotlin.resolve.calls.inference.model.SimpleConstraintSystemConstraintPosition
import org.jetbrains.kotlin.resolve.calls.inference.model.TypeVariableFromCallableDescriptor
import org.jetbrains.kotlin.resolve.calls.inference.substitute
import org.jetbrains.kotlin.resolve.calls.model.ASTCall
import org.jetbrains.kotlin.resolve.calls.model.CallArgument
import org.jetbrains.kotlin.resolve.calls.model.ReceiverCallArgument
import org.jetbrains.kotlin.resolve.calls.model.TypeArgument
import org.jetbrains.kotlin.resolve.calls.results.SimpleConstraintSystem
import org.jetbrains.kotlin.types.TypeConstructorSubstitution
import org.jetbrains.kotlin.types.TypeSubstitutor
import org.jetbrains.kotlin.types.UnwrappedType
import org.jetbrains.kotlin.types.typeUtil.asTypeProjection
import java.lang.UnsupportedOperationException


class SimpleConstraintSystemImpl(callComponents: CallContextComponents) : SimpleConstraintSystem {
    val csBuilder: ConstraintSystemBuilder = NewConstraintSystemImpl(callComponents).getBuilder()

    override fun registerTypeVariables(typeParameters: Collection<TypeParameterDescriptor>): TypeSubstitutor {
        val substitutionMap = typeParameters.associate {
            val variable = TypeVariableFromCallableDescriptor(ThrowableASTCall, it)
            csBuilder.registerVariable(variable)

            it.defaultType.constructor to variable.defaultType.asTypeProjection()
        }
        val substitutor = TypeConstructorSubstitution.createByConstructorsMap(substitutionMap).buildSubstitutor()
        for (typeParameter in typeParameters) {
            for (upperBound in typeParameter.upperBounds) {
                addSubtypeConstraint(substitutor.substitute(typeParameter.defaultType), substitutor.substitute(upperBound.unwrap()))
            }
        }
        return substitutor
    }

    override fun addSubtypeConstraint(subType: UnwrappedType, superType: UnwrappedType) {
        csBuilder.addSubtypeConstraint(subType, superType, SimpleConstraintSystemConstraintPosition)
    }

    override fun hasContradiction() = csBuilder.hasContradiction

    override val captureFromArgument get() = true

    private object ThrowableASTCall : ASTCall {
        override val callKind: ASTCallKind get() = throw UnsupportedOperationException()
        override val explicitReceiver: ReceiverCallArgument? get() = throw UnsupportedOperationException()
        override val name: Name get() = throw UnsupportedOperationException()
        override val typeArguments: List<TypeArgument> get() = throw UnsupportedOperationException()
        override val argumentsInParenthesis: List<CallArgument> get() = throw UnsupportedOperationException()
        override val externalArgument: CallArgument? get() = throw UnsupportedOperationException()
        override val isInfixCall: Boolean get() = throw UnsupportedOperationException()
        override val isOperatorCall: Boolean get() = throw UnsupportedOperationException()
        override val isSuperOrDelegatingConstructorCall: Boolean get() = throw UnsupportedOperationException()
    }
}