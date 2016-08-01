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

package org.jetbrains.kotlin.resolve.calls.components

import org.jetbrains.kotlin.builtins.KotlinBuiltIns
import org.jetbrains.kotlin.resolve.calls.CallContextComponents
import org.jetbrains.kotlin.resolve.calls.inference.components.SimpleConstraintSystemImpl
import org.jetbrains.kotlin.resolve.calls.model.*
import org.jetbrains.kotlin.resolve.calls.results.FlatSignature
import org.jetbrains.kotlin.resolve.calls.results.FlatSignature.Companion.argumentValueType
import org.jetbrains.kotlin.resolve.calls.results.OverloadingConflictResolver
import org.jetbrains.kotlin.resolve.calls.results.TypeSpecificityComparator
import org.jetbrains.kotlin.types.KotlinType
import java.util.*

class NewOverloadingConflictResolver(
        builtIns: KotlinBuiltIns,
        specificityComparator: TypeSpecificityComparator,
        isDescriptorFromSourcePredicate: IsDescriptorFromSourcePredicate,
        callComponents: CallContextComponents
) : OverloadingConflictResolver<NewResolutionCandidate>(
        builtIns,
        specificityComparator,
        {
            (it as? VariableAsFunctionResolutionCandidate)?.invokeCandidate?.descriptorWithFreshTypes ?:
            (it as SimpleResolutionCandidate).descriptorWithFreshTypes
        },
        { SimpleConstraintSystemImpl(callComponents) },
        Companion::createFlatSignature,
        { (it as? VariableAsFunctionResolutionCandidate)?.resolvedVariable },
        isDescriptorFromSourcePredicate
) {

    companion object {
        private fun createFlatSignature(candidate: NewResolutionCandidate): FlatSignature<NewResolutionCandidate> {
            val simpleCandidate = (candidate as? VariableAsFunctionResolutionCandidate)?.invokeCandidate ?: (candidate as SimpleResolutionCandidate)

            val originalDescriptor = simpleCandidate.descriptorWithFreshTypes.original
            val originalValueParameters = originalDescriptor.valueParameters

            var numDefaults = 0
            val valueArgumentToParameterType = HashMap<CallArgument, KotlinType>()
            for ((valueParameter, resolvedValueArgument) in simpleCandidate.argumentMappingByOriginal) {
                if (resolvedValueArgument is ResolvedCallArgument.DefaultArgument) {
                    numDefaults++
                }
                else {
                    val originalValueParameter = originalValueParameters[valueParameter.index]
                    val parameterType = originalValueParameter.argumentValueType
                    for (valueArgument in resolvedValueArgument.arguments) {
                        valueArgumentToParameterType[valueArgument] = parameterType
                    }
                }
            }

            return FlatSignature.create(candidate,
                                        originalDescriptor,
                                        numDefaults,
                                        listOfNotNull(originalDescriptor.extensionReceiverParameter?.type) +
                                        simpleCandidate.astCall.argumentsInParenthesis.map { valueArgumentToParameterType[it] } +
                                        listOfNotNull(simpleCandidate.astCall.externalArgument?.let { valueArgumentToParameterType[it] })
            )

        }
    }
}



