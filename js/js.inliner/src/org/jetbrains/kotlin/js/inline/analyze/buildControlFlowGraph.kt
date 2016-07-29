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

package org.jetbrains.kotlin.js.inline.analyze

import com.google.dart.compiler.backend.js.ast.*
import org.jetbrains.kotlin.js.translate.utils.jsAstUtils.array
import org.jetbrains.kotlin.js.translate.utils.jsAstUtils.index
import org.jetbrains.kotlin.js.translate.utils.jsAstUtils.test
import kotlin.reflect.KMutableProperty0

fun JsFunction.buildControlFlowGraph(): BasicBlock {
    val visitor = ControlFlowBuildVisitor()
    body.accept(visitor)

    val start = visitor.start
    val end = BasicBlock()
    end.nodes += this
    visitor.currentBlock.connectTo(end)
    for (protectedBlock in visitor.createdBlocks.filter { it in visitor.terminalBlocks }) {
        protectedBlock.finallyBlock = end
    }

    removeUnreachableBlocks(start)
    removeEmptyBlocks(start)
    return start
}

private class ControlFlowBuildVisitor() : JsVisitor() {
    private val breakTargets = mutableMapOf<JsName, BasicBlock>()
    private val continueTargets = mutableMapOf<JsName, BasicBlock>()
    private var currentBreakTarget: BasicBlock? = null
    private var currentContinueTarget: BasicBlock? = null
    val createdBlocks = mutableSetOf<BasicBlock>()
    val terminalBlocks = mutableSetOf<BasicBlock>()
    val start = createBasicBlock()
    var currentBlock = start

    override fun visitArrayAccess(x: JsArrayAccess) {
        accept(x.array)
        accept(x.index)
        currentBlock.nodes += x
    }

    override fun visitArray(x: JsArrayLiteral) {
        x.expressions.forEach { accept(it) }
        currentBlock.nodes += x
    }

    override fun visitBinaryExpression(x: JsBinaryOperation) {
        when (x.operator) {
            JsBinaryOperator.AND,
            JsBinaryOperator.OR -> {
                accept(x.arg1)
                currentBlock.nodes += x
                val thenBlock = enterBasicBlock {
                    accept(x.arg2)
                }
                val elseBlock = createBasicBlock()
                thenBlock.connectTo(elseBlock)
                currentBlock.connectTo(elseBlock)
                currentBlock = elseBlock
            }
            else -> {
                if (x.operator.isAssignment) {
                    acceptLhs(x.arg1)
                }
                else {
                    accept(x.arg1)
                }
                accept(x.arg2)
                currentBlock.nodes += x
            }
        }
    }

    override fun visitPrefixOperation(x: JsPrefixOperation) {
        accept(x.arg)
        currentBlock.nodes += x
    }

    override fun visitPostfixOperation(x: JsPostfixOperation) {
        accept(x.arg)
        currentBlock.nodes += x
    }

    private fun acceptLhs(x: JsExpression) {
        when (x) {
            is JsNameRef -> {
                accept(x.qualifier)
            }
            is JsArrayAccess -> {
                accept(x.array)
                accept(x.index)
            }
            else -> {
                accept(x)
            }
        }
    }

    override fun visitBlock(x: JsBlock) = processBlock(x, null)

    private fun processBlock(x: JsBlock, label: JsName?) {
        val exit = createBasicBlock()
        withBreakTarget(label, exit, false) {
            for (part in x.statements) {
                accept(part)
            }
        }
        if (label != null) {
            currentBlock.connectTo(exit)
            currentBlock = exit
        }
    }

    override fun visitBreak(x: JsBreak) {
        val label = x.label?.name
        val target = if (label != null) {
            breakTargets[label]!!
        }
        else {
            currentBreakTarget!!
        }
        currentBlock.nodes += x
        currentBlock.connectTo(target)
        currentBlock = createBasicBlock()
    }

    override fun visitContinue(x: JsContinue) {
        val label = x.label?.name
        val target = if (label != null) {
            continueTargets[label]!!
        }
        else {
            currentContinueTarget!!
        }
        currentBlock.nodes += x
        currentBlock.connectTo(target)
        currentBlock = createBasicBlock()
    }

    override fun visitConditional(x: JsConditional) {
        accept(x.test)
        currentBlock.nodes += x
        val thenBlock = enterBasicBlock {
            accept(x.thenExpression)
        }
        val elseBlock = enterBasicBlock {
            accept(x.elseExpression)
        }
        currentBlock = createBasicBlock()
        thenBlock.connectTo(currentBlock)
        elseBlock.connectTo(currentBlock)
        currentBlock.nodes += x
    }

    override fun visitWhile(x: JsWhile) = processWhile(x, null)

    private fun processWhile(x: JsWhile, label: JsName?) {
        val condition = x.condition
        if (condition is JsLiteral.JsBooleanLiteral && !condition.value) {
            return
        }

        val head = createBasicBlock()
        val exit = createBasicBlock()
        val body = createBasicBlock()
        currentBlock.connectTo(head)
        currentBlock = head

        accept(condition)
        currentBlock.nodes += x
        head.connectTo(body)
        if (condition !is JsLiteral.JsBooleanLiteral || !condition.value) {
            head.connectTo(exit)
        }

        currentBlock = body
        withBreakTarget(label, exit, true) {
            withContinueTarget(label, head, true) {
                accept(x.body)
            }
        }
        currentBlock.connectTo(head)

        currentBlock = exit
    }

    override fun visitDoWhile(x: JsDoWhile) = processDoWhile(x, null)

    private fun processDoWhile(x: JsDoWhile, label: JsName?) {
        val head = createBasicBlock()
        val guard = createBasicBlock()
        val exit = createBasicBlock()

        currentBlock.connectTo(head)
        currentBlock = head

        withBreakTarget(label, exit, true) {
            withContinueTarget(label, guard, true) {
                accept(x.body)
            }
        }

        val condition = x.condition
        if (condition is JsLiteral.JsBooleanLiteral && !condition.value) {
            currentBlock.connectTo(exit)
        }
        else {
            currentBlock.connectTo(guard)
            currentBlock = guard
            accept(condition)
            currentBlock.nodes += x
            currentBlock.connectTo(head)
            if (condition !is JsLiteral.JsBooleanLiteral || !condition.value) {
                currentBlock.connectTo(exit)
            }
        }

        currentBlock = exit
    }

    override fun visitExpressionStatement(x: JsExpressionStatement) {
        accept(x.expression)
        currentBlock.nodes += x
    }

    override fun visitFor(x: JsFor) = processFor(x, null)

    private fun processFor(x: JsFor, label: JsName?) {
        x.initVars?.accept(this)
        x.initExpression?.accept(this)

        val condition = x.condition
        if (condition is JsLiteral.JsBooleanLiteral && !condition.value) {
            return
        }

        val head = createBasicBlock()
        val body = createBasicBlock()
        val exit = createBasicBlock()
        val increment = createBasicBlock()
        currentBlock.connectTo(head)
        currentBlock = head

        accept(condition)
        currentBlock.nodes += x
        head.connectTo(body)
        if (condition != null || condition !is JsLiteral.JsBooleanLiteral || !condition.value) {
            head.connectTo(exit)
        }

        currentBlock = body
        withBreakTarget(label, exit, true) {
            withContinueTarget(label, increment, true) {
                accept(x.body)
            }
        }

        currentBlock.connectTo(increment)
        currentBlock = increment

        x.incrementExpression?.accept(this)
        currentBlock.connectTo(head)

        currentBlock = exit
    }

    override fun visitForIn(x: JsForIn) = processForIn(x, null)

    private fun processForIn(x: JsForIn, label: JsName?) {
        x.objectExpression?.accept(this)

        val head = createBasicBlock()
        val body = createBasicBlock()
        val exit = createBasicBlock()

        currentBlock.connectTo(head)
        currentBlock = head

        x.iterExpression?.accept(this)
        currentBlock.nodes += x
        currentBlock.connectTo(body)
        currentBlock.connectTo(exit)
        currentBlock = body

        withBreakTarget(label, exit, true) {
            withContinueTarget(label, head, true) {
                accept(x.body)
            }
        }

        currentBlock.connectTo(head)

        currentBlock = exit
    }

    override fun visitFunction(x: JsFunction) {
        currentBlock.nodes += x
    }

    override fun visitIf(x: JsIf) {
        accept(x.ifExpression)
        currentBlock.nodes += x
        val thenBlock = enterBasicBlock {
            accept(x.thenStatement)
        }
        val elseBlock = enterBasicBlock {
            x.elseStatement?.let { accept(it) }
        }
        currentBlock = createBasicBlock()
        thenBlock.connectTo(currentBlock)
        elseBlock.connectTo(currentBlock)
    }

    override fun visitInvocation(invocation: JsInvocation) {
        accept(invocation.qualifier)
        invocation.arguments.forEach { accept(it) }
        currentBlock.nodes += invocation
    }

    override fun visitNew(x: JsNew) {
        accept(x.constructorExpression)
        x.arguments.forEach { accept(it) }
        currentBlock.nodes += x
    }

    override fun visitLabel(x: JsLabel) {
        val body = x.statement
        val label = x.name

        when (body) {
            is JsWhile -> processWhile(body, label)
            is JsDoWhile -> processDoWhile(body, label)
            is JsFor -> processFor(body, label)
            is JsForIn -> processForIn(body, label)
            is JsSwitch -> processSwitch(body, label)
            else -> {
                val next = createBasicBlock()
                withBreakTarget(label, next, false) {
                    accept(body)
                }
                currentBlock.connectTo(next)
                currentBlock = next
                currentBlock.nodes += x
            }
        }
    }

    override fun visitNameRef(nameRef: JsNameRef) {
        nameRef.qualifier?.let { accept(it) }
        currentBlock.nodes += nameRef
    }

    override fun visitReturn(x: JsReturn) {
        accept(x.expression)
        currentBlock.nodes += x
        terminalBlocks += currentBlock
        currentBlock = createBasicBlock()
    }

    override fun visit(x: JsSwitch) = processSwitch(x, null)

    private fun processSwitch(x: JsSwitch, label: JsName?) {
        accept(x.expression)
        currentBlock.nodes += x
        val conditionBlock = currentBlock
        val enter = currentBlock
        val exit = createBasicBlock()

        withBreakTarget(label, exit, true) {
            var previousBlock: BasicBlock? = null

            for (switchCase in x.cases) {
                currentBlock = createBasicBlock()

                conditionBlock.connectTo(currentBlock)
                if (previousBlock != null) {
                    previousBlock.connectTo(currentBlock)
                }

                switchCase.statements.forEach { accept(it) }
                previousBlock = currentBlock
            }
        }

        val hasDefaultCase = x.cases.any { it is JsDefault }
        if (!hasDefaultCase) {
            enter.connectTo(exit)
        }

        currentBlock.connectTo(exit)
        currentBlock = exit
    }

    override fun visitThrow(x: JsThrow) {
        accept(x.expression)
        currentBlock.nodes += x
        terminalBlocks += currentBlock
        currentBlock = createBasicBlock()
    }

    override fun visitTry(x: JsTry) {
        val exit = createBasicBlock()

        val oldCreatedBlocks = createdBlocks.toList()
        createdBlocks.clear()

        val enter = createBasicBlock()
        currentBlock.connectTo(enter)
        currentBlock = enter

        accept(x.tryBlock)
        val protectedBlocks = createdBlocks.toList()
        createdBlocks.clear()
        createdBlocks += oldCreatedBlocks

        val landingPad = createBasicBlock()
        landingPad.nodes += x
        for (protectedBlock in protectedBlocks) {
            protectedBlock.exceptionHandler = landingPad
        }

        val bodyEnd = currentBlock
        if (x.finallyBlock == null) {
            bodyEnd.connectTo(exit)
        }

        x.catches.map {
            currentBlock = createBasicBlock()
            landingPad.connectTo(currentBlock)
            currentBlock.nodes += it
            accept(it.body)
            currentBlock.connectTo(exit)
        }

        x.finallyBlock?.let {
            currentBlock = createBasicBlock()
            bodyEnd.connectTo(currentBlock)
            protectedBlocks.filter { it in terminalBlocks }.forEach { it.finallyBlock = currentBlock }
            landingPad.connectTo(currentBlock)
            currentBlock.finallyNode = x
            accept(it)
            currentBlock.connectTo(exit)
            terminalBlocks += currentBlock
        }

        currentBlock = exit
    }

    override fun visit(x: JsVars.JsVar) {
        val initializer = x.initExpression
        if (initializer != null) {
            accept(initializer)
        }
    }

    override fun visitVars(x: JsVars) {
        x.vars.forEach { accept(it) }
        currentBlock.nodes.add(x)
    }

    override fun visitBoolean(x: JsLiteral.JsBooleanLiteral) {
        currentBlock.nodes += x
    }

    override fun visitDouble(x: JsNumberLiteral.JsDoubleLiteral) {
        currentBlock.nodes += x
    }

    override fun visitInt(x: JsNumberLiteral.JsIntLiteral) {
        currentBlock.nodes += x
    }

    override fun visitNull(x: JsNullLiteral) {
        currentBlock.nodes += x
    }

    override fun visitObjectLiteral(x: JsObjectLiteral) {
        for (property in x.propertyInitializers) {
            accept(property.labelExpr)
            accept(property.valueExpr)
        }
        currentBlock.nodes += x
    }

    override fun visitThis(x: JsLiteral.JsThisRef) {
        currentBlock.nodes += x
    }

    override fun visitString(x: JsStringLiteral) {
        currentBlock.nodes += x
    }

    override fun visitRegExp(x: JsRegExp) {
        currentBlock.nodes += x
    }

    private inline fun enterBasicBlock(action: () -> Unit): BasicBlock {
        val oldBlock = currentBlock
        val newBlock = createBasicBlock()
        oldBlock.connectTo(newBlock)
        currentBlock = newBlock
        action()
        val result = currentBlock
        currentBlock = oldBlock
        return result
    }

    private inline fun withBreakTarget(label: JsName?, target: BasicBlock, default: Boolean, action: () -> Unit) {
        withTarget(breakTargets, this::currentBreakTarget, label, target, default, action)
    }

    private inline fun withContinueTarget(label: JsName?, target: BasicBlock, default: Boolean, action: () -> Unit) {
        withTarget(continueTargets, this::currentContinueTarget, label, target, default, action)
    }

    private inline fun withTarget(
            map: MutableMap<JsName, BasicBlock>, defaultTarget: KMutableProperty0<BasicBlock?>,
            label: JsName?, target: BasicBlock, default: Boolean, action: () -> Unit
    ) {
        val oldTarget = map[label]
        if (label != null) {
            map[label] = target
        }

        val oldDefaultTarget = defaultTarget.get()
        if (default) {
            defaultTarget.set(target)
        }

        action()

        if (label != null) {
            if (oldTarget == null) {
                map.keys -= label
            }
            else {
                map[label] = oldTarget
            }
        }

        if (default) {
            defaultTarget.set(oldDefaultTarget)
        }
    }

    private fun createBasicBlock() = BasicBlock().apply { createdBlocks += this }
}

private fun removeUnreachableBlocks(start: BasicBlock) {
    val reachable = start.getReachableBlocks()
    for (block in reachable) {
        for (predecessor in block.predecessors.toList()) {
            if (predecessor !in reachable) {
                predecessor.disconnectFromAny(block)
            }
        }
    }
}

private fun removeEmptyBlocks(start: BasicBlock) {
    if (start.nodes.isEmpty() && start.successors.size == 1 && start.typedSuccessors.isEmpty()) {
        val startSuccessor = start.ordinarySuccessors.first()
        start.nodes += startSuccessor.nodes
        startSuccessor.nodes.clear()

        startSuccessor.ordinarySuccessors.forEach { start.connectTo(it) }
        startSuccessor.typedSuccessors.forEach { val (type, node) = it; start.connectTo(node, type) }
        startSuccessor.successors.toList().forEach { startSuccessor.disconnectFromAny(it) }

        start.disconnectFrom(startSuccessor)
    }

    val reachable = start.getReachableBlocks()
    val emptyBlockReplacements = mutableMapOf<BasicBlock, BasicBlock>()
    fun getBlockReplacement(block: BasicBlock): BasicBlock = emptyBlockReplacements.getOrPut(block) {
        if (block != start && block.nodes.isEmpty() && block.ordinarySuccessors.size == 1 && block.typedSuccessors.isEmpty()) {
            getBlockReplacement(block.ordinarySuccessors.first())
        }
        else {
            block
        }
    }

    for (block in reachable) {
        getBlockReplacement(block)
    }

    for (block in reachable) {
        val successorsWithReplacements = block.successors.map { Pair(it, getBlockReplacement(it)) }
        if (successorsWithReplacements.any { it.first != it.second }) {
            for (successor in block.ordinarySuccessors.toList()) {
                block.disconnectFrom(successor)
                block.connectTo(getBlockReplacement(successor))
            }
            for ((type, successor) in block.typedSuccessors.entries.toList()) {
                block.disconnectFrom(successor, type)
                block.connectTo(getBlockReplacement(successor), type)
            }
        }
    }
}