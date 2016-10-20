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

import org.jetbrains.kotlin.builtins.functions.FunctionInvokeDescriptor
import org.jetbrains.kotlin.diagnostics.Errors
import org.jetbrains.kotlin.diagnostics.Errors.*
import org.jetbrains.kotlin.diagnostics.Errors.BadNamedArgumentsTarget.*
import org.jetbrains.kotlin.psi.Call
import org.jetbrains.kotlin.psi.KtPsiUtil
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.BindingTrace
import org.jetbrains.kotlin.resolve.calls.components.*
import org.jetbrains.kotlin.resolve.calls.context.BasicCallResolutionContext
import org.jetbrains.kotlin.resolve.calls.inference.model.*
import org.jetbrains.kotlin.resolve.calls.model.*
import org.jetbrains.kotlin.resolve.calls.smartcasts.DataFlowValueFactory
import org.jetbrains.kotlin.resolve.calls.smartcasts.SmartCastManager
import org.jetbrains.kotlin.resolve.calls.tasks.TracingStrategy
import org.jetbrains.kotlin.resolve.calls.tower.*
import org.jetbrains.kotlin.resolve.scopes.receivers.ExpressionReceiver

class DiagnosticReporterByTrackingStrategy(
        val context: BasicCallResolutionContext,
        val trace: BindingTrace,
        val psiAstCall: PSIASTCall
): DiagnosticReporter {
    private val tracingStrategy: TracingStrategy get() = psiAstCall.tracingStrategy
    private val call: Call get() = psiAstCall.psiCall

    override fun onExplicitReceiver(diagnostic: CallDiagnostic) {

    }

    override fun onCall(diagnostic: CallDiagnostic) {
        when (diagnostic.javaClass) {
            VisibilityError::class.java -> tracingStrategy.invisibleMember(trace, (diagnostic as VisibilityError).invisibleMember)
            NoValueForParameter::class.java -> tracingStrategy.noValueForParameter(trace, (diagnostic as NoValueForParameter).parameterDescriptor)
            InstantiationOfAbstractClass::class.java -> tracingStrategy.instantiationOfAbstractClass(trace)
        }
    }

    override fun onTypeArguments(diagnostic: CallDiagnostic) {

    }

    override fun onCallName(diagnostic: CallDiagnostic) {

    }

    override fun onTypeArgument(typeArgument: TypeArgument, diagnostic: CallDiagnostic) {

    }

    override fun onCallReceiver(callReceiver: SimpleCallArgument, diagnostic: CallDiagnostic) {
        when (diagnostic.javaClass) {
            UnsafeCallError::class.java -> {
                val implicitInvokeCheck = (callReceiver as? ReceiverExpressionArgument)?.isVariableReceiverForInvoke ?: false
                tracingStrategy.unsafeCall(trace, callReceiver.receiver.receiverValue.type, implicitInvokeCheck)
            }
        }
    }

    override fun onCallArgument(callArgument: CallArgument, diagnostic: CallDiagnostic) {
        when (diagnostic.javaClass) {
            SmartCastDiagnostic::class.java -> reportSmartCast(diagnostic as SmartCastDiagnostic)
            UnstableSmartCast::class.java -> reportUnstableSmartCast(diagnostic as UnstableSmartCast)
            TooManyArguments::class.java ->
                trace.report(TOO_MANY_ARGUMENTS.on(callArgument.psiExpression!!, (diagnostic as TooManyArguments).descriptor))
            VarargArgumentOutsideParentheses::class.java ->
                trace.report(VARARG_OUTSIDE_PARENTHESES.on(callArgument.psiExpression!!))
        }
    }

    override fun onCallArgumentName(callArgument: CallArgument, diagnostic: CallDiagnostic) {
        val nameReference = callArgument.psiCallArgument.valueArgument.getArgumentName()?.referenceExpression ?:
                           error("Argument name should be not null for argument: $callArgument")
        when (diagnostic.javaClass) {
            NamedArgumentReference::class.java ->
                trace.record(BindingContext.REFERENCE_TARGET, nameReference, (diagnostic as NamedArgumentReference).parameterDescriptor)
            NameForAmbiguousParameter::class.java -> trace.report(NAME_FOR_AMBIGUOUS_PARAMETER.on(nameReference))
            NameNotFound::class.java -> trace.report(NAMED_PARAMETER_NOT_FOUND.on(nameReference, nameReference))

            NamedArgumentNotAllowed::class.java -> trace.report(NAMED_ARGUMENTS_NOT_ALLOWED.on(
                    nameReference,
                    if ((diagnostic as NamedArgumentNotAllowed).descriptor is FunctionInvokeDescriptor) INVOKE_ON_FUNCTION_TYPE else NON_KOTLIN_FUNCTION
            ))
            ArgumentPassedTwice::class.java -> trace.report(ARGUMENT_PASSED_TWICE.on(nameReference))
        }
    }

    override fun onCallArgumentSpread(callArgument: CallArgument, diagnostic: CallDiagnostic) {

    }

    private fun reportSmartCast(smartCastDiagnostic: SmartCastDiagnostic) {
        val expressionArgument = smartCastDiagnostic.expressionArgument
        if (expressionArgument is ExpressionArgumentImpl) {
            val context = context.replaceDataFlowInfo(expressionArgument.dataFlowInfoBeforeThisArgument)
            val argumentExpression = KtPsiUtil.getLastElementDeparenthesized(expressionArgument.valueArgument.getArgumentExpression (), context.statementFilter)
            val dataFlowValue = DataFlowValueFactory.createDataFlowValue(expressionArgument.receiver.receiverValue, context)
            SmartCastManager.checkAndRecordPossibleCast(
                    dataFlowValue, smartCastDiagnostic.smartCastType, argumentExpression, context, call,
                    recordExpressionType = true)
        }
        else if(expressionArgument is ReceiverExpressionArgument) {
            val receiverValue = expressionArgument.receiver.receiverValue
            val dataFlowValue = DataFlowValueFactory.createDataFlowValue(receiverValue, context)
            SmartCastManager.checkAndRecordPossibleCast(
                    dataFlowValue, smartCastDiagnostic.smartCastType, (receiverValue as? ExpressionReceiver)?.expression, context, call,
                    recordExpressionType = true)
        }
    }

    private fun reportUnstableSmartCast(unstableSmartCast: UnstableSmartCast) {
        // todo hack -- remove it after removing SmartCastManager
        reportSmartCast(SmartCastDiagnostic(unstableSmartCast.expressionArgument, unstableSmartCast.targetType))
    }

    override fun constraintError(diagnostic: CallDiagnostic) {
        when (diagnostic.javaClass) {
            NewConstraintError::class.java -> {
                val constraintError = diagnostic as NewConstraintError
                (constraintError.position as? ArgumentConstraintPosition)?.let {
                    val expression = it.argument.psiExpression ?: return
                    trace.report(Errors.TYPE_MISMATCH.on(expression, constraintError.upperType, constraintError.lowerType))
                }
                (constraintError.position as? ExplicitTypeParameterConstraintPosition)?.let {
                    val typeArgumentReference = (it.typeArgument as SimpleTypeArgumentImpl).typeReference
                    trace.report(UPPER_BOUND_VIOLATED.on(typeArgumentReference, constraintError.upperType, constraintError.lowerType))
                }
            }
        }
    }
}