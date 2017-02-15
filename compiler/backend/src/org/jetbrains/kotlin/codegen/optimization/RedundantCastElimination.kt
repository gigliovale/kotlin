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

import org.jetbrains.kotlin.codegen.inline.ReifiedTypeInliner
import org.jetbrains.kotlin.codegen.optimization.common.OptimizationBasicInterpreter
import org.jetbrains.kotlin.codegen.optimization.fixStack.top
import org.jetbrains.kotlin.codegen.optimization.nullCheck.popReferenceValueBefore
import org.jetbrains.kotlin.codegen.optimization.transformer.MethodTransformer
import org.jetbrains.kotlin.resolve.jvm.AsmTypes
import org.jetbrains.kotlin.utils.addToStdlib.cast
import org.jetbrains.org.objectweb.asm.Opcodes
import org.jetbrains.org.objectweb.asm.Type
import org.jetbrains.org.objectweb.asm.tree.*

class RedundantCheckCastEliminationMethodTransformer : MethodTransformer() {
    override fun transform(internalClassName: String, methodNode: MethodNode) {
        while (runSingleStep(internalClassName, methodNode)) {}
    }

    private fun runSingleStep(internalClassName: String, methodNode: MethodNode): Boolean {
        val insns = methodNode.instructions.toArray()
        if (!insns.any { it.opcode == Opcodes.CHECKCAST || it.opcode == Opcodes.INSTANCEOF }) return false

        val redundantCheckCasts = ArrayList<TypeInsnNode>()
        val redundantInstanceOfs = ArrayList<TypeInsnNode>()

        val frames = analyze(internalClassName, methodNode, OptimizationBasicInterpreter())
        for (i in insns.indices) {
            val valueType = frames[i]?.top()?.type ?: continue
            val insn = insns[i]
            if (ReifiedTypeInliner.isOperationReifiedMarker(insn.previous)) continue

            if (insn is TypeInsnNode) {
                val insnType = Type.getObjectType(insn.desc)
                if (!isTrivialSubtype(insnType, valueType)) continue

                when (insn.opcode) {
                    Opcodes.CHECKCAST -> redundantCheckCasts.add(insn)
                    Opcodes.INSTANCEOF -> redundantInstanceOfs.add(insn)
                }
            }
        }

        redundantCheckCasts.forEach { methodNode.instructions.remove(it) }
        redundantInstanceOfs.forEach { methodNode.instructions.removeAlwaysTrueInstanceOf(it) }

        val dceResult = DeadCodeEliminationMethodTransformer().transformWithResult(internalClassName, methodNode)

        return redundantCheckCasts.isNotEmpty() || redundantInstanceOfs.isNotEmpty() || dceResult.hasRemovedAnything()
    }

    private fun InsnList.removeAlwaysTrueInstanceOf(insn: TypeInsnNode) {
        popReferenceValueBefore(insn)
        val next = insn.next
        val nextOpcode = next.opcode
        when (nextOpcode) {
            Opcodes.IFEQ -> {
                remove(insn)
                remove(next)
            }
            Opcodes.IFNE -> {
                remove(insn)
                set(next, JumpInsnNode(Opcodes.GOTO, next.cast<JumpInsnNode>().label))
            }
            else -> {
                set(insn, InsnNode(Opcodes.ICONST_1))
            }
        }
    }

    private fun isTrivialSubtype(superType: Type, subType: Type) =
            superType == AsmTypes.OBJECT_TYPE ||
            superType == subType
}