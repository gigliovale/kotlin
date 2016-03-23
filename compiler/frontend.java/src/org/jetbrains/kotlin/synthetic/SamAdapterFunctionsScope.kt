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

package org.jetbrains.kotlin.synthetic

import com.intellij.util.SmartList
import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.descriptors.annotations.Annotations
import org.jetbrains.kotlin.descriptors.impl.SimpleFunctionDescriptorImpl
import org.jetbrains.kotlin.incremental.components.LookupLocation
import org.jetbrains.kotlin.load.java.sam.SingleAbstractMethodUtils
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.resolve.scopes.DescriptorKindFilter
import org.jetbrains.kotlin.resolve.scopes.SyntheticScope
import org.jetbrains.kotlin.storage.StorageManager
import org.jetbrains.kotlin.types.*
import java.util.*

interface SamAdapterExtensionFunctionDescriptor : FunctionDescriptor {
    val sourceFunction: FunctionDescriptor
}

class SamAdapterFunctionsScope(storageManager: StorageManager) : SyntheticScope {
    private val samForOriginalFunction = storageManager.createMemoizedFunctionWithNullableValues<FunctionDescriptor, FunctionDescriptor> { originalFunction ->
        createSamAdapterForFunctionNotCached(originalFunction)
    }

    override fun getSyntheticMemberFunctions(receiverTypes: Collection<KotlinType>, name: Name, location: LookupLocation): Collection<FunctionDescriptor> {
        var result: SmartList<FunctionDescriptor>? = null
        for (type in receiverTypes) {
            for (function in type.memberScope.getContributedFunctions(name, location)) {
                val samAdapter = createSamAdapterForFunction(function)
                if (samAdapter != null) {
                    if (result == null) {
                        result = SmartList()
                    }
                    result.add(samAdapter)
                }
            }
        }
        return when {
            result == null -> emptyList()
            result.size > 1 -> result.toSet()
            else -> result
        }
    }

    override fun getSyntheticMemberFunctions(receiverTypes: Collection<KotlinType>): Collection<FunctionDescriptor> {
        return receiverTypes.flatMapTo(LinkedHashSet<FunctionDescriptor>()) { type ->
            type.memberScope.getContributedDescriptors(DescriptorKindFilter.FUNCTIONS)
                    .filterIsInstance<FunctionDescriptor>()
                    .mapNotNull { createSamAdapterForFunction(it.original) }
        }
    }

    override fun getSyntheticExtensionProperties(receiverTypes: Collection<KotlinType>, name: Name, location: LookupLocation): Collection<PropertyDescriptor> = emptyList()

    override fun getSyntheticExtensionProperties(receiverTypes: Collection<KotlinType>): Collection<PropertyDescriptor> = emptyList()

    private fun createSamAdapterForFunction(function: FunctionDescriptor): FunctionDescriptor? {
        if (function.original === function) { // TODO: identityEquals?
            return samForOriginalFunction(function)
        }
        else {
            return createSamAdapterForFunctionNotCached(function)
        }
    }

    private fun createSamAdapterForFunctionNotCached(function: FunctionDescriptor): FunctionDescriptor? {
        val originalFunction = function.original
        if (!originalFunction.visibility.isVisibleOutside()) return null
        if (!originalFunction.hasJavaOriginInHierarchy()) return null //TODO: should we go into base at all?
        if (originalFunction.returnType == null) return null

        if (!SingleAbstractMethodUtils.isSamAdapterNecessary(function)) return null // TODO: original or substitution?

        return MyFunctionDescriptor.create(function)
    }

    private class MyFunctionDescriptor(
            containingDeclaration: DeclarationDescriptor,
            original: SimpleFunctionDescriptor?,
            annotations: Annotations,
            name: Name,
            kind: CallableMemberDescriptor.Kind,
            sourceFunction: FunctionDescriptor
    ) : SamAdapterExtensionFunctionDescriptor, SimpleFunctionDescriptorImpl(containingDeclaration, original, annotations, name, kind, sourceFunction.source) {

        override var sourceFunction: FunctionDescriptor = sourceFunction
            private set

        private var toSourceFunctionTypeParameters: Map<TypeParameterDescriptor, TypeParameterDescriptor>? = null

        companion object {
            fun create(sourceFunction: FunctionDescriptor): MyFunctionDescriptor {
                val descriptor = MyFunctionDescriptor(sourceFunction.containingDeclaration,
                                                      null,
                                                      sourceFunction.annotations,
                                                      sourceFunction.name,
                                                      CallableMemberDescriptor.Kind.SYNTHESIZED,
                                                      sourceFunction)
                descriptor.sourceFunction = sourceFunction

                val sourceTypeParams = sourceFunction.typeParameters
                val ownerClass = sourceFunction.containingDeclaration as ClassDescriptor

                val typeParameters = ArrayList<TypeParameterDescriptor>(sourceTypeParams.size)
                val typeSubstitutor = DescriptorSubstitutor.substituteTypeParameters(sourceTypeParams, TypeSubstitution.EMPTY, descriptor, typeParameters)

                descriptor.toSourceFunctionTypeParameters = typeParameters.zip(sourceTypeParams).toMap()

                val returnType = typeSubstitutor.safeSubstitute(sourceFunction.returnType!!, Variance.INVARIANT)
                val valueParameters = SingleAbstractMethodUtils.createValueParametersForSamAdapter(sourceFunction, descriptor, typeSubstitutor)

                // TODO: check this
                val visibility = syntheticExtensionVisibility(sourceFunction)

                descriptor.initialize(null, ownerClass.thisAsReceiverParameter, typeParameters, valueParameters, returnType,
                                      sourceFunction.modality, visibility)

                descriptor.isOperator = sourceFunction.isOperator
                descriptor.isInfix = sourceFunction.isInfix

                return descriptor
            }
        }

        override fun hasStableParameterNames() = sourceFunction.hasStableParameterNames()
        override fun hasSynthesizedParameterNames() = sourceFunction.hasSynthesizedParameterNames()

        override fun createSubstitutedCopy(
                newOwner: DeclarationDescriptor,
                original: FunctionDescriptor?,
                kind: CallableMemberDescriptor.Kind,
                newName: Name?,
                preserveSource: Boolean
        ): MyFunctionDescriptor {
            return MyFunctionDescriptor(
                    containingDeclaration, original as SimpleFunctionDescriptor?, annotations, newName ?: name, kind, sourceFunction
            )
        }

        override fun doSubstitute(configuration: CopyConfiguration): FunctionDescriptor? {
            val descriptor = super.doSubstitute(configuration) as MyFunctionDescriptor? ?: return null
            val original = configuration.original
                           ?: throw UnsupportedOperationException("doSubstitute with no original should not be called for synthetic extension")

            original as MyFunctionDescriptor
            assert(original.original == original) { "original in doSubstitute should have no other original" }

            val substitutionMap = HashMap<TypeConstructor, TypeProjection>()
            for (typeParameter in original.typeParameters) {
                val typeProjection = configuration.originalSubstitutor.substitution[typeParameter.defaultType] ?: continue
                val sourceTypeParameter = original.toSourceFunctionTypeParameters!![typeParameter]!!
                substitutionMap[sourceTypeParameter.typeConstructor] = typeProjection
            }

            val sourceFunctionSubstitutor = TypeSubstitutor.create(substitutionMap)
            descriptor.sourceFunction = original.sourceFunction.substitute(sourceFunctionSubstitutor)

            return descriptor
        }

        // TODO: overrides
    }
}
