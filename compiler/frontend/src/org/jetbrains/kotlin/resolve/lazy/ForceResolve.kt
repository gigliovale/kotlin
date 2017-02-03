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
import org.jetbrains.kotlin.resolve.lazy.ForceResolve.Depth.*
import org.jetbrains.kotlin.resolve.scopes.MemberScope
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.TypeConstructor
import org.jetbrains.kotlin.types.asFlexibleType
import org.jetbrains.kotlin.types.isFlexible


interface LazyEntity {
    fun forceResolveAllContents()
}

object ForceResolve {

    enum class Depth {
        Deep, Shallow
    }


    @JvmStatic
    @JvmOverloads
    fun <T : Any> forceResolveAllContents(descriptor: T, depth: Depth = Deep): T {
        doForceResolveAllContents(descriptor, depth)
        return descriptor
    }

    @JvmStatic
    @JvmOverloads
    fun forceResolveAllContents(scope: MemberScope, depth: Depth = Deep) {
        forceResolveAllContents(DescriptorUtils.getAllDescriptors(scope), depth)
    }

    @JvmStatic
    @JvmOverloads
    fun forceResolveAllContents(descriptors: Iterable<DeclarationDescriptor>, depth: Depth = Deep) {
        for (descriptor in descriptors) {
            forceResolveAllContents(descriptor, depth)
        }
    }

    @JvmStatic
    @JvmOverloads
    fun forceResolveAllContents(types: Collection<KotlinType>, depth: Depth = Deep) {
        for (type in types) {
            forceResolveAllContents(type, depth)
        }
    }

    @JvmStatic
    @JvmOverloads
    fun forceResolveAllContents(typeConstructor: TypeConstructor, depth: Depth = Deep) {
        doForceResolveAllContents(typeConstructor, depth)
    }

    @JvmStatic
    @JvmOverloads
    fun forceResolveAllContents(annotations: Annotations, depth: Depth = Deep) {
        doForceResolveAllContents(annotations, depth)
        for (annotationWithTarget in annotations.getAllAnnotations()) {
            doForceResolveAllContents(annotationWithTarget.annotation, depth)
        }
    }

    private fun doForceResolveAllContents(obj: Any, depth: Depth) {
        (obj as? LazyEntity)?.forceResolveAllContents()
        (obj as? ValueParameterDescriptorImpl.WithDestructuringDeclaration)?.destructuringVariables
        if (obj is CallableDescriptor) {
            val callableDescriptor = obj
            val parameter = callableDescriptor.extensionReceiverParameter
            if (parameter != null) {
                forceResolveAllContents(parameter.type, depth)
            }
            for (parameterDescriptor in callableDescriptor.valueParameters) {
                forceResolveAllContents(parameterDescriptor, depth)
            }
            for (typeParameterDescriptor in callableDescriptor.typeParameters) {
                forceResolveAllContents(typeParameterDescriptor.upperBounds, depth)
            }
            forceResolveAllContents(callableDescriptor.returnType, depth)
            forceResolveAllContents(callableDescriptor.annotations, depth)
        }
        else if (obj is TypeAliasDescriptor) {
            forceResolveAllContents(obj.underlyingType, depth)
        }
    }

    @JvmStatic
    @JvmOverloads
    fun forceResolveAllContents(type: KotlinType?, depth: Depth = Deep): KotlinType? {
        if (type == null) return null

        forceResolveAllContents(type.annotations, depth)
        if (type.isFlexible()) {
            forceResolveAllContents(type.asFlexibleType().lowerBound, depth)
            forceResolveAllContents(type.asFlexibleType().upperBound, depth)
        }
        else {
            forceResolveAllContents(type.constructor, depth)
            for (projection in type.arguments) {
                if (!projection.isStarProjection) {
                    forceResolveAllContents(projection.type, depth)
                }
            }
        }
        return type
    }
}
