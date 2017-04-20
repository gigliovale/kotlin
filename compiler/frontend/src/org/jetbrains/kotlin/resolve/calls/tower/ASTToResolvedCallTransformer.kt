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

package org.jetbrains.kotlin.resolve.calls.tower

import org.jetbrains.kotlin.config.LanguageVersionSettings
import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.diagnostics.Errors
import org.jetbrains.kotlin.psi.Call
import org.jetbrains.kotlin.psi.KtPsiUtil
import org.jetbrains.kotlin.psi.ValueArgument
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.BindingTrace
import org.jetbrains.kotlin.resolve.calls.*
import org.jetbrains.kotlin.resolve.calls.callResolverUtil.getEffectiveExpectedType
import org.jetbrains.kotlin.resolve.calls.callUtil.isFakeElement
import org.jetbrains.kotlin.resolve.calls.checkers.CallChecker
import org.jetbrains.kotlin.resolve.calls.checkers.CallCheckerContext
import org.jetbrains.kotlin.resolve.calls.components.isVararg
import org.jetbrains.kotlin.resolve.calls.context.BasicCallResolutionContext
import org.jetbrains.kotlin.resolve.calls.context.CallPosition
import org.jetbrains.kotlin.resolve.calls.model.*
import org.jetbrains.kotlin.resolve.calls.results.ResolutionStatus
import org.jetbrains.kotlin.resolve.calls.smartcasts.DataFlowInfo
import org.jetbrains.kotlin.resolve.calls.tasks.ExplicitReceiverKind
import org.jetbrains.kotlin.resolve.constants.evaluate.ConstantExpressionEvaluator
import org.jetbrains.kotlin.resolve.scopes.receivers.ReceiverValue
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.TypeUtils
import org.jetbrains.kotlin.types.expressions.DataFlowAnalyzer
import java.util.*
import kotlin.collections.HashMap


class ASTToResolvedCallTransformer(
        private val callCheckers: Iterable<CallChecker>,
        private val languageFeatureSettings: LanguageVersionSettings,
        private val dataFlowAnalyzer: DataFlowAnalyzer,
        private val argumentTypeResolver: ArgumentTypeResolver,
        private val constantExpressionEvaluator: ConstantExpressionEvaluator
) {

    fun <D : CallableDescriptor> transformAndReport(
            baseResolvedCall: BaseResolvedCall,
            context: BasicCallResolutionContext,
            trace: BindingTrace? // if trace is not null then all information will be reported to this trace
    ): ResolvedCall<D> {
        if (baseResolvedCall is BaseResolvedCall.CompletedResolvedCall) {
            val allResolvedCalls = baseResolvedCall.allInnerCalls.mapTo(ArrayList<ResolvedCall<*>>()) { transformAndReportCompletedCall<CallableDescriptor>(it, context, trace) }
            val result = transformAndReportCompletedCall<D>(baseResolvedCall.completedCall, context, trace)
            allResolvedCalls.add(result)

            val callCheckerContext = CallCheckerContext(context, languageFeatureSettings)
            for (resolvedCall in allResolvedCalls) {
                runCallCheckers(resolvedCall, callCheckerContext)
            }

            return result
        }

        val onlyResolvedCall = (baseResolvedCall as BaseResolvedCall.OnlyResolvedCall)
        trace?.record(BindingContext.ONLY_RESOLVED_CALL, onlyResolvedCall.candidate.astCall.psiAstCall.psiCall, onlyResolvedCall)

        return createStubResolvedCallAndWriteItToTrace(onlyResolvedCall.candidate, trace)
    }

    fun <D : CallableDescriptor> createStubResolvedCallAndWriteItToTrace(candidate: NewResolutionCandidate, trace: BindingTrace?): ResolvedCall<D> {
        val result = when (candidate) {
            is VariableAsFunctionResolutionCandidate -> {
                val variableStub = StubOnlyResolvedCall<VariableDescriptor>(candidate.resolvedVariable)
                val invokeStub = StubOnlyResolvedCall<FunctionDescriptor>(candidate.invokeCandidate)
                StubOnlyVariableAsFunctionCall(variableStub, invokeStub) as ResolvedCall<D>
            }
            is SimpleResolutionCandidate -> {
                StubOnlyResolvedCall<D>(candidate)
            }
        }
        if (trace != null) {
            val tracing = candidate.astCall.psiAstCall.tracingStrategy

            tracing.bindReference(trace, result)
            tracing.bindResolvedCall(trace, result)
        }
        return result
    }


    private fun <D : CallableDescriptor> transformAndReportCompletedCall(
            completedCall: CompletedCall,
            context: BasicCallResolutionContext,
            trace: BindingTrace?
    ): ResolvedCall<D> {
        fun <C> C.runIfTraceNotNull(action: (BasicCallResolutionContext, BindingTrace, C) -> Unit): C {
            if (trace != null) action(context, trace, this)
            return this
        }

        val resolvedCall = when (completedCall) {
            is CompletedCall.Simple -> {
                NewResolvedCallImpl<D>(completedCall).runIfTraceNotNull(this::bindResolvedCall).runIfTraceNotNull(this::runArgumentsChecks)
            }
            is CompletedCall.VariableAsFunction -> {
                val resolvedCall = NewVariableAsFunctionResolvedCallImpl(
                        completedCall,
                        NewResolvedCallImpl(completedCall.variableCall),
                        NewResolvedCallImpl<FunctionDescriptor>(completedCall.invokeCall).runIfTraceNotNull(this::runArgumentsChecks)
                ).runIfTraceNotNull(this::bindResolvedCall)

                @Suppress("UNCHECKED_CAST")
                (resolvedCall as ResolvedCall<D>)
            }
        }

        return resolvedCall
    }

    private fun runCallCheckers(resolvedCall: ResolvedCall<*>, callCheckerContext: CallCheckerContext) {
        val calleeExpression = if (resolvedCall is VariableAsFunctionResolvedCall)
            resolvedCall.variableCall.call.calleeExpression
        else
            resolvedCall.call.calleeExpression
        val reportOn =
                if (calleeExpression != null && !calleeExpression.isFakeElement) calleeExpression
                else resolvedCall.call.callElement

        for (callChecker in callCheckers) {
            callChecker.check(resolvedCall, reportOn, callCheckerContext)

            if (resolvedCall is VariableAsFunctionResolvedCall) {
                callChecker.check(resolvedCall.variableCall, reportOn, callCheckerContext)
            }
        }
    }


    // todo very beginning code
    private fun runArgumentsChecks(
            context: BasicCallResolutionContext,
            trace: BindingTrace,
            resolvedCall: NewResolvedCallImpl<*>
    ) {

        for (valueArgument in resolvedCall.call.valueArguments) {
            val argumentMapping = resolvedCall.getArgumentMapping(valueArgument!!)
            val (expectedType, callPosition) = when (argumentMapping) {
                is ArgumentMatch -> Pair(
                        getEffectiveExpectedType(argumentMapping.valueParameter, valueArgument),
                        CallPosition.ValueArgumentPosition(resolvedCall, argumentMapping.valueParameter, valueArgument))
                else -> Pair(TypeUtils.NO_EXPECTED_TYPE, CallPosition.Unknown)
            }
            val newContext =
                    context.replaceDataFlowInfo(resolvedCall.dataFlowInfoForArguments.getInfo(valueArgument))
                            .replaceExpectedType(expectedType)
                            .replaceCallPosition(callPosition)
                            .replaceBindingTrace(trace)

            // todo
//            if (valueArgument.isExternal()) continue

            val deparenthesized = valueArgument.getArgumentExpression()?.let {
                KtPsiUtil.getLastElementDeparenthesized(it, context.statementFilter)
            } ?: continue

            var recordedType = context.trace.getType(deparenthesized)

            // For the cases like 'foo(1)' the type of '1' depends on expected type (it can be Int, Byte, etc.),
            // so while the expected type is not known, it's IntegerValueType(1), and should be updated when the expected type is known.
            if (recordedType != null && !recordedType.constructor.isDenotable) {
                recordedType = argumentTypeResolver.updateResultArgumentTypeIfNotDenotable(newContext, deparenthesized) ?: recordedType
            }

//            dataFlowAnalyzer.checkType(recordedType, deparenthesized, newContext)
        }

    }

    private fun bindResolvedCall(context: BasicCallResolutionContext, trace: BindingTrace, simpleResolvedCall: NewResolvedCallImpl<*>) {
        reportCallDiagnostic(context, trace, simpleResolvedCall.completedCall)
        val tracing = simpleResolvedCall.completedCall.astCall.psiAstCall.tracingStrategy

        tracing.bindReference(trace, simpleResolvedCall)
        tracing.bindResolvedCall(trace, simpleResolvedCall)
    }

    private fun bindResolvedCall(context: BasicCallResolutionContext, trace: BindingTrace, variableAsFunction: NewVariableAsFunctionResolvedCallImpl) {
        reportCallDiagnostic(context, trace, variableAsFunction.variableCall.completedCall)
        reportCallDiagnostic(context, trace, variableAsFunction.functionCall.completedCall)

        val outerTracingStrategy = variableAsFunction.completedCall.astCall.psiAstCall.tracingStrategy
        outerTracingStrategy.bindReference(trace, variableAsFunction.variableCall)
        outerTracingStrategy.bindResolvedCall(trace, variableAsFunction)
        variableAsFunction.functionCall.astCall.psiAstCall.tracingStrategy.bindReference(trace, variableAsFunction.functionCall)
    }

    private fun reportCallDiagnostic(
            context: BasicCallResolutionContext,
            trace: BindingTrace,
            completedCall: CompletedCall.Simple
    ) {
        var reported = false
        val reportTrackedTrace = object : BindingTrace by trace {
            override fun report(diagnostic: Diagnostic) {
                trace.report(diagnostic)
                reported = true
            }
        }

        val diagnosticReporter = DiagnosticReporterByTrackingStrategy(constantExpressionEvaluator, context, reportTrackedTrace,
                                                                      completedCall.astCall.psiAstCall)
        for (diagnostic in completedCall.resolutionStatus.diagnostics) {
            reported = false
            diagnostic.report(diagnosticReporter)
            if (!reported && REPORT_MISSING_NEW_INFERENCE_DIAGNOSTIC) {
                if (diagnostic.candidateApplicability.isSuccess) {
                    trace.report(Errors.NEW_INFERENCE_DIAGNOSTIC.on(diagnosticReporter.psiAstCall.psiCall.callElement, "Missing diagnostic: $diagnostic"))
                }
                else {
                    trace.report(Errors.NEW_INFERENCE_ERROR.on(diagnosticReporter.psiAstCall.psiCall.callElement, "Missing diagnostic: $diagnostic"))
                }
            }
        }
    }
}

sealed class NewAbstractResolvedCall<D : CallableDescriptor>(): ResolvedCall<D> {
    abstract val argumentMappingByOriginal: Map<ValueParameterDescriptor, ResolvedCallArgument>
    abstract val astCall: ASTCall

    private var argumentToParameterMap: Map<ValueArgument, ArgumentMatchImpl>? = null
    private val _valueArguments: Map<ValueParameterDescriptor, ResolvedValueArgument> by lazy { createValueArguments() }

    override fun getCall(): Call = astCall.psiAstCall.psiCall

    override fun getValueArguments(): Map<ValueParameterDescriptor, ResolvedValueArgument> = _valueArguments

    override fun getValueArgumentsByIndex(): List<ResolvedValueArgument>? {
        val arguments = ArrayList<ResolvedValueArgument?>(candidateDescriptor.valueParameters.size)
        for (i in 0..candidateDescriptor.valueParameters.size - 1) {
            arguments.add(null)
        }

        for ((parameterDescriptor, value) in valueArguments) {
            val oldValue = arguments.set(parameterDescriptor.index, value)
            if (oldValue != null) {
                return null
            }
        }

        if (arguments.any { it == null }) return null

        @Suppress("UNCHECKED_CAST")
        return arguments as List<ResolvedValueArgument>
    }

    override fun getArgumentMapping(valueArgument: ValueArgument): ArgumentMapping {
        if (argumentToParameterMap == null) {
            argumentToParameterMap = argumentToParameterMap(resultingDescriptor, valueArguments)
        }
        val argumentMatch = argumentToParameterMap!![valueArgument] ?: return ArgumentUnmapped
        return argumentMatch
    }

    override fun getDataFlowInfoForArguments() = object : DataFlowInfoForArguments {
        override fun getResultInfo() = astCall.psiAstCall.resultDataFlowInfo
        override fun getInfo(valueArgument: ValueArgument): DataFlowInfo {
            val externalPsiCallArgument = astCall.externalArgument?.psiCallArgument
            if (externalPsiCallArgument?.valueArgument == valueArgument) {
                return externalPsiCallArgument.dataFlowInfoAfterThisArgument
            }
            astCall.argumentsInParenthesis.find { it.psiCallArgument.valueArgument == valueArgument }?.let {
                return it.psiCallArgument.dataFlowInfoAfterThisArgument
            }

            // valueArgument is not found
            // may be we should return initial DataFlowInfo but I think that it isn't important
            return astCall.psiAstCall.resultDataFlowInfo
        }
    }

    private fun argumentToParameterMap(
            resultingDescriptor: CallableDescriptor,
            valueArguments: Map<ValueParameterDescriptor, ResolvedValueArgument>
    ): Map<ValueArgument, ArgumentMatchImpl> =
            HashMap<ValueArgument, ArgumentMatchImpl>().also { result ->
                for (parameter in resultingDescriptor.valueParameters) {
                    val resolvedArgument = valueArguments[parameter] ?: continue
                    for (arguments in resolvedArgument.arguments) {
                        result[arguments] = ArgumentMatchImpl(parameter).apply { recordMatchStatus(ArgumentMatchStatus.SUCCESS) }
                    }
                }
            }

    private fun createValueArguments(): Map<ValueParameterDescriptor, ResolvedValueArgument> =
            HashMap<ValueParameterDescriptor, ResolvedValueArgument>().also { result ->
                for (parameter in resultingDescriptor.valueParameters) {
                    val resolvedCallArgument = argumentMappingByOriginal[parameter.original] ?: continue
                    val valueArgument = when (resolvedCallArgument) {
                        ResolvedCallArgument.DefaultArgument ->
                            DefaultValueArgument.DEFAULT
                        is ResolvedCallArgument.SimpleArgument -> {
                            val valueArgument = resolvedCallArgument.callArgument.psiCallArgument.valueArgument
                            if (parameter.isVararg)
                                VarargValueArgument().apply { addArgument(valueArgument) }
                            else
                                ExpressionValueArgument(valueArgument)
                        }
                        is ResolvedCallArgument.VarargArgument ->
                            VarargValueArgument().apply {
                                resolvedCallArgument.arguments.map { it.psiCallArgument.valueArgument }.forEach { addArgument(it) }
                            }
                    }
                    result[parameter] = valueArgument
                }
            }
}

class NewResolvedCallImpl<D : CallableDescriptor>(
        val completedCall: CompletedCall.Simple
): NewAbstractResolvedCall<D>() {
    override val astCall: ASTCall get() = completedCall.astCall

    override fun getStatus(): ResolutionStatus = completedCall.resolutionStatus.resultingApplicability.toResolutionStatus()

    override val argumentMappingByOriginal: Map<ValueParameterDescriptor, ResolvedCallArgument>
        get() = completedCall.argumentMappingByOriginal

    override fun getCandidateDescriptor(): D = completedCall.candidateDescriptor as D
    override fun getResultingDescriptor(): D = completedCall.resultingDescriptor as D
    override fun getExtensionReceiver(): ReceiverValue? = completedCall.extensionReceiver?.receiverValue
    override fun getDispatchReceiver(): ReceiverValue? = completedCall.dispatchReceiver?.receiverValue
    override fun getExplicitReceiverKind(): ExplicitReceiverKind = completedCall.explicitReceiverKind

    override fun getTypeArguments(): Map<TypeParameterDescriptor, KotlinType> {
        val typeParameters = candidateDescriptor.typeParameters.takeIf { it.isNotEmpty() } ?: return emptyMap()
        return typeParameters.zip(completedCall.typeArguments).toMap()
    }

    override fun getSmartCastDispatchReceiverType(): KotlinType? = null // todo
}

class NewVariableAsFunctionResolvedCallImpl(
        val completedCall: CompletedCall.VariableAsFunction,
        override val variableCall: NewResolvedCallImpl<VariableDescriptor>,
        override val functionCall: NewResolvedCallImpl<FunctionDescriptor>
): VariableAsFunctionResolvedCall, ResolvedCall<FunctionDescriptor> by functionCall

class StubOnlyResolvedCall<D : CallableDescriptor>(val candidate: SimpleResolutionCandidate): NewAbstractResolvedCall<D>() {
    override fun getStatus() = ResolutionStatus.UNKNOWN_STATUS

    override fun getCandidateDescriptor(): D = candidate.candidateDescriptor as D
    override fun getResultingDescriptor(): D = candidateDescriptor
    override fun getExtensionReceiver() = candidate.extensionReceiver?.receiver?.receiverValue
    override fun getDispatchReceiver() = candidate.dispatchReceiverArgument?.receiver?.receiverValue
    override fun getExplicitReceiverKind() = candidate.explicitReceiverKind

    override fun getTypeArguments(): Map<TypeParameterDescriptor, KotlinType> = emptyMap()

    override fun getSmartCastDispatchReceiverType(): KotlinType? = null

    override val argumentMappingByOriginal: Map<ValueParameterDescriptor, ResolvedCallArgument>
        get() = candidate.argumentMappingByOriginal
    override val astCall: ASTCall get() = candidate.astCall
}

class StubOnlyVariableAsFunctionCall(
        override val variableCall: StubOnlyResolvedCall<VariableDescriptor>,
        override val functionCall: StubOnlyResolvedCall<FunctionDescriptor>
) : VariableAsFunctionResolvedCall, ResolvedCall<FunctionDescriptor> by functionCall