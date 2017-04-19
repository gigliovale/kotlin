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

package org.jetbrains.kotlin.resolve.calls

import org.jetbrains.kotlin.descriptors.FunctionDescriptor
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.resolve.calls.components.ArgumentsToParametersMapper
import org.jetbrains.kotlin.resolve.calls.components.CallableReferenceResolver
import org.jetbrains.kotlin.resolve.calls.components.TypeArgumentsToParametersMapper
import org.jetbrains.kotlin.resolve.calls.inference.components.ConstraintInjector
import org.jetbrains.kotlin.resolve.calls.inference.components.ResultTypeResolver
import org.jetbrains.kotlin.resolve.calls.model.*
import org.jetbrains.kotlin.resolve.calls.tasks.ExplicitReceiverKind
import org.jetbrains.kotlin.resolve.calls.tower.CandidateFactory
import org.jetbrains.kotlin.resolve.calls.tower.CandidateWithBoundDispatchReceiver
import org.jetbrains.kotlin.resolve.calls.tower.ImplicitScopeTower
import org.jetbrains.kotlin.resolve.scopes.receivers.ReceiverValueWithSmartCastInfo
import org.jetbrains.kotlin.types.ErrorUtils
import org.jetbrains.kotlin.types.TypeSubstitutor


val GIVEN_CANDIDATES_NAME = Name.special("<given candidates>")

class CallContextComponents(
        val argumentsToParametersMapper: ArgumentsToParametersMapper,
        val typeArgumentsToParametersMapper: TypeArgumentsToParametersMapper,
        val resultTypeResolver: ResultTypeResolver,
        val callableReferenceResolver: CallableReferenceResolver,
        val constraintInjector: ConstraintInjector
)

class CallContext(
        val c: CallContextComponents,
        val scopeTower: ImplicitScopeTower,
        val astCall: ASTCall,
        val lambdaAnalyzer: LambdaAnalyzer
)

class SimpleCandidateFactory(val callContext: CallContext): CandidateFactory<SimpleResolutionCandidate> {

    // todo: try something else, because current method is ugly and unstable
    private fun createReceiverArgument(
            explicitReceiver: ReceiverCallArgument?,
            fromResolution: ReceiverValueWithSmartCastInfo?
    ): SimpleCallArgument? =
            explicitReceiver as? SimpleCallArgument ?: // qualifier receiver cannot be safe
            fromResolution?.let { ReceiverExpressionArgument(it, isSafeCall = false) } // todo smartcast implicit this

    override fun createCandidate(
            towerCandidate: CandidateWithBoundDispatchReceiver,
            explicitReceiverKind: ExplicitReceiverKind,
            extensionReceiver: ReceiverValueWithSmartCastInfo?
    ): SimpleResolutionCandidate {
        val dispatchArgumentReceiver = createReceiverArgument(callContext.astCall.getExplicitDispatchReceiver(explicitReceiverKind),
                                                              towerCandidate.dispatchReceiver)
        val extensionArgumentReceiver = createReceiverArgument(callContext.astCall.getExplicitExtensionReceiver(explicitReceiverKind), extensionReceiver)

        if (ErrorUtils.isError(towerCandidate.descriptor)) {
            return ErrorResolutionCandidate(callContext, explicitReceiverKind, dispatchArgumentReceiver, extensionArgumentReceiver, towerCandidate.descriptor)
        }

        return SimpleResolutionCandidate(callContext, explicitReceiverKind, dispatchArgumentReceiver, extensionArgumentReceiver,
                                         towerCandidate.descriptor, towerCandidate.diagnostics)
    }
}



enum class ASTCallKind(vararg resolutionPart: ResolutionPart) {
    VARIABLE(
            CheckVisibility,
            CheckInfixResolutionPart,
            CheckOperatorResolutionPart,
            NoTypeArguments,
            NoArguments,
            CreateDescriptorWithFreshTypeVariables,
            CheckExplicitReceiverKindConsistency,
            CheckReceivers
    ),
    FUNCTION(
            CheckInstantiationOfAbstractClass,
            CheckVisibility,
            MapTypeArguments,
            MapArguments,
            CreateDescriptorWithFreshTypeVariables,
            CheckExplicitReceiverKindConsistency,
            CheckReceivers,
            CheckArguments
    ),
    UNSUPPORTED();

    val resolutionSequence = resolutionPart.asList()
}

class GivenCandidate(
        val descriptor: FunctionDescriptor,
        val dispatchReceiver: ReceiverValueWithSmartCastInfo?,
        val knownTypeParametersResultingSubstitutor: TypeSubstitutor?
)

