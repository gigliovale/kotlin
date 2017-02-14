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

package org.jetbrains.kotlin.codegen

import org.jetbrains.annotations.NotNull
import org.jetbrains.org.objectweb.asm.Type
import org.jetbrains.org.objectweb.asm.commons.InstructionAdapter

abstract class StackValueBase {
    open fun put(type: Type, v: InstructionAdapter) {
        put(type, v, false)
    }

    abstract fun put(type: Type, v: InstructionAdapter, skipReceiver: Boolean)

    open fun putWithArguments(type: Type, v: InstructionAdapter, arguments: LazyArguments?) {
        arguments?.generateAllDirectlyTo(v)
        put(type, v, arguments != null)
    }

    open fun putLazy(type: Type, v: InstructionAdapter, arguments: LazyArguments?): StackValue {
        return OperationStackValue(type) {
            putWithArguments(type, v, arguments)
        }
    }

    open fun putReceiver(arguments: LazyArguments, isRead: Boolean) {

    }

    //abstract fun generate(v: InstructionAdapter)
}

fun StackValue.put(v: InstructionAdapter) {
    this.put(type, v)
}



//class ReceiverAndArgs constructor(val instructions: List<StackValue>) : StackValueBase() {
//
//    val size = instructions.fold(0) { acc, value -> acc + value.getSizeOnStack()}
//    val canHaveSideEffects = instructions.any {it.canHaveSideEffects()}
//
//    constructor(receiver: StackValue, argument: StackValue): this(listOf(receiver, argument))
//    constructor(receiver: StackValue): this(listOf(receiver))
//
//    override fun put(type: Type, v: InstructionAdapter, skipReceiver: Boolean) {
//        generate(v)
//    }
//
//    override fun generate(v: InstructionAdapter) {
//        for (instruction in instructions) {
//            instruction.put(instruction.type, v)
//        }
//    }
//
//    override fun getSizeOnStack(): Int {
//        return size
//    }
//
//    override fun canHaveSideEffects(): Boolean = canHaveSideEffects
//}
//
//
//class InstructionStackValue(type: Type, val read: Instruction, val write: Instruction) : StackValue(type) {
//
//    override fun putSelector(type: Type, v: InstructionAdapter) {
//        read.generate(v)
//    }
//
//    override fun storeSelector(topOfStackType: Type, v: InstructionAdapter) {
//        write.generate(v)
//    }
//}