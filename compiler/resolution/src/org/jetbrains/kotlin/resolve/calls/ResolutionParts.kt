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

import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.resolve.calls.components.TypeArgumentsToParametersMapper.TypeArgumentsMapping.NoExplicitArguments
import org.jetbrains.kotlin.resolve.calls.inference.model.DeclaredUpperBoundConstraintPosition
import org.jetbrains.kotlin.resolve.calls.inference.model.ExplicitTypeParameterConstraintPosition
import org.jetbrains.kotlin.resolve.calls.inference.model.TypeVariableFromCallableDescriptor
import org.jetbrains.kotlin.resolve.calls.inference.substitute
import org.jetbrains.kotlin.resolve.calls.model.*
import org.jetbrains.kotlin.resolve.calls.smartcasts.getReceiverValueWithSmartCast
import org.jetbrains.kotlin.resolve.calls.tasks.ExplicitReceiverKind.*
import org.jetbrains.kotlin.resolve.calls.tower.ResolutionCandidateApplicability
import org.jetbrains.kotlin.resolve.calls.tower.ResolutionCandidateApplicability.*
import org.jetbrains.kotlin.resolve.calls.tower.VisibilityError
import org.jetbrains.kotlin.types.IndexedParametersSubstitution
import org.jetbrains.kotlin.types.TypeSubstitutor
import org.jetbrains.kotlin.types.UnwrappedType
import org.jetbrains.kotlin.types.Variance
import org.jetbrains.kotlin.types.typeUtil.asTypeProjection

internal object CheckInstantiationOfAbstractClass : ResolutionPart {
    override fun SimpleResolutionCandidate.process(): List<CallDiagnostic> {
        if (candidateDescriptor is ConstructorDescriptor && !callContext.astCall.isSupertypeConstructorCall) {
            if (candidateDescriptor.constructedClass.modality == Modality.ABSTRACT) {
                return listOf(InstantiationOfAbstractClass)
            }
        }

        return emptyList()
    }
}

internal object CheckVisibility : ResolutionPart {
    override fun SimpleResolutionCandidate.process(): List<CallDiagnostic> {
        val receiverValue = dispatchReceiverArgument?.receiver?.receiverValue
        val invisibleMember = Visibilities.findInvisibleMember(receiverValue, candidateDescriptor, containingDescriptor) ?: return emptyList()

        if (dispatchReceiverArgument is ExpressionArgument) {
            val smartCastReceiver = getReceiverValueWithSmartCast(receiverValue, dispatchReceiverArgument.stableType)
            if (Visibilities.findInvisibleMember(smartCastReceiver, candidateDescriptor, containingDescriptor) == null) {
                return listOf(SmartCastDiagnostic(dispatchReceiverArgument, dispatchReceiverArgument.stableType))
            }
        }

        return listOf(VisibilityError(invisibleMember))
    }
}

internal object MapTypeArguments : ResolutionPart {
    override fun SimpleResolutionCandidate.process(): List<CallDiagnostic> {
        typeArgumentMappingByOriginal = callContext.c.typeArgumentsToParametersMapper.mapTypeArguments(astCall, candidateDescriptor.original)
        return typeArgumentMappingByOriginal.diagnostics
    }
}

internal object NoTypeArguments : ResolutionPart {
    override fun SimpleResolutionCandidate.process(): List<CallDiagnostic> {
        assert(astCall.typeArguments.isEmpty()) {
            "Variable call cannot has explicit type arguments: ${astCall.typeArguments}. Call: $astCall"
        }
        typeArgumentMappingByOriginal = NoExplicitArguments
        return typeArgumentMappingByOriginal.diagnostics
    }
}

internal object MapArguments : ResolutionPart {
    override fun SimpleResolutionCandidate.process(): List<CallDiagnostic> {
        val mapping = callContext.c.argumentsToParametersMapper.mapArguments(astCall, candidateDescriptor.original)
        argumentMappingByOriginal = mapping.parameterToCallArgumentMap
        return mapping.diagnostics
    }
}

internal object NoArguments : ResolutionPart {
    override fun SimpleResolutionCandidate.process(): List<CallDiagnostic> {
        assert(astCall.argumentsInParenthesis.isEmpty()) {
            "Variable call cannot has arguments: ${astCall.argumentsInParenthesis}. Call: $astCall"
        }
        assert(astCall.externalArgument == null) {
            "Variable call cannot has external argument: ${astCall.externalArgument}. Call: $astCall"
        }
        argumentMappingByOriginal = emptyMap()
        return emptyList()
    }
}

internal object CreteDescriptorWithFreshTypeVariables : ResolutionPart {
    override fun SimpleResolutionCandidate.process(): List<CallDiagnostic> {
        if (candidateDescriptor.typeParameters.isEmpty()) {
            descriptorWithFreshTypes = candidateDescriptor
            return emptyList()
        }
        val typeParameters = candidateDescriptor.typeParameters

        val freshTypeVariables = typeParameters.map { TypeVariableFromCallableDescriptor(astCall, it) }
        val toFreshVariables = IndexedParametersSubstitution(typeParameters,
                                                             freshTypeVariables.map { it.defaultType.asTypeProjection() }).buildSubstitutor()

        for (freshVariable in freshTypeVariables) {
            csBuilder.registerVariable(freshVariable)
        }

        for (index in typeParameters.indices) {
            val typeParameter = typeParameters[index]
            val freshVariable = freshTypeVariables[index]
            val position = DeclaredUpperBoundConstraintPosition(typeParameter)

            for (upperBound in typeParameter.upperBounds) {
                csBuilder.addSubtypeConstraint(freshVariable.defaultType, upperBound.unwrap().substitute(toFreshVariables), position)
            }
        }

        // bad function -- error on declaration side
        if (csBuilder.hasContradiction) {
            descriptorWithFreshTypes = candidateDescriptor
            return emptyList()
        }

        // optimization
        if (typeArgumentMappingByOriginal == NoExplicitArguments) {
            descriptorWithFreshTypes = candidateDescriptor.safeSubstitute(toFreshVariables)
            csBuilder.simplify().let { assert(it.isEmpty) { "Substitutor should be empty: $it, call: $astCall" } }
            return emptyList()
        }

        for (index in typeParameters.indices) {
            val typeParameter = typeParameters[index]
            val typeArgument = typeArgumentMappingByOriginal.getTypeArgument(typeParameter)

            if (typeArgument is SimpleTypeArgument) {
                val freshVariable = freshTypeVariables[index]
                csBuilder.addEqualityConstraint(freshVariable.defaultType, typeArgument.type, ExplicitTypeParameterConstraintPosition(typeArgument))
            }
            else {
                assert(typeArgument == TypeArgumentPlaceholder) {
                    "Unexpected typeArgument: $typeArgument, ${typeArgument.javaClass.canonicalName}"
                }
            }
        }

        /**
         * Note: here we can fix also placeholders arguments.
         * Example:
         *  fun <X : Array<Y>, Y> foo()
         *
         *  foo<Array<String>, *>()
         */
        val toFixedTypeParameters = csBuilder.simplify()
        // todo optimize -- composite substitutions before run safeSubstitute
        descriptorWithFreshTypes = candidateDescriptor.substitute(toFreshVariables)!!.substitute(toFixedTypeParameters)!!

        return emptyList()
    }
}

internal object CheckExplicitReceiverKindConsistency : ResolutionPart {
    private fun SimpleResolutionCandidate.hasError(): Nothing =
            error("Inconsistent call: $astCall. \n" +
                  "Candidate: $candidateDescriptor, explicitReceiverKind: $explicitReceiverKind.\n" +
                  "Explicit receiver: ${astCall.explicitReceiver}, dispatchReceiverForInvokeExtension: ${astCall.dispatchReceiverForInvokeExtension}")

    override fun SimpleResolutionCandidate.process(): List<CallDiagnostic> {
        when (explicitReceiverKind) {
            NO_EXPLICIT_RECEIVER -> if (astCall.explicitReceiver is SimpleCallArgument || astCall.dispatchReceiverForInvokeExtension != null) hasError()
            DISPATCH_RECEIVER, EXTENSION_RECEIVER -> if (astCall.explicitReceiver == null || astCall.dispatchReceiverForInvokeExtension != null) hasError()
            BOTH_RECEIVERS -> if (astCall.explicitReceiver == null || astCall.dispatchReceiverForInvokeExtension == null) hasError()
        }
        return emptyList()
    }
}

internal object CheckReceivers : ResolutionPart {
    private fun SimpleResolutionCandidate.checkReceiver(
            receiverArgument: SimpleCallArgument?,
            receiverParameter: ReceiverParameterDescriptor?
    ): CallDiagnostic? {
        if ((receiverArgument == null) != (receiverParameter == null)) {
            error("Inconsistency receiver state for call $astCall and candidate descriptor: $candidateDescriptor")
        }
        if (receiverArgument == null || receiverParameter == null) return null

        val expectedType = receiverParameter.type.unwrap()

        return when (receiverArgument) {
            is ExpressionArgument -> checkExpressionArgument(csBuilder, receiverArgument, expectedType, isReceiver = true)
            is SubCallArgument -> checkSubCallArgument(csBuilder, receiverArgument, expectedType, isReceiver = true)
            else -> incorrectReceiver(receiverArgument)
        }
    }

    private fun incorrectReceiver(callReceiver: SimpleCallArgument): Nothing =
            error("Incorrect receiver type: $callReceiver. Class name: ${callReceiver.javaClass.canonicalName}")

    override fun SimpleResolutionCandidate.process() =
            listOfNotNull(checkReceiver(dispatchReceiverArgument, descriptorWithFreshTypes.dispatchReceiverParameter),
                          checkReceiver(extensionReceiver, descriptorWithFreshTypes.extensionReceiverParameter))
}


fun <D : CallableDescriptor> D.safeSubstitute(substitutor: TypeSubstitutor): D =
        @Suppress("UNCHECKED_CAST") (substitute(substitutor) as D)

fun UnwrappedType.substitute(substitutor: TypeSubstitutor): UnwrappedType = substitutor.substitute(this, Variance.INVARIANT)!!.unwrap()


object InstantiationOfAbstractClass : CallDiagnostic(RUNTIME_ERROR) {
    override fun report(reporter: DiagnosticReporter) = reporter.onCall(this)
}

class UnstableSmartCast(val expressionArgument: ExpressionArgument, val targetType: UnwrappedType) :
        CallDiagnostic(ResolutionCandidateApplicability.MAY_THROW_RUNTIME_ERROR) {
    override fun report(reporter: DiagnosticReporter) = reporter.onCallArgument(expressionArgument, this)
}

class UnsafeCallError(val receiver: SimpleCallArgument) : CallDiagnostic(ResolutionCandidateApplicability.MAY_THROW_RUNTIME_ERROR) {
    override fun report(reporter: DiagnosticReporter) = reporter.onCallReceiver(receiver, this)
}

class ExpectedLambdaParametersCountMismatch(
        val lambdaArgument: LambdaArgument,
        val expected: Int,
        val actual: Int
) : CallDiagnostic(IMPOSSIBLE_TO_GENERATE) {
    override fun report(reporter: DiagnosticReporter) = reporter.onCallArgument(lambdaArgument, this)
}

class UnexpectedReceiver(val functionExpression: FunctionExpression) : CallDiagnostic(IMPOSSIBLE_TO_GENERATE) {
    override fun report(reporter: DiagnosticReporter) = reporter.onCallArgument(functionExpression, this)
}

class MissingReceiver(val functionExpression: FunctionExpression) : CallDiagnostic(IMPOSSIBLE_TO_GENERATE) {
    override fun report(reporter: DiagnosticReporter) = reporter.onCallArgument(functionExpression, this)
}

class ErrorCallableMapping(val functionReference: ResolvedFunctionReference) : CallDiagnostic(IMPOSSIBLE_TO_GENERATE) {
    override fun report(reporter: DiagnosticReporter) = reporter.onCallArgument(functionReference.argument, this)
}