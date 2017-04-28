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
import org.jetbrains.kotlin.ir.declarations.IrDeclaration
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.IrProperty
import org.jetbrains.kotlin.ir.declarations.impl.IrFieldImpl
import org.jetbrains.kotlin.ir.declarations.impl.IrFunctionImpl
import org.jetbrains.kotlin.ir.declarations.impl.IrPropertyImpl
import org.jetbrains.kotlin.ir.expressions.IrBlockBody
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrStatementOrigin
import org.jetbrains.kotlin.ir.expressions.impl.*
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtPropertyDelegate
import org.jetbrains.kotlin.psi.psiUtil.endOffset
import org.jetbrains.kotlin.psi.psiUtil.startOffset
import org.jetbrains.kotlin.psi2ir.endOffsetOrUndefined
import org.jetbrains.kotlin.psi2ir.startOffsetOrUndefined
import org.jetbrains.kotlin.resolve.BindingContext

class PropertyGenerator(declarationGenerator: DeclarationGenerator) : DeclarationGeneratorExtension(declarationGenerator) {
    fun generatePropertyDeclaration(ktProperty: KtProperty): IrProperty {
        val propertyDescriptor = getPropertyDescriptor(ktProperty)
        val ktDelegate = ktProperty.delegate
        return if (ktDelegate != null)
            generateDelegatedProperty(ktProperty, ktDelegate, propertyDescriptor)
        else
            generateSimpleProperty(ktProperty, propertyDescriptor)
    }

    fun generatePropertyForPrimaryConstructorParameter(ktParameter: KtParameter): IrDeclaration {
        val valueParameterDescriptor = getOrFail(BindingContext.VALUE_PARAMETER, ktParameter)
        val propertyDescriptor = getOrFail(BindingContext.PRIMARY_CONSTRUCTOR_PARAMETER, ktParameter)

        val irProperty = IrPropertyImpl(ktParameter.startOffset, ktParameter.endOffset, IrDeclarationOrigin.DEFINED, false, propertyDescriptor)

        val irField = IrFieldImpl(ktParameter.startOffset, ktParameter.endOffset, IrDeclarationOrigin.PROPERTY_BACKING_FIELD, propertyDescriptor)
        val irGetParameter = IrGetValueImpl(ktParameter.startOffset, ktParameter.endOffset,
                                            valueParameterDescriptor, IrStatementOrigin.INITIALIZE_PROPERTY_FROM_PARAMETER)
        irField.initializer = IrExpressionBodyImpl(irGetParameter)
        irProperty.backingField = irField

        val getter = propertyDescriptor.getter ?:
                     throw AssertionError("Property declared in primary constructor has no getter: $propertyDescriptor")
        irProperty.getter = generateDefaultAccessor(getter, ktParameter, isGetter = true)

        if (propertyDescriptor.isVar) {
            val setter = propertyDescriptor.setter ?:
                         throw AssertionError("Property declared in primary constructor has no setter: $propertyDescriptor")
            irProperty.setter = generateDefaultAccessor(setter, ktParameter, isGetter = false)
        }

        return irProperty
    }

    fun generateDefaultAccessor(descriptor: PropertyAccessorDescriptor, ktElement: KtElement, isGetter: Boolean): IrFunction {
        val irAccessor = IrFunctionImpl(ktElement.startOffsetOrUndefined, ktElement.endOffsetOrUndefined, IrDeclarationOrigin.DEFAULT_PROPERTY_ACCESSOR, descriptor)
        val accessorDescriptor = irAccessor.descriptor
        declarationGenerator.generateTypeParameterDeclarations(irAccessor, accessorDescriptor.typeParameters)
        FunctionGenerator(declarationGenerator).generateSyntheticFunctionParameterDeclarations(irAccessor)
        irAccessor.body =
                if (isGetter) generateDefaultGetterBody(ktElement, descriptor as PropertyGetterDescriptor)
                else generateDefaultSetterBody(ktElement, descriptor as PropertySetterDescriptor)
        return irAccessor
    }

    private fun generateDelegatedProperty(ktProperty: KtProperty, ktDelegate: KtPropertyDelegate, propertyDescriptor: PropertyDescriptor): IrProperty {
        val ktDelegateExpression = ktDelegate.expression!!
        val irDelegateInitializer = declarationGenerator.generateInitializerBody(propertyDescriptor, ktDelegateExpression)
        return DelegatedPropertyGenerator(declarationGenerator).generateDelegatedProperty(ktProperty, ktDelegate, propertyDescriptor, irDelegateInitializer)
    }

    private fun generateSimpleProperty(ktProperty: KtProperty, propertyDescriptor: PropertyDescriptor): IrProperty {
        val irProperty = IrPropertyImpl(ktProperty.startOffset, ktProperty.endOffset, IrDeclarationOrigin.DEFINED, false, propertyDescriptor)

        val irField = if (propertyDescriptor.hasBackingField()) {
            IrFieldImpl(ktProperty.startOffset, ktProperty.endOffset, IrDeclarationOrigin.PROPERTY_BACKING_FIELD, propertyDescriptor,
                        ktProperty.initializer?.let { declarationGenerator.generateInitializerBody(propertyDescriptor, it) })
        }
        else null
        irProperty.backingField = irField

        irProperty.getter = generateGetterIfRequired(ktProperty, propertyDescriptor)

        irProperty.setter = generateSetterIfRequired(ktProperty, propertyDescriptor)

        return irProperty
    }

    private fun PropertyDescriptor.hasBackingField(): Boolean =
            get(BindingContext.BACKING_FIELD_REQUIRED, this) ?: false

    private fun generateGetterIfRequired(ktProperty: KtProperty, property: PropertyDescriptor): IrFunction? {
        val getter = property.getter ?: return null

        val ktGetter = ktProperty.getter

        val irGetter = ktGetter?.let {
            IrFunctionImpl(it.startOffset, it.endOffset, IrDeclarationOrigin.DEFINED, getter)
        } ?: IrFunctionImpl(ktProperty.startOffset, ktProperty.endOffset, IrDeclarationOrigin.DEFAULT_PROPERTY_ACCESSOR, getter)
        FunctionGenerator(declarationGenerator).generateFunctionParameterDeclarations(irGetter, ktProperty, ktProperty.receiverTypeReference)

        irGetter.body = ktGetter?.bodyExpression?.let {
            declarationGenerator.generateFunctionBody(getter, it )
        } ?: generateDefaultGetterBody(ktProperty, getter)

        return irGetter
    }

    private fun generateSetterIfRequired(ktProperty: KtProperty, property: PropertyDescriptor): IrFunction? {
        if (!property.isVar) return null
        val setter = property.setter ?: return null

        val ktSetter = ktProperty.setter

        val irSetter = ktSetter?.let {
            IrFunctionImpl(it.startOffset, it.endOffset, IrDeclarationOrigin.DEFINED, setter)
        } ?: IrFunctionImpl(ktProperty.startOffset, ktProperty.endOffset, IrDeclarationOrigin.DEFAULT_PROPERTY_ACCESSOR, setter)
        FunctionGenerator(declarationGenerator).generateFunctionParameterDeclarations(irSetter, ktProperty, ktProperty.receiverTypeReference)

        irSetter.body = ktSetter?.bodyExpression?.let {
            declarationGenerator.generateFunctionBody(setter, it )
        } ?: generateDefaultSetterBody(ktProperty, setter)

        return irSetter
    }

    private fun generateDefaultGetterBody(ktProperty: KtElement, getter: PropertyGetterDescriptor): IrBlockBody {
        val property = getter.correspondingProperty

        val irBody = IrBlockBodyImpl(ktProperty.startOffset, ktProperty.endOffset)

        val receiver = generateReceiverExpressionForDefaultPropertyAccessor(ktProperty, property)

        irBody.statements.add(IrReturnImpl(ktProperty.startOffset, ktProperty.endOffset, context.builtIns.nothingType, getter,
                                           IrGetFieldImpl(ktProperty.startOffset, ktProperty.endOffset, property, receiver)))
        return irBody
    }

    private fun generateDefaultSetterBody(ktProperty: KtElement, setter: PropertySetterDescriptor): IrBlockBody {
        val property = setter.correspondingProperty

        val irBody = IrBlockBodyImpl(ktProperty.startOffset, ktProperty.endOffset)

        val receiver = generateReceiverExpressionForDefaultPropertyAccessor(ktProperty, property)

        val setterParameter = setter.valueParameters.single()
        irBody.statements.add(IrSetFieldImpl(ktProperty.startOffset, ktProperty.endOffset, property, receiver,
                                             IrGetValueImpl(ktProperty.startOffset, ktProperty.endOffset, setterParameter)))
        return irBody
    }

    private fun generateReceiverExpressionForDefaultPropertyAccessor(ktProperty: KtElement, property: PropertyDescriptor): IrExpression? {
        val containingDeclaration = property.containingDeclaration
        val receiver =
                if (containingDeclaration is ClassDescriptor)
                    IrGetValueImpl(ktProperty.startOffset, ktProperty.endOffset, containingDeclaration.thisAsReceiverParameter)
                else
                    null
        return receiver
    }

    private fun getPropertyDescriptor(ktProperty: KtProperty): PropertyDescriptor {
        val variableDescriptor = getOrFail(BindingContext.VARIABLE, ktProperty)
        val propertyDescriptor = variableDescriptor as? PropertyDescriptor ?: TODO("not a property?")
        return propertyDescriptor
    }
}
