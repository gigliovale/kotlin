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

package org.jetbrains.kotlin.psi2ir.generators

import org.jetbrains.kotlin.descriptors.CallableDescriptor
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.TypeParameterDescriptor
import org.jetbrains.kotlin.descriptors.ValueParameterDescriptor
import org.jetbrains.kotlin.descriptors.impl.SyntheticFieldDescriptor
import org.jetbrains.kotlin.descriptors.impl.TypeAliasConstructorDescriptor
import org.jetbrains.kotlin.ir.expressions.IrDeclarationReference
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrExpressionWithCopy
import org.jetbrains.kotlin.ir.expressions.impl.*
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.psiUtil.endOffset
import org.jetbrains.kotlin.psi.psiUtil.startOffset
import org.jetbrains.kotlin.psi2ir.intermediate.*
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.DescriptorUtils
import org.jetbrains.kotlin.resolve.ImportedFromObjectCallableDescriptor
import org.jetbrains.kotlin.resolve.calls.callResolverUtil.getSuperCallExpression
import org.jetbrains.kotlin.resolve.calls.callUtil.isSafeCall
import org.jetbrains.kotlin.resolve.calls.model.*
import org.jetbrains.kotlin.resolve.scopes.receivers.*
import org.jetbrains.kotlin.types.KotlinType
import java.lang.AssertionError

fun StatementGenerator.generateReceiverOrNull(ktDefaultElement: KtElement, receiver: ReceiverValue?): IntermediateValue? =
        receiver?.let { generateReceiver(ktDefaultElement, receiver) }

fun StatementGenerator.generateReceiver(ktDefaultElement: KtElement, receiver: ReceiverValue): IntermediateValue =
        generateReceiver(ktDefaultElement.startOffset, ktDefaultElement.endOffset, receiver)

fun StatementGenerator.generateReceiver(startOffset: Int, endOffset: Int, receiver: ReceiverValue): IntermediateValue =
        if (receiver is TransientReceiver)
            TransientReceiverValue(receiver.type)
        else generateDelegatedValue(receiver.type) {
            val receiverExpression = when (receiver) {
                is ImplicitClassReceiver -> {
                    val receiverClassDescriptor = receiver.classDescriptor
                    if (shouldGenerateReceiverAsSingletonReference(receiverClassDescriptor))
                        generateSingletonReference(receiverClassDescriptor, startOffset, endOffset, receiver.type)
                    else
                        IrGetValueImpl(startOffset, endOffset,
                                       context.symbolTable.referenceValueParameter(receiverClassDescriptor.thisAsReceiverParameter))
                }
                is ThisClassReceiver ->
                    generateThisOrSuperReceiver(receiver, receiver.classDescriptor)
                is SuperCallReceiverValue ->
                    generateThisOrSuperReceiver(receiver, receiver.thisType.constructor.declarationDescriptor as ClassDescriptor)
                is ExpressionReceiver ->
                    generateExpression(receiver.expression)
                is ClassValueReceiver ->
                    IrGetObjectValueImpl(receiver.expression.startOffset, receiver.expression.endOffset, receiver.type,
                                         context.symbolTable.referenceClass(receiver.classQualifier.descriptor as ClassDescriptor))
                is ExtensionReceiver ->
                    IrGetValueImpl(startOffset, startOffset,
                                   context.symbolTable.referenceValueParameter(receiver.declarationDescriptor.extensionReceiverParameter!!))
                else ->
                    TODO("Receiver: ${receiver::class.java.simpleName}")
            }

            if (receiverExpression is IrExpressionWithCopy)
                RematerializableValue(receiverExpression)
            else
                OnceExpressionValue(receiverExpression)
        }

fun StatementGenerator.generateSingletonReference(descriptor: ClassDescriptor, startOffset: Int, endOffset: Int, type: KotlinType): IrDeclarationReference =
        when {
            DescriptorUtils.isObject(descriptor) ->
                IrGetObjectValueImpl(startOffset, endOffset, type,
                                     context.symbolTable.referenceClass(descriptor))
            DescriptorUtils.isEnumEntry(descriptor) ->
                IrGetEnumValueImpl(startOffset, endOffset, type,
                                   context.symbolTable.referenceEnumEntry(descriptor))
            else -> {
                val companionObjectDescriptor = descriptor.companionObjectDescriptor
                                                ?: throw java.lang.AssertionError("Class value without companion object: $descriptor")
                IrGetObjectValueImpl(startOffset, endOffset, type,
                                     context.symbolTable.referenceClass(companionObjectDescriptor))
            }
        }

private fun StatementGenerator.shouldGenerateReceiverAsSingletonReference(receiverClassDescriptor: ClassDescriptor): Boolean {
    return receiverClassDescriptor.kind.isSingleton &&
           this.scopeOwner != receiverClassDescriptor && //For anonymous initializers
           this.scopeOwner.containingDeclaration != receiverClassDescriptor
}

private fun StatementGenerator.generateThisOrSuperReceiver(receiver: ReceiverValue, classDescriptor: ClassDescriptor): IrExpression {
    val expressionReceiver = receiver as? ExpressionReceiver ?:
                             throw AssertionError("'this' or 'super' receiver should be an expression receiver")
    val ktReceiver = expressionReceiver.expression
    return IrGetValueImpl(ktReceiver.startOffset, ktReceiver.endOffset,
                          context.symbolTable.referenceValueParameter(classDescriptor.thisAsReceiverParameter))
}

fun StatementGenerator.generateBackingFieldReceiver(
        startOffset: Int,
        endOffset: Int,
        resolvedCall: ResolvedCall<*>?,
        fieldDescriptor: SyntheticFieldDescriptor
): IntermediateValue? {
    val receiver = resolvedCall?.dispatchReceiver ?: fieldDescriptor.getDispatchReceiverForBackend() ?: return null
    return this.generateReceiver(startOffset, endOffset, receiver)
}

fun StatementGenerator.generateCallReceiver(
        ktDefaultElement: KtElement,
        calleeDescriptor: CallableDescriptor,
        dispatchReceiver: ReceiverValue?,
        extensionReceiver: ReceiverValue?,
        isSafe: Boolean,
        isAssignmentReceiver: Boolean = false
) : CallReceiver {
    val dispatchReceiverValue: IntermediateValue?
    val extensionReceiverValue: IntermediateValue?
    when (calleeDescriptor) {
        is ImportedFromObjectCallableDescriptor<*> -> {
            assert(dispatchReceiver == null) {
                "Call for member imported from object $calleeDescriptor has non-null dispatch receiver $dispatchReceiver"
            }
            dispatchReceiverValue = generateReceiverForCalleeImportedFromObject(ktDefaultElement.startOffset, ktDefaultElement.endOffset, calleeDescriptor)
            extensionReceiverValue = generateReceiverOrNull(ktDefaultElement, extensionReceiver)
        }
        is TypeAliasConstructorDescriptor -> {
            assert(!(dispatchReceiver != null && extensionReceiver != null)) {
                "Type alias constructor call for $calleeDescriptor can't have both dispatch receiver and extension receiver: " +
                "$dispatchReceiver, $extensionReceiver"
            }
            dispatchReceiverValue = generateReceiverOrNull(ktDefaultElement, extensionReceiver ?: dispatchReceiver)
            extensionReceiverValue = null
        }
        else -> {
            dispatchReceiverValue = generateReceiverOrNull(ktDefaultElement, dispatchReceiver)
            extensionReceiverValue = generateReceiverOrNull(ktDefaultElement, extensionReceiver)
        }
    }

    return when {
        !isSafe ->
            SimpleCallReceiver(dispatchReceiverValue, extensionReceiverValue)
        extensionReceiverValue != null  || dispatchReceiverValue != null->
            SafeCallReceiver(this, ktDefaultElement.startOffset, ktDefaultElement.endOffset,
                             extensionReceiverValue, dispatchReceiverValue, isAssignmentReceiver)
        else ->
            throw AssertionError("Safe call should have an explicit receiver: ${ktDefaultElement.text}")
    }
}

private fun StatementGenerator.generateReceiverForCalleeImportedFromObject(
        startOffset: Int,
        endOffset: Int,
        calleeDescriptor: ImportedFromObjectCallableDescriptor<*>
): ExpressionValue {
    val objectDescriptor = calleeDescriptor.containingObject
    val objectType = objectDescriptor.defaultType
    return generateExpressionValue(objectType) {
        IrGetObjectValueImpl(startOffset, endOffset, objectType,
                             context.symbolTable.referenceClass(objectDescriptor))
    }
}

fun StatementGenerator.generateVarargExpression(varargArgument: VarargValueArgument, valueParameter: ValueParameterDescriptor) : IrExpression? {
    if (varargArgument.arguments.isEmpty()) {
        return null
    }

    val varargStartOffset = varargArgument.arguments.fold(Int.MAX_VALUE) { minStartOffset, argument ->
        Math.min(minStartOffset, argument.asElement().startOffset)
    }
    val varargEndOffset = varargArgument.arguments.fold(Int.MIN_VALUE) { maxEndOffset, argument ->
        Math.max(maxEndOffset, argument.asElement().endOffset)
    }

    val varargElementType = valueParameter.varargElementType ?:
                            throw AssertionError("Vararg argument for non-vararg parameter $valueParameter")

    val irVararg = IrVarargImpl(varargStartOffset, varargEndOffset, valueParameter.type, varargElementType)

    for (argument in varargArgument.arguments) {
        val ktArgumentExpression = argument.getArgumentExpression() ?:
                                   throw AssertionError("No argument expression for vararg element ${argument.asElement().text}")
        val irVarargElement =
                if (argument.getSpreadElement() != null)
                    IrSpreadElementImpl(ktArgumentExpression.startOffset, ktArgumentExpression.endOffset,
                                                                            generateExpression(ktArgumentExpression))
                else
                    generateExpression(ktArgumentExpression)

        irVararg.addElement(irVarargElement)
    }

    return irVararg
}

fun StatementGenerator.generateValueArgument(valueArgument: ResolvedValueArgument, valueParameter: ValueParameterDescriptor): IrExpression? =
        when (valueArgument) {
            is DefaultValueArgument ->
                null
            is ExpressionValueArgument ->
                generateExpression(valueArgument.valueArgument!!.getArgumentExpression()!!)
            is VarargValueArgument ->
                generateVarargExpression(valueArgument, valueParameter)
            else ->
                TODO("Unexpected valueArgument: ${valueArgument::class.java.simpleName}")
        }

fun Generator.getSuperQualifier(resolvedCall: ResolvedCall<*>): ClassDescriptor? {
    val superCallExpression = getSuperCallExpression(resolvedCall.call) ?: return null
    return getOrFail(BindingContext.REFERENCE_TARGET, superCallExpression.instanceReference) as ClassDescriptor
}

fun StatementGenerator.pregenerateCall(resolvedCall: ResolvedCall<*>): CallBuilder {
    val call = pregenerateCallWithReceivers(resolvedCall)
    pregenerateValueArguments(call, resolvedCall)
    return call
}

fun getTypeArguments(resolvedCall: ResolvedCall<*>?): Map<TypeParameterDescriptor, KotlinType>? {
    if (resolvedCall == null) return null

    val descriptor = resolvedCall.resultingDescriptor
    if (descriptor.typeParameters.isEmpty()) return null

    return resolvedCall.typeArguments
}

private fun StatementGenerator.pregenerateValueArguments(call: CallBuilder, resolvedCall: ResolvedCall<*>) {
    resolvedCall.valueArgumentsByIndex!!.forEachIndexed { index, valueArgument ->
        val valueParameter = call.descriptor.valueParameters[index]
        call.irValueArgumentsByIndex[index] = generateValueArgument(valueArgument, valueParameter)
    }
}

fun StatementGenerator.pregenerateCallWithReceivers(resolvedCall: ResolvedCall<*>): CallBuilder {
    val call = CallBuilder(resolvedCall, unwrapCallableDescriptor(resolvedCall.resultingDescriptor))

    call.callReceiver = generateCallReceiver(resolvedCall.call.callElement,
                                             resolvedCall.resultingDescriptor,
                                             resolvedCall.dispatchReceiver,
                                             resolvedCall.extensionReceiver,
                                             isSafe = resolvedCall.call.isSafeCall())

    call.superQualifier = getSuperQualifier(resolvedCall)

    return call
}

fun unwrapCallableDescriptor(resultingDescriptor: CallableDescriptor): CallableDescriptor =
        when (resultingDescriptor) {
            is ImportedFromObjectCallableDescriptor<*> ->
                resultingDescriptor.callableFromObject
            is TypeAliasConstructorDescriptor ->
                resultingDescriptor.underlyingConstructorDescriptor
            else ->
                resultingDescriptor
        }
