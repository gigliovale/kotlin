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

package org.jetbrains.kotlin.kserialization.extensions

import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.PropertyDescriptor
import org.jetbrains.kotlin.descriptors.SimpleFunctionDescriptor
import org.jetbrains.kotlin.kserialization.resolve.KSerializerDescriptorResolver
import org.jetbrains.kotlin.kserialization.resolve.isDefaultSerializable
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.name.SpecialNames
import org.jetbrains.kotlin.resolve.extensions.SyntheticResolveExtension
import org.jetbrains.kotlin.types.KotlinType
import java.util.*

class SerializationResolveExtension : SyntheticResolveExtension {
    override fun getSyntheticCompanionObjectNameIfNeeded(thisDescriptor: ClassDescriptor): Name? =
            if (thisDescriptor.isDefaultSerializable) SpecialNames.DEFAULT_NAME_FOR_COMPANION_OBJECT
            else null

    override fun addSyntheticSupertypes(thisDescriptor: ClassDescriptor, supertypes: MutableList<KotlinType>) {
        KSerializerDescriptorResolver.addSerializableSupertypes(thisDescriptor, supertypes)
        KSerializerDescriptorResolver.addSerializerSupertypes(thisDescriptor, supertypes)
    }

    override fun generateSyntheticMethods(thisDescriptor: ClassDescriptor, name: Name, fromSupertypes: List<SimpleFunctionDescriptor>, result: MutableCollection<SimpleFunctionDescriptor>) {
        KSerializerDescriptorResolver.generateSerializerMethods(thisDescriptor, fromSupertypes, name, result)
    }

    override fun generateSyntheticProperties(thisDescriptor: ClassDescriptor, name: Name, fromSupertypes: ArrayList<PropertyDescriptor>, result: MutableSet<PropertyDescriptor>) {
        KSerializerDescriptorResolver.generateSerializerProperties(thisDescriptor, fromSupertypes, name, result)
    }
}