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

import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.backend.common.DataClassMethodGenerator
import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.ir.UNDEFINED_OFFSET
import org.jetbrains.kotlin.ir.builders.*
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.putDefault
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.mapValueParameters
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi2ir.containsNull
import org.jetbrains.kotlin.psi2ir.endOffsetOrUndefined
import org.jetbrains.kotlin.psi2ir.findFirstFunction
import org.jetbrains.kotlin.psi2ir.startOffsetOrUndefined
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.DescriptorToSourceUtils
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.checker.KotlinTypeChecker
import java.lang.AssertionError

class DataClassMembersGenerator(
        declarationGenerator: DeclarationGenerator
) : DeclarationGeneratorExtension(declarationGenerator), Generator{
    fun generate(ktClassOrObject: KtClassOrObject, irClass: IrClass) {
        MyDataClassMethodGenerator(ktClassOrObject, irClass).generate()
    }

    private inner class MemberFunctionBuilder(
            val irClass: IrClass,
            val function: FunctionDescriptor,
            val origin: IrDeclarationOrigin,
            startOffset: Int = UNDEFINED_OFFSET,
            endOffset: Int = UNDEFINED_OFFSET
    ) : IrBlockBodyBuilder(context, Scope(function), startOffset, endOffset)  {
        lateinit var irFunction: IrFunction

        inline fun addToClass(body: MemberFunctionBuilder.(IrFunction) -> Unit): IrFunction {
            irFunction = this@DataClassMembersGenerator.context.symbolTable
                    .declareSimpleFunction(startOffset, endOffset, origin, function)

            body(irFunction)
            irFunction.body = doBuild()
            irClass.declarations.add(irFunction)
            return irFunction
        }

        fun putDefault(parameter: ValueParameterDescriptor, value: IrExpression) {
            irFunction.putDefault(parameter, irExprBody(value))
        }
    }

    private inner class MyDataClassMethodGenerator(
            ktClassOrObject: KtClassOrObject,
            val irClass: IrClass
    ) : DataClassMethodGenerator(ktClassOrObject, declarationGenerator.context.bindingContext) {
        private inline fun buildMember(
                function: FunctionDescriptor,
                psiElement: PsiElement? = null,
                body: MemberFunctionBuilder.(IrFunction) -> Unit
        ) {
            MemberFunctionBuilder(
                    irClass, function, IrDeclarationOrigin.GENERATED_DATA_CLASS_MEMBER,
                    psiElement.startOffsetOrUndefined, psiElement.endOffsetOrUndefined
            ).addToClass { irFunction ->
                irFunction.buildWithScope {
                    FunctionGenerator(declarationGenerator).generateSyntheticFunctionParameterDeclarations(irFunction)
                    body(irFunction)
                }
            }
        }

        override fun generateComponentFunction(function: FunctionDescriptor, parameter: ValueParameterDescriptor) {
            val ktParameter = DescriptorToSourceUtils.descriptorToDeclaration(parameter) ?:
                              throw AssertionError("No definition for data class constructor parameter $parameter")

            val property = getOrFail(BindingContext.VALUE_PARAMETER_AS_PROPERTY, parameter)

            buildMember(function, ktParameter) {
                +irReturn(irGet(irThis(), property))
            }
        }

        override fun generateCopyFunction(function: FunctionDescriptor, constructorParameters: List<KtParameter>) {
            val dataClassConstructor = classDescriptor.unsubstitutedPrimaryConstructor ?:
                                       throw AssertionError("Data class should have a primary constructor: $classDescriptor")

            buildMember(function) {
                function.valueParameters.forEach { parameter ->
                    val property = getOrFail(BindingContext.VALUE_PARAMETER_AS_PROPERTY, parameter)
                    putDefault(parameter, irGet(irThis(), property))
                }
                +irReturn(irCall(dataClassConstructor).mapValueParameters { irGet(function.valueParameters[it.index]) })
            }
        }

        override fun generateEqualsMethod(function: FunctionDescriptor, properties: List<PropertyDescriptor>) {
            buildMember(function) {
                +irIfThenReturnTrue(irEqeqeq(irThis(), irOther()))
                +irIfThenReturnFalse(irNotIs(irOther(), classDescriptor.defaultType))
                val otherWithCast = defineTemporary(irAs(irOther(), classDescriptor.defaultType), "other_with_cast")
                for (property in properties) {
                    +irIfThenReturnFalse(
                            irNotEquals(irGet(irThis(), property),
                                        irGet(irGet(otherWithCast), property)))
                }
                +irReturnTrue()
            }
        }

        private val INT = context.builtIns.int
        private val INT_TYPE = context.builtIns.intType

        private val IMUL = INT.findFirstFunction("times") { KotlinTypeChecker.DEFAULT.equalTypes(it.valueParameters[0].type, INT_TYPE) }
        private val IADD = INT.findFirstFunction("plus") { KotlinTypeChecker.DEFAULT.equalTypes(it.valueParameters[0].type, INT_TYPE) }

        private fun getHashCodeFunction(type: KotlinType): CallableDescriptor {
            val typeConstructorDescriptor = type.constructor.declarationDescriptor
            when (typeConstructorDescriptor) {
                is ClassDescriptor ->
                    return typeConstructorDescriptor.findFirstFunction("hashCode") { it.valueParameters.isEmpty() }
                is TypeParameterDescriptor ->
                    return getHashCodeFunction(context.builtIns.anyType) // TODO
                else ->
                    throw AssertionError("Unexpected type: $type")
            }
        }

        override fun generateHashCodeMethod(function: FunctionDescriptor, properties: List<PropertyDescriptor>) {
            buildMember(function) {
                val result = defineTemporaryVar(irInt(0), "result")
                var first = true
                for (property in properties) {
                    val hashCodeOfProperty = getHashCodeOfProperty(irThis(), property)
                    val irNewValue =
                            if (first) hashCodeOfProperty
                            else irCallOp(IADD, irCallOp(IMUL, irGet(result), irInt(31)), hashCodeOfProperty)
                    +irSetVar(result, irNewValue)
                    first = false
                }
                +irReturn(irGet(result))
            }
        }

        private fun MemberFunctionBuilder.getHashCodeOfProperty(receiver: IrExpression, property: PropertyDescriptor): IrExpression =
                when {
                    property.type.containsNull() ->
                        irLet(irGet(receiver, property)) { variable ->
                            irIfNull(context.builtIns.intType, irGet(variable), irInt(0), getHashCodeOf(irGet(variable)))
                        }
                    else ->
                        getHashCodeOf(irGet(receiver, property))
                }

        private fun MemberFunctionBuilder.getHashCodeOf(irValue: IrExpression): IrExpression =
                irCall(getHashCodeFunction(irValue.type)).apply { dispatchReceiver = irValue }

        override fun generateToStringMethod(function: FunctionDescriptor, properties: List<PropertyDescriptor>) {
            buildMember(function) {
                val irConcat = irConcat()
                irConcat.addArgument(irString(classDescriptor.name.asString() + "("))
                var first = true
                for (property in properties) {
                    if (!first) irConcat.addArgument(irString(", "))
                    irConcat.addArgument(irString(property.name.asString() + "="))
                    irConcat.addArgument(irGet(irThis(), property))
                    first = false
                }
                irConcat.addArgument(irString(")"))
                +irReturn(irConcat)
            }
        }
    }
}
