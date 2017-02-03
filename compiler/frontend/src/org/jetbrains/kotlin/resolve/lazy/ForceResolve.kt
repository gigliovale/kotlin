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

package org.jetbrains.kotlin.resolve.lazy

import org.jetbrains.kotlin.descriptors.CallableDescriptor
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.descriptors.TypeAliasDescriptor
import org.jetbrains.kotlin.descriptors.annotations.Annotations
import org.jetbrains.kotlin.descriptors.impl.ValueParameterDescriptorImpl
import org.jetbrains.kotlin.resolve.DescriptorUtils
import org.jetbrains.kotlin.resolve.scopes.MemberScope
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.TypeConstructor
import org.jetbrains.kotlin.types.asFlexibleType
import org.jetbrains.kotlin.types.isFlexible

object ForceResolve {
    @JvmStatic
    fun <T : Any> forceResolveAllContents(descriptor: T): T {
        doForceResolveAllContents(descriptor)
        return descriptor
    }

    @JvmStatic
    fun forceResolveAllContents(scope: MemberScope) {
        forceResolveAllContents(DescriptorUtils.getAllDescriptors(scope))
    }

    @JvmStatic
    fun forceResolveAllContents(descriptors: Iterable<DeclarationDescriptor>) {
        for (descriptor in descriptors) {
            forceResolveAllContents(descriptor)
        }
    }

    @JvmStatic
    fun forceResolveAllContents(types: Collection<KotlinType>) {
        for (type in types) {
            forceResolveAllContents(type)
        }
    }

    @JvmStatic
    fun forceResolveAllContents(typeConstructor: TypeConstructor) {
        doForceResolveAllContents(typeConstructor)
    }

    @JvmStatic
    fun forceResolveAllContents(annotations: Annotations) {
        doForceResolveAllContents(annotations)
        for (annotationWithTarget in annotations.getAllAnnotations()) {
            doForceResolveAllContents(annotationWithTarget.annotation)
        }
    }

    private fun doForceResolveAllContents(obj: Any) {
        (obj as? LazyEntity)?.forceResolveAllContents()
        (obj as? ValueParameterDescriptorImpl.WithDestructuringDeclaration)?.destructuringVariables
        if (obj is CallableDescriptor) {
            val callableDescriptor = obj
            val parameter = callableDescriptor.extensionReceiverParameter
            if (parameter != null) {
                forceResolveAllContents(parameter.type)
            }
            for (parameterDescriptor in callableDescriptor.valueParameters) {
                forceResolveAllContents(parameterDescriptor)
            }
            for (typeParameterDescriptor in callableDescriptor.typeParameters) {
                forceResolveAllContents(typeParameterDescriptor.upperBounds)
            }
            forceResolveAllContents(callableDescriptor.returnType)
            forceResolveAllContents(callableDescriptor.annotations)
        }
        else if (obj is TypeAliasDescriptor) {
            forceResolveAllContents(obj.underlyingType)
        }
    }

    @JvmStatic
    fun forceResolveAllContents(type: KotlinType?): KotlinType? {
        if (type == null) return null

        forceResolveAllContents(type.annotations)
        if (type.isFlexible()) {
            forceResolveAllContents(type.asFlexibleType().lowerBound)
            forceResolveAllContents(type.asFlexibleType().upperBound)
        }
        else {
            forceResolveAllContents(type.constructor)
            for (projection in type.arguments) {
                if (!projection.isStarProjection) {
                    forceResolveAllContents(projection.type)
                }
            }
        }
        return type
    }
}
