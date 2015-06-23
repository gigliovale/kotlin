/*
 * Copyright 2010-2015 JetBrains s.r.o.
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

import org.jetbrains.kotlin.codegen.optimization.common.OptimizationBasicInterpreter
import org.jetbrains.kotlin.codegen.optimization.transformer.MethodTransformer
import org.jetbrains.kotlin.codegen.pseudoInsns.PseudoInsnOpcode
import org.jetbrains.kotlin.codegen.pseudoInsns.parseOrNull
import org.jetbrains.org.objectweb.asm.Opcodes
import org.jetbrains.org.objectweb.asm.tree.*
import org.jetbrains.org.objectweb.asm.tree.analysis.*
import java.util.*

class ClearStackPseudoInsnTransformer : MethodTransformer() {
    public override fun transform(internalClassName: String, methodNode: MethodNode) {
        val analyzer = StackDepthAnalyzer()
        analyzer.analyze(internalClassName, methodNode)

        val actions = arrayListOf<ReplaceInsnWithPops>()

        val iter = methodNode.instructions.iterator()
        while (iter.hasNext()) {
            val insnIndex = iter.nextIndex()
            val insn = iter.next()
            val pseudo = parseOrNull(insn)
            if (pseudo != null && pseudo.opcode == PseudoInsnOpcode.CLEAR_STACK) {
                val frame = analyzer.getFrames()[insnIndex]
                val stackSize = frame?.getStackSize() ?: 0
                actions.add(ReplaceInsnWithPops(insn, stackSize))
            }
        }
        for (action in actions) {
            action.perform(methodNode)
        }
    }

    private class ReplaceInsnWithPops(val insn: AbstractInsnNode, val numPops: Int) {
        fun perform(methodNode: MethodNode) {
            repeat(numPops) {
                methodNode.instructions.insert(insn, InsnNode(Opcodes.POP))
            }
            methodNode.instructions.remove(insn)
        }
    }

    private class StackDepthAnalyzer : Analyzer<BasicValue>(OptimizationBasicInterpreter()) {
        var methodNode: MethodNode? = null

        override fun init(owner: String?, m: MethodNode?) {
            methodNode = m
        }

        override fun newFrame(maxLocals: Int, maxStack: Int): Frame<BasicValue> =
                StackDepthAnalyzerFrame(maxLocals, maxStack)

        override fun newFrame(src: Frame<out BasicValue>): Frame<BasicValue> {
            val frame = StackDepthAnalyzerFrame(src.getLocals(), src.getMaxStackSize())
            frame.init(src)
            return frame
        }
    }

    private class StackDepthAnalyzerFrame(maxLocals: Int, maxStack: Int) : Frame<BasicValue>(maxLocals, maxStack) {
        override fun merge(frame: Frame<out BasicValue>, interpreter: Interpreter<BasicValue>): Boolean {
            val mergedStackSize = Math.min(getStackSize(), frame.getStackSize())

            var changes = false

            if (mergeUsing(getLocals(), interpreter, { getLocal(it) }, { frame.getLocal(it) }, { i, v -> setLocal(i, v) })) {
                changes = true
            }

            // Frame has no setStack(i), so we do it manually.
            val stackValues = Array(mergedStackSize) { getStack(it) }
            if (mergeUsing(mergedStackSize, interpreter, { stackValues[it] }, { frame.getStack(it) }, { i, v -> stackValues[i] = v })) {
                changes = true
            }
            clearStack()
            stackValues.forEach { push(it) }

            return changes
        }
    }

}

private inline fun <T : Value> mergeUsing(
        n: Int,
        interpreter: Interpreter<T>,
        getOld: (Int) -> T,
        getIncoming: (Int) -> T,
        setMerged: (Int, T) -> Unit
): Boolean {
    var changes = false
    for (i in 0 .. n - 1) {
        val oldValue = getOld(i)
        val newValue = interpreter.merge(oldValue, getIncoming(i))
        if (oldValue != newValue) {
            setMerged(i, newValue)
            changes = true
        }
    }
    return changes
}

private inline fun InsnList.forEachIndexed(operation: (Int, AbstractInsnNode) -> Unit) {
    this.forEachIndexed(0, operation)
}

private inline fun InsnList.forEachIndexed(from: Int, operation: (Int, AbstractInsnNode) -> Unit) {
    val iter = this.iterator(from)
    while (iter.hasNext()) {
        val insnIndex = iter.nextIndex()
        val insn = iter.next()
        operation(insnIndex, insn)
    }
}

