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

package org.jetbrains.kotlin.codegen.optimization.captured

import org.jetbrains.kotlin.codegen.optimization.common.StrictBasicValue
import org.jetbrains.org.objectweb.asm.Type

interface ReferenceValueHazard

interface ReferenceValueDescriptor {
    fun markAsTainted()
}

abstract class ReferenceValue<out VD : ReferenceValueDescriptor>(type: Type): StrictBasicValue(type) {
    abstract val descriptors: Set<VD>
}

class ProperReferenceValue<out VD : ReferenceValueDescriptor>(type: Type, val descriptor: VD) : ReferenceValue<VD>(type) {
    override val descriptors: Set<VD>
        get() = setOf(descriptor)

    override fun equals(other: Any?): Boolean =
            other === this ||
            other is ProperReferenceValue<*> && other.descriptor == this.descriptor

    override fun hashCode(): Int =
            descriptor.hashCode()

    override fun toString(): String =
            "[$descriptor]"
}

class TaintedReferenceValue<out VD : ReferenceValueDescriptor>(type: Type, override val descriptors: Set<VD>) : ReferenceValue<VD>(type) {
    override fun equals(other: Any?): Boolean =
            other === this ||
            other is TaintedReferenceValue<*> && other.descriptors == this.descriptors

    override fun hashCode(): Int =
            descriptors.hashCode()

    override fun toString(): String =
            "!$descriptors"
}
