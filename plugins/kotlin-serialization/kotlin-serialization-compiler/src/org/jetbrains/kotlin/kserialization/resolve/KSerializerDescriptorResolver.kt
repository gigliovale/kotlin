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

package org.jetbrains.kotlin.kserialization.resolve

import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.descriptors.annotations.Annotations
import org.jetbrains.kotlin.descriptors.impl.*
import org.jetbrains.kotlin.incremental.components.NoLookupLocation
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.BindingTrace
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.Variance
import org.jetbrains.kotlin.types.typeUtil.createProjection
import java.util.*

object KSerializerDescriptorResolver {

    val SERIALIZABLE_CLASS = "serializableClass"
    val SAVE = "save"
    val LOAD = "load"

    val SERIALIZABLE_CLASS_NAME = Name.identifier("serializableClass")
    val SAVE_NAME = Name.identifier(SAVE)
    val LOAD_NAME = Name.identifier(LOAD)

    fun addSerializableSupertypes(classDescriptor: ClassDescriptor, supertypes: MutableList<KotlinType>) {
        if (classDescriptor.isDefaultSerializable && supertypes.none(::isJavaSerializable)) {
            supertypes.add(classDescriptor.getJavaSerializableType())
        }
    }

    fun addSerializerSupertypes(classDescriptor: ClassDescriptor, supertypes: MutableList<KotlinType>) {
        val serializableClassDescriptor = getSerializableClassDescriptorBySerializer(classDescriptor) ?: return
        if (supertypes.none(::isKSerializer)) {
            supertypes.add(classDescriptor.getKSerializerType(serializableClassDescriptor.defaultType))
        }
    }

    fun generateSerializerProperties(thisDescriptor: ClassDescriptor,
                                     fromSupertypes: ArrayList<PropertyDescriptor>,
                                     name: Name,
                                     result: MutableSet<PropertyDescriptor>) {
        val classDescriptor = getSerializableClassDescriptorBySerializer(thisDescriptor) ?: return
        // todo: check if it is already defined
        if (name == SERIALIZABLE_CLASS_NAME)
            result.add(createSerializableClassPropertyDescriptor(thisDescriptor, classDescriptor))

    }

    fun generateSerializerMethods(thisDescriptor: ClassDescriptor,
                                  fromSupertypes: List<SimpleFunctionDescriptor>,
                                  name: Name,
                                  result: MutableCollection<SimpleFunctionDescriptor>) {
        val classDescriptor = getSerializableClassDescriptorBySerializer(thisDescriptor) ?: return

        fun shouldAddSerializerFunction(checkParameters: (FunctionDescriptor) -> Boolean): Boolean {
            // Add 'save' / 'load' iff there is no such declared member AND there is no such final member in supertypes
            return result.none(checkParameters) &&
                   fromSupertypes.none { checkParameters(it) && it.modality == Modality.FINAL }
        }

        if (name == SAVE_NAME &&
            shouldAddSerializerFunction { classDescriptor.checkSaveMethodParameters(it.valueParameters) }) {
            result.add(createSaveFunctionDescriptor(thisDescriptor, classDescriptor))
        }

        if (name == LOAD_NAME &&
            shouldAddSerializerFunction { classDescriptor.checkLoadMethodParameters(it.valueParameters) }) {
            result.add(createLoadFunctionDescriptor(thisDescriptor, classDescriptor))
        }
    }

    fun createSerializableClassPropertyDescriptor(companionDescriptor: ClassDescriptor, classDescriptor: ClassDescriptor): PropertyDescriptor =
            doCreateSerializerProperty(companionDescriptor, classDescriptor, SERIALIZABLE_CLASS_NAME)

    fun createSaveFunctionDescriptor(companionDescriptor: ClassDescriptor, classDescriptor: ClassDescriptor): SimpleFunctionDescriptor =
            doCreateSerializerFunction(companionDescriptor, classDescriptor, SAVE_NAME)

    fun createLoadFunctionDescriptor(companionDescriptor: ClassDescriptor, classDescriptor: ClassDescriptor): SimpleFunctionDescriptor =
            doCreateSerializerFunction(companionDescriptor, classDescriptor, LOAD_NAME)

    private fun doCreateSerializerProperty(
            companionDescriptor: ClassDescriptor,
            classDescriptor: ClassDescriptor,
            name: Name
    ): PropertyDescriptor {
        val typeParam = listOf(createProjection(classDescriptor.defaultType, Variance.INVARIANT, null))
        val propertyFromSerializer = companionDescriptor.getKSerializerDescriptor().getMemberScope(typeParam)
                .getContributedVariables(name, NoLookupLocation.FROM_BUILTINS).single()

        val propertyDescriptor = PropertyDescriptorImpl.create(
                companionDescriptor, Annotations.EMPTY, Modality.OPEN, Visibilities.PUBLIC, false, name,
                CallableMemberDescriptor.Kind.SYNTHESIZED, companionDescriptor.source, false, false, false, false, false, false
        )

        val extensionReceiverParameter: ReceiverParameterDescriptor? = null // kludge to disambiguate call
        propertyDescriptor.setType(propertyFromSerializer.type,
                                   propertyFromSerializer.typeParameters,
                                   companionDescriptor.thisAsReceiverParameter,
                                   extensionReceiverParameter)

        val propertyGetter = PropertyGetterDescriptorImpl(
                propertyDescriptor, Annotations.EMPTY, Modality.OPEN, Visibilities.PUBLIC, false, false, false,
                CallableMemberDescriptor.Kind.SYNTHESIZED, null, companionDescriptor.source
        )

        propertyGetter.initialize(propertyFromSerializer.type)
        propertyDescriptor.initialize(propertyGetter, null)
        propertyDescriptor.overriddenDescriptors = listOf(propertyFromSerializer)

        return propertyDescriptor
    }

    private fun doCreateSerializerFunction(
            companionDescriptor: ClassDescriptor,
            classDescriptor: ClassDescriptor,
            name: Name
    ): SimpleFunctionDescriptor {
        val functionDescriptor = SimpleFunctionDescriptorImpl.create(
                companionDescriptor, Annotations.EMPTY, name, CallableMemberDescriptor.Kind.SYNTHESIZED, companionDescriptor.source
        )

        val typeParam = listOf(createProjection(classDescriptor.defaultType, Variance.INVARIANT, null))
        val functionFromSerializer = companionDescriptor.getKSerializerDescriptor().getMemberScope(typeParam)
                .getContributedFunctions(name, NoLookupLocation.FROM_BUILTINS).single()

        functionDescriptor.initialize(
                null,
                companionDescriptor.thisAsReceiverParameter,
                functionFromSerializer.typeParameters,
                functionFromSerializer.valueParameters.map { it.copy(functionDescriptor, it.name, it.index) },
                functionFromSerializer.returnType,
                Modality.OPEN,
                Visibilities.PUBLIC
        )

        return functionDescriptor
    }

    // todo: not used yet, do we need a descriptor at all?
    fun createLoadConstructorDescriptor(
            constructorParameters: List<ValueParameterDescriptor>,
            classDescriptor: ClassDescriptor,
            trace: BindingTrace
    ): ConstructorDescriptor {
        val functionDescriptor = ClassConstructorDescriptorImpl.create(
                classDescriptor,
                Annotations.EMPTY,
                true,
                classDescriptor.source
        )

        val parameterDescriptors = arrayListOf<ValueParameterDescriptor>()

        for (parameter in constructorParameters) {
            val propertyDescriptor = trace.bindingContext.get(BindingContext.VALUE_PARAMETER_AS_PROPERTY, parameter)
            // If a parameter doesn't have the corresponding property, it must not have a default value in the 'copy' function
            val declaresDefaultValue = propertyDescriptor != null
            val parameterDescriptor = ValueParameterDescriptorImpl(
                    functionDescriptor, null, parameter.index, parameter.annotations, parameter.name, parameter.type, declaresDefaultValue,
                    parameter.isCrossinline, parameter.isNoinline, parameter.varargElementType, parameter.source
            )
            parameterDescriptors.add(parameterDescriptor)
            if (declaresDefaultValue) {
                trace.record(BindingContext.VALUE_PARAMETER_AS_PROPERTY, parameterDescriptor, propertyDescriptor)
            }
        }

        functionDescriptor.initialize(
                constructorParameters,
                Visibilities.INTERNAL
        )

        //trace.record(BindingContext.SERIALIZABLE_CLASS_LOAD_CONSTRUCTOR, classDescriptor, functionDescriptor)
        return functionDescriptor
    }
}
