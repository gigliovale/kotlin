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

package org.jetbrains.kotlin.codegen.optimization

import org.jetbrains.kotlin.codegen.optimization.transformer.MethodTransformer
import org.jetbrains.org.objectweb.asm.tree.JumpInsnNode
import org.jetbrains.org.objectweb.asm.tree.MethodNode

abstract class IterativeMethodTransformer : MethodTransformer() {
    protected var maxIterations = -1

    fun maxIterations(value: Int) =
            apply { maxIterations = value }

    protected fun shouldStop(step: Int) =
            maxIterations in 0..step
}


open class RepeatUntilFixPointMethodTransformer(vararg val transformers: MethodTransformer) : IterativeMethodTransformer() {
    override fun transform(internalClassName: String, methodNode: MethodNode) {
        if (maxIterations == 0) return
        var hashBefore = methodNode.contentHash()
        var step = 0
        while (true) {
            transformers.forEach {
                it.transform(internalClassName, methodNode)
            }
            if (shouldStop(++step)) break
            val hashAfter = methodNode.contentHash()
            if (hashAfter == hashBefore) break
            hashBefore = hashAfter
        }
    }
}


abstract class RepeatUntilNoChangesMethodTransformer : IterativeMethodTransformer() {
    override fun transform(internalClassName: String, methodNode: MethodNode) {
        if (maxIterations == 0) return
        var step = 0
        while (doTransform(internalClassName, methodNode)) {
            if (shouldStop(++step)) break
        }
    }

    abstract protected fun doTransform(internalClassName: String, methodNode: MethodNode): Boolean
}


open class CompositeMethodTransformer(vararg val transformers: MethodTransformer) : MethodTransformer() {
    override fun transform(internalClassName: String, methodNode: MethodNode) {
        transformers.forEach {
            it.transform(internalClassName, methodNode)
        }
    }
}


fun MethodNode.contentHash(): Int {
    val insns = instructions.toArray()
    var result = 0
    for (insn in insns) {
        result = result * 31 + insn.opcode
        if (insn is JumpInsnNode) {
            result = result * 31 + instructions.indexOf(insn.label)
        }
    }
    return result
}

