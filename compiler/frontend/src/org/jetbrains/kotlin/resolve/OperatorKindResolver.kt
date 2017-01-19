/*
 * Copyright 2010-2017 JetBrains s.r.o.
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

package org.jetbrains.kotlin.resolve

import org.jetbrains.kotlin.descriptors.FunctionDescriptor

enum class OperatorKind {
    /// Kotlin function with explicit 'operator' modifier.
    OPERATOR,
    /// Java or some other method with inferred 'operator' modifier.
    INFERRED_OPERATOR,
    /// Not an operator.
    NON_OPERATOR
}

interface OperatorKindResolver {
    fun getOperatorKind(descriptor: FunctionDescriptor): OperatorKind

    object DEFAULT : OperatorKindResolver {
        override fun getOperatorKind(descriptor: FunctionDescriptor): OperatorKind =
                if (descriptor.isOperator) OperatorKind.OPERATOR else OperatorKind.NON_OPERATOR
    }
}

fun OperatorKindResolver.isInferredOperator(descriptor: FunctionDescriptor) =
        getOperatorKind(descriptor) == OperatorKind.INFERRED_OPERATOR

fun OperatorKindResolver.isExplicitOperator(descriptor: FunctionDescriptor) =
        getOperatorKind(descriptor) == OperatorKind.OPERATOR