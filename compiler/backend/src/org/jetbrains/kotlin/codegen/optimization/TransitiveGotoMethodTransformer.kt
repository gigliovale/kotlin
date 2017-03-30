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

import org.jetbrains.kotlin.codegen.optimization.common.asSequence
import org.jetbrains.kotlin.codegen.optimization.transformer.MethodTransformer
import org.jetbrains.kotlin.utils.SmartSet
import org.jetbrains.org.objectweb.asm.Opcodes
import org.jetbrains.org.objectweb.asm.tree.AbstractInsnNode
import org.jetbrains.org.objectweb.asm.tree.JumpInsnNode
import org.jetbrains.org.objectweb.asm.tree.LabelNode
import org.jetbrains.org.objectweb.asm.tree.MethodNode

class TransitiveGotoMethodTransformer : MethodTransformer() {
    override fun transform(internalClassName: String, methodNode: MethodNode) {
        methodNode.instructions.asSequence().forEach { insn ->
            if (insn is JumpInsnNode) {
                insn.label = computeTargetLabel(insn)
            }
        }
    }

    private fun computeTargetLabel(insn: JumpInsnNode): LabelNode =
            computeTargetLabelRec(insn, SmartSet.create<AbstractInsnNode>())

    private fun computeTargetLabelRec(current: JumpInsnNode, visited: MutableSet<AbstractInsnNode>): LabelNode {
        val jumpsTo = current.label.next
        return when {
            jumpsTo == null ->
                throw AssertionError("Jump to undefined label")
            jumpsTo in visited ->
                throw AssertionError("Cyclic GOTOs generated")
            jumpsTo is JumpInsnNode && jumpsTo.opcode == Opcodes.GOTO ->
                computeTargetLabelRec(jumpsTo, visited.apply { add(current) })
            else ->
                current.label
        }
    }
}