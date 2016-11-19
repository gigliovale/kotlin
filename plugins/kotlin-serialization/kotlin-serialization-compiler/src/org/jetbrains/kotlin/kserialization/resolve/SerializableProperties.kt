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

import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.PropertyDescriptor
import org.jetbrains.kotlin.descriptors.ValueParameterDescriptor
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.scopes.DescriptorKindFilter

class SerializableProperties(serializableClass: ClassDescriptor, bindingContext: BindingContext) {
    private val primaryConstructorParameters: List<ValueParameterDescriptor> =
            serializableClass.unsubstitutedPrimaryConstructor?.valueParameters ?: emptyList()

    private val primaryConstructorProperties: Set<PropertyDescriptor> =
            primaryConstructorParameters.asSequence()
                .mapNotNull { parameter -> bindingContext[BindingContext.VALUE_PARAMETER_AS_PROPERTY, parameter] }
                .toSet()

    val isExternallySerializable: Boolean =
            primaryConstructorParameters.size == primaryConstructorProperties.size

    val serializableProperties: List<SerializableProperty> =
            serializableClass.unsubstitutedMemberScope.getContributedDescriptors(DescriptorKindFilter.VARIABLES)
                .asSequence()
                .filterIsInstance<PropertyDescriptor>()
                .filter { it.isVar || primaryConstructorProperties.contains(it) }
                .map(::SerializableProperty)
                .toList()

    val serializableConstructorProperties: List<SerializableProperty> =
            serializableProperties.asSequence()
                .filter { primaryConstructorProperties.contains(it.descriptor) }
                .toList()

    val serializableStandaloneProperties: List<SerializableProperty> =
            serializableProperties.minus(serializableConstructorProperties)

    val size = serializableProperties.size
    val indices = serializableProperties.indices
    operator fun get(index: Int) = serializableProperties[index]
    operator fun iterator() = serializableProperties.iterator()
}