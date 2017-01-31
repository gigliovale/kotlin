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

import org.jetbrains.kotlin.builtins.PrimitiveType
import org.jetbrains.kotlin.codegen.optimization.common.removeEmptyCatchBlocks
import org.jetbrains.kotlin.codegen.optimization.common.removeUnusedLocalVariables
import org.jetbrains.kotlin.codegen.optimization.fixStack.peek
import org.jetbrains.kotlin.codegen.optimization.fixStack.top
import org.jetbrains.kotlin.codegen.optimization.transformer.MethodTransformer
import org.jetbrains.kotlin.resolve.jvm.AsmTypes
import org.jetbrains.kotlin.utils.addToStdlib.safeAs
import org.jetbrains.org.objectweb.asm.Opcodes
import org.jetbrains.org.objectweb.asm.Type
import org.jetbrains.org.objectweb.asm.tree.*
import org.jetbrains.org.objectweb.asm.tree.analysis.BasicValue
import org.jetbrains.org.objectweb.asm.tree.analysis.Frame

class CapturedVarsOptimizationMethodTransformer : MethodTransformer() {
    override fun transform(internalClassName: String, methodNode: MethodNode) {
        Transformer(internalClassName, methodNode).run()
    }

    class RefValueDescriptor(val newInsn: TypeInsnNode, val refType: Type, val valueType: Type) : ReferenceValueDescriptor {
        var hazard: ReferenceValueHazard? = null

        var initCallInsn: MethodInsnNode? = null
        var localVar: LocalVariableNode? = null
        var localVarIndex = -1
        val astoreInsns: MutableCollection<VarInsnNode> = LinkedHashSet()
        val aloadInsns: MutableCollection<VarInsnNode> = LinkedHashSet()
        val stackInsns: MutableCollection<AbstractInsnNode> = LinkedHashSet()
        val getFieldInsns: MutableCollection<FieldInsnNode> = LinkedHashSet()
        val putFieldInsns: MutableCollection<FieldInsnNode> = LinkedHashSet()

        fun canRewrite(): Boolean =
                !hasHazard &&
                initCallInsn != null &&
                localVar != null &&
                localVarIndex >= 0

        val hasHazard: Boolean get() = hazard != null

        override fun markAsTainted() {
            hazard = Hazard.MERGED
        }
    }

    enum class Hazard : ReferenceValueHazard {
        UNEXPECTED_USAGE,
        MERGED,
        UNEXPECTED_STACK_OP,
        LOCAL_VAR_CONFLICT,
        STRANGE_INITIALIZATION
    }

    class Transformer(private val internalClassName: String, private val methodNode: MethodNode) {
        private val refValues = ArrayList<RefValueDescriptor>()
        private val refValuesByNewInsn = LinkedHashMap<TypeInsnNode, RefValueDescriptor>()
        private val insns = methodNode.instructions.toArray()
        private lateinit var frames: Array<out Frame<BasicValue>?>

        val hasRewritableRefValues: Boolean
            get() = refValues.isNotEmpty()

        fun run() {
            createRefValues()
            if (!hasRewritableRefValues) return

            analyze()
            if (!hasRewritableRefValues) return

            rewrite()
        }

        private fun AbstractInsnNode.getIndex() = methodNode.instructions.indexOf(this)

        private fun createRefValues() {
            for (insn in insns) {
                if (insn.opcode == Opcodes.NEW && insn is TypeInsnNode) {
                    val type = Type.getObjectType(insn.desc)
                    if (AsmTypes.isSharedVarType(type)) {
                        val valueType = REF_TYPE_TO_ELEMENT_TYPE[type.internalName] ?: continue
                        val refValue = RefValueDescriptor(insn, type, valueType)
                        refValues.add(refValue)
                        refValuesByNewInsn[insn] = refValue
                    }
                }
            }
        }

        inner class Interpreter : ReferenceTrackingInterpreter<RefValueDescriptor>() {
            override fun newOperation(insn: AbstractInsnNode): BasicValue =
                    refValuesByNewInsn[insn]?.let { descriptor ->
                        ProperReferenceValue(descriptor.refType, descriptor)
                    }
                    ?: super.newOperation(insn)

            override fun processRefValueUsage(value: ReferenceValue<*>, insn: AbstractInsnNode, position: Int) {
                @Suppress("UNCHECKED_CAST")
                for (descriptor in value.descriptors as Set<RefValueDescriptor>) {
                    when {
                        insn.opcode == Opcodes.ALOAD ->
                            descriptor.aloadInsns.add(insn as VarInsnNode)
                        insn.opcode == Opcodes.ASTORE ->
                            descriptor.astoreInsns.add(insn as VarInsnNode)
                        insn.opcode == Opcodes.GETFIELD && insn is FieldInsnNode && insn.name == REF_ELEMENT_FIELD && position == 0 ->
                            descriptor.getFieldInsns.add(insn)
                        insn.opcode == Opcodes.PUTFIELD && insn is FieldInsnNode && insn.name == REF_ELEMENT_FIELD && position == 0 ->
                            descriptor.putFieldInsns.add(insn)
                        insn.opcode == Opcodes.INVOKESPECIAL && insn is MethodInsnNode && insn.name == INIT_METHOD_NAME && position == 0 ->
                            if (descriptor.initCallInsn != null && descriptor.initCallInsn != insn)
                                descriptor.hazard = CapturedVarsOptimizationMethodTransformer.Hazard.UNEXPECTED_USAGE
                            else
                                descriptor.initCallInsn = insn
                        insn.opcode == Opcodes.DUP ->
                            descriptor.stackInsns.add(insn)
                        else ->
                            descriptor.hazard = CapturedVarsOptimizationMethodTransformer.Hazard.UNEXPECTED_USAGE
                    }
                }

            }
        }

        private fun analyze() {
            frames = MethodTransformer.analyze(internalClassName, methodNode, Interpreter())
            trackPops()
            assignLocalVars()

            refValues.removeAll { !it.canRewrite() }
        }


        private fun trackPops() {
            for (i in insns.indices) {
                val frame = frames[i] ?: continue
                val insn = insns[i]

                when (insn.opcode) {
                    Opcodes.POP -> {
                        frame.top()?.getDescriptor()?.run { stackInsns.add(insn) }
                    }
                    Opcodes.POP2 -> {
                        val top = frame.top()
                        if (top?.size == 1) {
                            top.getDescriptor()?.hazard = Hazard.UNEXPECTED_STACK_OP
                            frame.peek(1)?.getDescriptor()?.hazard = Hazard.UNEXPECTED_STACK_OP
                        }
                    }
                }
            }
        }

        private fun BasicValue.getDescriptor() =
                safeAs<ProperReferenceValue<*>>()?.descriptor?.safeAs<RefValueDescriptor>()

        private fun assignLocalVars() {
            for (localVar in methodNode.localVariables) {
                val type = Type.getType(localVar.desc)
                if (!AsmTypes.isSharedVarType(type)) continue

                val startFrame = frames[localVar.start.getIndex()] ?: continue

                val refValue = startFrame.getLocal(localVar.index) as? ProperReferenceValue<*> ?: continue
                val descriptor = refValue.descriptor as? RefValueDescriptor ?: continue

                if (descriptor.hasHazard) continue

                if (descriptor.localVar == null) {
                    descriptor.localVar = localVar
                }
                else {
                    descriptor.hazard = Hazard.LOCAL_VAR_CONFLICT
                }
            }

            for (refValue in refValues) {
                if (refValue.hasHazard) continue
                val localVar = refValue.localVar ?: continue

                if (refValue.valueType.size != 1) {
                    refValue.localVarIndex = methodNode.maxLocals
                    methodNode.maxLocals += 2
                    localVar.index = refValue.localVarIndex
                }
                else {
                    refValue.localVarIndex = localVar.index
                }

                val startIndex = localVar.start.getIndex()
                val initFieldInsns = refValue.putFieldInsns.filter { it.getIndex() < startIndex }
                if (initFieldInsns.size != 1) {
                    refValue.hazard = Hazard.STRANGE_INITIALIZATION
                    continue
                }
            }
        }

        private fun rewrite() {
            for (refValue in refValues) {
                if (!refValue.canRewrite()) continue

                rewriteRefValue(refValue)
            }

            methodNode.removeEmptyCatchBlocks()
            methodNode.removeUnusedLocalVariables()
        }

        private fun rewriteRefValue(refValue: RefValueDescriptor) {
            methodNode.instructions.run {
                refValue.localVar!!.let {
                    assert(it.signature == null) { "Non-null signature for local var $it" }
                    it.desc = refValue.valueType.descriptor
                }

                remove(refValue.newInsn)
                remove(refValue.initCallInsn!!)
                refValue.stackInsns.forEach { remove(it) }
                refValue.aloadInsns.forEach { remove(it) }
                refValue.astoreInsns.forEach { remove(it) }

                refValue.getFieldInsns.forEach {
                    insert(it, VarInsnNode(refValue.valueType.getOpcode(Opcodes.ILOAD), refValue.localVarIndex))
                    remove(it)
                }

                refValue.putFieldInsns.forEach {
                    insert(it, VarInsnNode(refValue.valueType.getOpcode(Opcodes.ISTORE), refValue.localVarIndex))
                    remove(it)
                }
            }
        }
    }
}

internal const val REF_ELEMENT_FIELD = "element"
internal const val INIT_METHOD_NAME = "<init>"

internal val REF_TYPE_TO_ELEMENT_TYPE = HashMap<String, Type>().apply {
    put(AsmTypes.OBJECT_REF_TYPE.internalName, AsmTypes.OBJECT_TYPE)
    PrimitiveType.values().forEach {
        put(AsmTypes.sharedTypeForPrimitive(it).internalName, AsmTypes.valueTypeForPrimitive(it))
    }
}
