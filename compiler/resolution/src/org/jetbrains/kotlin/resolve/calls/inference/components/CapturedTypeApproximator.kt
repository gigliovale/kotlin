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

package org.jetbrains.kotlin.resolve.calls.inference

import org.jetbrains.kotlin.types.*
import org.jetbrains.kotlin.types.checker.NewCapturedType
import org.jetbrains.kotlin.types.checker.NewKotlinTypeChecker
import org.jetbrains.kotlin.types.checker.intersectTypes
import org.jetbrains.kotlin.types.typeUtil.builtIns
import org.jetbrains.kotlin.types.typeUtil.isNothing
import org.jetbrains.kotlin.types.typeUtil.isNullableAny
import org.jetbrains.kotlin.utils.SmartList


class CapturedTypeApproximator {

    data class ApproximationBounds<out T : UnwrappedType>(
            val lower: T,
            val upper: T
    )

    fun safeApproximateCapturedTypes(type: UnwrappedType, predicate: (NewCapturedType) -> Boolean) =
            approximateCapturedTypes(type, predicate) ?: ApproximationBounds(type, type)

    // null if there is no captured types
    private fun approximateCapturedTypes(
            type: UnwrappedType,
            predicate: (NewCapturedType) -> Boolean  // if predicate(capturedType) is false, then this type considered as single classifier type
    ): ApproximationBounds<UnwrappedType>? {
        // dynamic and RawType not have captured type inside => null will be returned

        return when (type) {
            is FlexibleType -> {
                val boundsForFlexibleLower = approximateCapturedTypes(type.lowerBound, predicate)
                val boundsForFlexibleUpper = approximateCapturedTypes(type.upperBound, predicate)

                if (boundsForFlexibleLower == null && boundsForFlexibleUpper == null) return null

                ApproximationBounds(
                        KotlinTypeFactory.flexibleType(boundsForFlexibleLower?.lower ?: type.lowerBound,
                                                       boundsForFlexibleUpper?.lower ?: type.upperBound),

                        KotlinTypeFactory.flexibleType(boundsForFlexibleLower?.upper ?: type.lowerBound,
                                                       boundsForFlexibleUpper?.upper ?: type.upperBound))
            }
            is SimpleType -> approximateCapturedTypes(type, predicate)
        }
    }

    private fun approximateCapturedTypes(
            type: SimpleType,
            predicate: (NewCapturedType) -> Boolean
    ): ApproximationBounds<SimpleType>? {
        val builtIns = type.builtIns

        if (type is NewCapturedType) {
            if (!predicate(type)) return null

            val supertypes = type.constructor.supertypes
            val upper = if (supertypes.isNotEmpty()) {
                intersectTypes(supertypes).upperIfFlexible() // supertypes can be flexible
            }
            else {
                builtIns.nullableAnyType
            }
            // todo review approximation
            val lower = type.lowerType?.lowerIfFlexible() ?: builtIns.nothingType

            return if (!type.isMarkedNullable) {
                ApproximationBounds(lower, upper)
            }
            else {
                ApproximationBounds(lower.makeNullableAsSpecified(true), upper.makeNullableAsSpecified(true))
            }
        }

        val arguments = type.arguments
        if (arguments.isEmpty() || arguments.size != type.constructor.parameters.size) {
            return null
        }

        val approximatedArguments = Array(arguments.size) l@ {
            val typeProjection = arguments[it]
            if (typeProjection.isStarProjection) return@l null

            approximateCapturedTypes(typeProjection.type.unwrap(), predicate)
        }

        if (approximatedArguments.all { it == null }) return null

        val lowerArguments = SmartList<TypeProjection>()
        val upperArguments = SmartList<TypeProjection>()
        var lowerIsConsistent = true
        for ((index, typeProjection) in arguments.withIndex()) {
            val typeParameter = type.constructor.parameters[index]
            val approximatedType = approximatedArguments[index]

            // note: approximatedType == null => !typeProjection.isStarProjection
            if (approximatedType == null) {
                lowerArguments.add(typeProjection)
                upperArguments.add(typeProjection)
                continue
            }

            val effectiveVariance = NewKotlinTypeChecker.effectiveVariance(typeParameter.variance, typeProjection.projectionKind)
            if (effectiveVariance == null) { // actually it is error type
                lowerIsConsistent = false
                upperArguments.add(StarProjectionImpl(typeParameter))
                continue
            }

            when (effectiveVariance) {
                Variance.OUT_VARIANCE -> {
                    upperArguments.add(TypeProjectionImpl(Variance.OUT_VARIANCE, approximatedType.upper))
                    lowerArguments.add(TypeProjectionImpl(Variance.OUT_VARIANCE, approximatedType.lower))
                }
                Variance.IN_VARIANCE -> {
                    upperArguments.add(TypeProjectionImpl(Variance.OUT_VARIANCE, approximatedType.lower))
                    lowerArguments.add(TypeProjectionImpl(Variance.OUT_VARIANCE, approximatedType.upper))
                }

            /**
             * Missing case here: both ApproximationBounds is not trivial.
             * Example: Inv<C> where C <: Number and C >: Int.
             * Now we approximate type `Inv<C>` into `Inv<out Number>`.
             * It is correct, but we can do better: `Inv<out Number> & Inv<in Int>`.
             *
             * But there is another problem. What we should do for type `Inv2<C, C>`?
             * Proposal: Inv2<out NUmber, out Number> & ... (4 types).
             * Yea... so many types. But such cases is rare... yet.
             */
                Variance.INVARIANT -> {
                    val upperProjection = if (approximatedType.upper.isTrivialUpper() && !approximatedType.lower.isTrivialLower()) {
                        TypeProjectionImpl(Variance.IN_VARIANCE, approximatedType.lower)
                    }
                    else {
                        // todo missing case: both not trivial
                        TypeProjectionImpl(Variance.OUT_VARIANCE, approximatedType.upper)
                    }
                    upperArguments.add(upperProjection)
                    lowerIsConsistent = false
                }
            }
        }

        val lower = if (lowerIsConsistent) type.replace(lowerArguments) else builtIns.nothingType
        val upper = type.replace(upperArguments)

        return ApproximationBounds(lower, upper)
    }

    // Any? or Any!
    private fun UnwrappedType.isTrivialLower() = upperIfFlexible().isNullableAny()

    // Nothing or Nothing!
    private fun UnwrappedType.isTrivialUpper() = lowerIfFlexible().isNothing()
}