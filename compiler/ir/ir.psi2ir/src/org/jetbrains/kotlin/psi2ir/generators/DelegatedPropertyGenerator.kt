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

import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.descriptors.annotations.Annotations
import org.jetbrains.kotlin.ir.builders.irGet
import org.jetbrains.kotlin.ir.builders.irReturn
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.declarations.impl.*
import org.jetbrains.kotlin.ir.descriptors.*
import org.jetbrains.kotlin.ir.expressions.*
import org.jetbrains.kotlin.ir.expressions.impl.IrCallableReferenceImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrExpressionBodyImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrGetValueImpl
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtPropertyDelegate
import org.jetbrains.kotlin.psi.psiUtil.endOffset
import org.jetbrains.kotlin.psi.psiUtil.startOffset
import org.jetbrains.kotlin.psi2ir.intermediate.*
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.types.KotlinType

class DelegatedPropertyGenerator(declarationGenerator: DeclarationGenerator) : DeclarationGeneratorExtension(declarationGenerator) {
    constructor(context: GeneratorContext) : this(DeclarationGenerator(context))

    fun generateDelegatedProperty(
            ktProperty: KtProperty,
            ktDelegate: KtPropertyDelegate,
            propertyDescriptor: PropertyDescriptor,
            irDelegateInitializer: IrExpressionBody
    ): IrProperty {
        val kPropertyType = getKPropertyTypeForDelegatedProperty(propertyDescriptor)

        val irDelegate = generateDelegateFieldForProperty(propertyDescriptor, kPropertyType, irDelegateInitializer, ktDelegate)
        val delegateDescriptor = irDelegate.descriptor as IrPropertyDelegateDescriptor

        val irProperty = IrPropertyImpl(
                ktProperty.startOffset, ktProperty.endOffset, IrDeclarationOrigin.DEFINED, true,
                propertyDescriptor, irDelegate)

        val delegateReceiverValue = createBackingFieldValueForDelegate(delegateDescriptor, ktDelegate)
        val getterDescriptor = propertyDescriptor.getter!!
        irProperty.getter = generateDelegatedPropertyAccessorExceptBody(ktProperty, ktDelegate, getterDescriptor).also { irGetter ->
            irGetter.body = generateDelegatedPropertyGetterBody(
                    ktDelegate, getterDescriptor, delegateReceiverValue,
                    createCallableReference(ktDelegate, kPropertyType, propertyDescriptor)
            )
        }

        if (propertyDescriptor.isVar) {
            val setterDescriptor = propertyDescriptor.setter!!
            irProperty.setter = generateDelegatedPropertyAccessorExceptBody(ktProperty, ktDelegate, setterDescriptor).also { irSetter ->
                irSetter.body = generateDelegatedPropertySetterBody(
                        ktDelegate, setterDescriptor, delegateReceiverValue,
                        createCallableReference(ktDelegate, kPropertyType, propertyDescriptor)
                )
            }
        }

        return irProperty
    }

    private fun generateDelegatedPropertyAccessorExceptBody(
            ktProperty: KtProperty,
            ktDelegate: KtPropertyDelegate,
            accessorDescriptor: PropertyAccessorDescriptor
    ): IrFunction =
            IrFunctionImpl(
                    ktDelegate.startOffset, ktDelegate.endOffset,
                    IrDeclarationOrigin.DELEGATED_PROPERTY_ACCESSOR,
                    accessorDescriptor
            ).also { irGetter ->
                FunctionGenerator(declarationGenerator).generateFunctionParameterDeclarations(irGetter, ktProperty, null)
            }


    private fun getKPropertyTypeForDelegatedProperty(propertyDescriptor: PropertyDescriptor): KotlinType {
        val receivers = listOfNotNull(propertyDescriptor.extensionReceiverParameter, propertyDescriptor.dispatchReceiverParameter)
        return context.reflectionTypes.getKPropertyType(Annotations.EMPTY, receivers.map{ it.type }, propertyDescriptor.type, propertyDescriptor.isVar)
    }

    private fun generateDelegateFieldForProperty(
            propertyDescriptor: PropertyDescriptor,
            kPropertyType: KotlinType,
            irDelegateInitializer: IrExpressionBody,
            ktDelegate: KtPropertyDelegate
    ): IrFieldImpl {
        val irActualDelegateInitializer = generateInitializerBodyForPropertyDelegate(propertyDescriptor, kPropertyType, irDelegateInitializer, ktDelegate)

        val delegateType = irActualDelegateInitializer.expression.type
        val delegateDescriptor = createPropertyDelegateDescriptor(propertyDescriptor, delegateType, kPropertyType)

        return IrFieldImpl(
                ktDelegate.startOffset, ktDelegate.endOffset, IrDeclarationOrigin.DELEGATE,
                delegateDescriptor, irActualDelegateInitializer
        )
    }

    private fun generateInitializerBodyForPropertyDelegate(
            property: VariableDescriptorWithAccessors,
            kPropertyType: KotlinType,
            irDelegateInitializer: IrExpressionBody,
            ktDelegate: KtPropertyDelegate
    ): IrExpressionBody {
        val provideDelegateResolvedCall = get(BindingContext.PROVIDE_DELEGATE_RESOLVED_CALL, property)
                                          ?: return irDelegateInitializer

        val statementGenerator = BodyGenerator(property, context).createStatementGenerator()
        val provideDelegateCall = statementGenerator.pregenerateCall(provideDelegateResolvedCall)
        provideDelegateCall.setExplicitReceiverValue(OnceExpressionValue(irDelegateInitializer.expression))
        provideDelegateCall.irValueArgumentsByIndex[1] = createCallableReference(ktDelegate, kPropertyType, property)
        val irProvideDelegate = CallGenerator(statementGenerator).generateCall(ktDelegate.startOffset, ktDelegate.endOffset, provideDelegateCall)
        return IrExpressionBodyImpl(irProvideDelegate)
    }

    private fun createBackingFieldValueForDelegate(delegateDescriptor: IrPropertyDelegateDescriptor, ktDelegate: KtPropertyDelegate): IntermediateValue {
        val thisClass = delegateDescriptor.correspondingProperty.containingDeclaration as? ClassDescriptor
        val thisValue = thisClass?.let {
            RematerializableValue(IrGetValueImpl(ktDelegate.startOffset, ktDelegate.endOffset, thisClass.thisAsReceiverParameter))
        }
        return BackingFieldLValue(ktDelegate.startOffset, ktDelegate.endOffset, delegateDescriptor, thisValue, null)
    }

    private fun createCallableReference(ktElement: KtElement, type: KotlinType, referencedDescriptor: CallableDescriptor): IrCallableReference =
            IrCallableReferenceImpl(ktElement.startOffset, ktElement.endOffset, type,
                                    referencedDescriptor, null, IrStatementOrigin.PROPERTY_REFERENCE_FOR_DELEGATE)

    fun generateLocalDelegatedProperty(
            ktProperty: KtProperty,
            ktDelegate: KtPropertyDelegate,
            variableDescriptor: VariableDescriptorWithAccessors,
            irDelegateInitializer: IrExpression
    ): IrLocalDelegatedProperty {
        val kPropertyType = getKPropertyTypeForLocalDelegatedProperty(variableDescriptor)

        val irActualDelegateInitializer =
                generateInitializerBodyForPropertyDelegate(
                        variableDescriptor, kPropertyType,
                        IrExpressionBodyImpl(irDelegateInitializer), ktDelegate
                ).expression
        val delegateType = irActualDelegateInitializer.type

        val delegateDescriptor = createLocalPropertyDelegatedDescriptor(variableDescriptor, delegateType, kPropertyType)

        val irDelegate = IrVariableImpl(
                ktDelegate.startOffset, ktDelegate.endOffset, IrDeclarationOrigin.DELEGATE,
                delegateDescriptor,
                irActualDelegateInitializer
        )

        val irLocalDelegatedProperty = IrLocalDelegatedPropertyImpl(
                ktProperty.startOffset, ktProperty.endOffset, IrDeclarationOrigin.DEFINED,
                variableDescriptor, irDelegate)

        val getterDescriptor = variableDescriptor.getter!!
        val delegateReceiverValue = createVariableValueForDelegate(delegateDescriptor, ktDelegate)
        irLocalDelegatedProperty.getter = createLocalPropertyAccessor(
                getterDescriptor, ktDelegate,
                generateDelegatedPropertyGetterBody(
                        ktDelegate, getterDescriptor, delegateReceiverValue,
                        createCallableReference(ktDelegate, delegateDescriptor.kPropertyType, delegateDescriptor.correspondingLocalProperty)))

        if (variableDescriptor.isVar) {
            val setterDescriptor = variableDescriptor.setter!!
            irLocalDelegatedProperty.setter = createLocalPropertyAccessor(
                    setterDescriptor, ktDelegate,
                    generateDelegatedPropertySetterBody(
                            ktDelegate, setterDescriptor, delegateReceiverValue,
                            createCallableReference(ktDelegate, delegateDescriptor.kPropertyType, delegateDescriptor.correspondingLocalProperty)))

        }

        return irLocalDelegatedProperty
    }

    private fun createVariableValueForDelegate(delegateDescriptor: IrLocalDelegatedPropertyDelegateDescriptor, ktDelegate: KtPropertyDelegate) =
            VariableLValue(ktDelegate.startOffset, ktDelegate.endOffset, delegateDescriptor)

    private fun createLocalPropertyAccessor(getterDescriptor: VariableAccessorDescriptor, ktDelegate: KtPropertyDelegate, body: IrBody) =
            IrFunctionImpl(ktDelegate.startOffset, ktDelegate.endOffset, IrDeclarationOrigin.DELEGATED_PROPERTY_ACCESSOR,
                           getterDescriptor, body)

    private fun createLocalPropertyDelegatedDescriptor(
            variableDescriptor: VariableDescriptorWithAccessors,
            delegateType: KotlinType,
            kPropertyType: KotlinType
    ): IrLocalDelegatedPropertyDelegateDescriptor {
        return IrLocalDelegatedPropertyDelegateDescriptorImpl(variableDescriptor, delegateType, kPropertyType)
    }

    private fun getKPropertyTypeForLocalDelegatedProperty(variableDescriptor: VariableDescriptorWithAccessors) =
            context.reflectionTypes.getKPropertyType(Annotations.EMPTY, emptyList(), variableDescriptor.type, variableDescriptor.isVar)

    private fun createPropertyDelegateDescriptor(
            propertyDescriptor: PropertyDescriptor,
            delegateType: KotlinType,
            kPropertyType: KotlinType
    ): IrPropertyDelegateDescriptor =
            IrPropertyDelegateDescriptorImpl(propertyDescriptor, delegateType, kPropertyType)

    fun generateDelegatedPropertyGetterBody(
            ktDelegate: KtPropertyDelegate,
            getterDescriptor: VariableAccessorDescriptor,
            delegateReceiverValue: IntermediateValue,
            irPropertyReference: IrCallableReference
    ): IrBody = with(BodyGenerator(getterDescriptor, context)) {
        irBlockBody(ktDelegate) {
            val statementGenerator = createStatementGenerator()
            val conventionMethodResolvedCall = getOrFail(BindingContext.DELEGATED_PROPERTY_RESOLVED_CALL, getterDescriptor)
            val conventionMethodCall = statementGenerator.pregenerateCall(conventionMethodResolvedCall)
            conventionMethodCall.setExplicitReceiverValue(delegateReceiverValue)
            conventionMethodCall.irValueArgumentsByIndex[1] = irPropertyReference
            +irReturn(CallGenerator(statementGenerator).generateCall(ktDelegate.startOffset, ktDelegate.endOffset, conventionMethodCall))
        }
    }

    fun generateDelegatedPropertySetterBody(
            ktDelegate: KtPropertyDelegate,
            setterDescriptor: VariableAccessorDescriptor,
            delegateReceiverValue: IntermediateValue,
            irPropertyReference: IrCallableReference
    ): IrBody = with(BodyGenerator(setterDescriptor, context)) {
        irBlockBody(ktDelegate) {
            val statementGenerator = createStatementGenerator()
            val conventionMethodResolvedCall = getOrFail(BindingContext.DELEGATED_PROPERTY_RESOLVED_CALL, setterDescriptor)
            val conventionMethodCall = statementGenerator.pregenerateCall(conventionMethodResolvedCall)
            conventionMethodCall.setExplicitReceiverValue(delegateReceiverValue)
            conventionMethodCall.irValueArgumentsByIndex[1] = irPropertyReference
            conventionMethodCall.irValueArgumentsByIndex[2] = irGet(setterDescriptor.valueParameters[0])
            +irReturn(CallGenerator(statementGenerator).generateCall(ktDelegate.startOffset, ktDelegate.endOffset, conventionMethodCall))
        }
    }
}