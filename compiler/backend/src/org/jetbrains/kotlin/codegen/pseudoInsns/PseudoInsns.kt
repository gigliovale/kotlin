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

package org.jetbrains.kotlin.codegen.pseudoInsns

import org.jetbrains.org.objectweb.asm.Opcodes
import org.jetbrains.org.objectweb.asm.commons.InstructionAdapter
import org.jetbrains.org.objectweb.asm.tree.AbstractInsnNode
import org.jetbrains.org.objectweb.asm.tree.InsnList
import org.jetbrains.org.objectweb.asm.tree.MethodInsnNode
import kotlin.platform.platformStatic

public val PSEUDO_INSN_CALL_OWNER: String = "kotlin.jvm.\$PseudoInsn"
public val PSEUDO_INSN_PARTS_SEPARATOR: String = ":"

public enum class PseudoInsnOpcode {
    CLEAR_STACK
    ;

    internal var singletonInsn: PseudoInsn? = null

    public fun insnOf(): PseudoInsn {
        if (singletonInsn != null) {
            return singletonInsn!!
        }
        else {
            throw IllegalArgumentException("$this is not a zero arguments instruction")
        }
    }

    public fun insnOf(args: List<String>): PseudoInsn {
        if (args.isEmpty() && singletonInsn != null) {
            return singletonInsn!!
        }
        else {
            return PseudoInsn(this, args)
        }
    }

    companion object {
        init {
            CLEAR_STACK.singletonInsn = PseudoInsn(CLEAR_STACK, emptyList())
        }
    }
}

public data class PseudoInsn(public val opcode: PseudoInsnOpcode, public val args: List<String>) {
    public val encodedMethodName: String =
            if (args.isEmpty())
                opcode.toString()
            else
                opcode.toString() + PSEUDO_INSN_PARTS_SEPARATOR + args.join(PSEUDO_INSN_PARTS_SEPARATOR)

    public fun emit(iv: InstructionAdapter) {
        iv.invokestatic(PSEUDO_INSN_CALL_OWNER, encodedMethodName, "()V", false)
    }
}

public fun parseOrNull(insn: AbstractInsnNode): PseudoInsn? =
        if (insn is MethodInsnNode && insn.getOpcode() == Opcodes.INVOKESTATIC && insn.owner == PSEUDO_INSN_CALL_OWNER)
            parseParts(insn.name.splitBy(PSEUDO_INSN_PARTS_SEPARATOR))
        else null

private fun parseParts(parts: List<String>): PseudoInsn? {
    try {
        val opcode = PseudoInsnOpcode.valueOf(parts[0])
        if (opcode.singletonInsn != null) {
            return opcode.singletonInsn
        }
        val args = parts.subList(1, parts.size())
        return PseudoInsn(opcode, args)
    }
    catch (e: IllegalArgumentException) {
        return null
    }
}
