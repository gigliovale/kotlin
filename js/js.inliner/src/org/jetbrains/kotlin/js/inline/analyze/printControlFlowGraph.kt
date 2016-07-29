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
import org.jetbrains.kotlin.utils.mapToIndex
import org.jetbrains.kotlin.utils.singletonOrEmptyList

fun BasicBlock.printControlFlowGraph(): String {
    val blocks = getReachableBlocks().toList()
    val blockIndexes = blocks.mapToIndex()
    val sb = StringBuilder()
    val insnPrinter = InstructionPrinter()

    for (block in blocks) {
        val index = blockIndexes[block]!!
        val successorIndexes = block.ordinarySuccessors.asSequence().map { blockIndexes[it]!!.toString() }
        val typedSuccessorIndexes = block.typedSuccessors.entries.asSequence().map {
            val (type, successor) = it
            "${blockIndexes[successor]} as $type"
        }
        sb.append("$index -> [" + (successorIndexes + typedSuccessorIndexes).joinToString(", ") + "]\n")

        block.finallyNode?.let {
            sb.append("  " + insnPrinter.printFinally(it) + "\n")
        }
        for (node in block.nodes) {
            sb.append("  ${insnPrinter.print(node)}\n")
        }
    }

    return sb.toString()
}

private class InstructionPrinter : JsVisitor() {
    private val nodeIndexes = mutableMapOf<JsNode, Int>()
    var text: String = ""

    fun print(node: JsNode): String {
        val prefix = "\$${node.getIndex()}(${node.javaClass.simpleName})"
        text = ""
        accept(node)
        return prefix + (if (text.isNotEmpty()) ": $text" else "")
    }

    fun printFinally(node: JsTry): String {
        return "finally: $" + node.getIndex()
    }

    override fun visitArrayAccess(x: JsArrayAccess) {
        text = "\$${x.arrayExpression.getIndex()}[\$${x.indexExpression.getIndex()}]"
    }

    override fun visitArray(x: JsArrayLiteral) {
        text = "[" + x.expressions.map { "\$${it.getIndex()}" }.joinToString(", ") + "]"
    }

    override fun visitBinaryExpression(x: JsBinaryOperation) {
        text = if (x.operator.isAssignment) {
            accept(x.arg1)
            "$text ${x.operator.name} \$${x.arg2.getIndex()}"
        }
        else {
            "\$${x.arg1.getIndex()} ${x.operator.name} \$${x.arg2.getIndex()}"
        }
    }

    override fun visitBlock(x: JsBlock) {
        text = "{ " + x.statements.map { "\$${it.getIndex()}" }.joinToString("; ") + " }"
    }

    override fun visitBoolean(x: JsLiteral.JsBooleanLiteral) {
        text = "${x.value}"
    }

    override fun visitConditional(x: JsConditional) {
        text = "\$${x.testExpression.getIndex()}"
    }

    override fun visitDoWhile(x: JsDoWhile) {
        text = "\$${x.condition.getIndex()}"
    }

    override fun visitExpressionStatement(x: JsExpressionStatement) {
        text = "\$${x.expression.getIndex()}"
    }

    override fun visitFor(x: JsFor) {
        val initText = (x.initVars ?: x.initExpression)?.let { "\$${it.getIndex()}" }
        val conditionText = x.condition?.let { " \$${it.getIndex()}" }
        val incrementText = x.incrementExpression?.let { " \$${it.getIndex()}" }
        text = (initText ?: "") + ";" + (conditionText ?: "") + ";" + (incrementText ?: "")
    }

    override fun visitForIn(x: JsForIn) {
        val iterText = x.iterExpression?.let { "\$${it.getIndex()}" } ?: x.iterVarName.ident
        text = "$iterText in \$${x.objectExpression.getIndex()}"
    }

    override fun visitIf(x: JsIf) {
        text = "\$${x.ifExpression.getIndex()}"
    }

    override fun visitInvocation(invocation: JsInvocation) {
        text = "\$${invocation.qualifier.getIndex()}(" + invocation.arguments.map { "\$${it.getIndex()}" }.joinToString(", ") + ")"
    }

    override fun visitNameRef(nameRef: JsNameRef) {
        text = (nameRef.qualifier?.let { "\$${it.getIndex()}." } ?: "") + (nameRef.name ?: nameRef.ident).toString()
    }

    override fun visitNew(x: JsNew) {
        text = "\$${x.constructorExpression.getIndex()}(" + x.arguments.map { "\$${it.getIndex()}" }.joinToString(", ") + ")"
    }

    override fun visitInt(x: JsNumberLiteral.JsIntLiteral) {
        text = "${x.value}"
    }

    override fun visitDouble(x: JsNumberLiteral.JsDoubleLiteral) {
        text = "${x.value}"
    }

    override fun visitObjectLiteral(x: JsObjectLiteral) {
        val initializersText = x.propertyInitializers
                .map { "\$${it.labelExpr.getIndex()}: \$${it.valueExpr.getIndex()}" }
               .joinToString(", ")
        text = "{ $initializersText }"
    }

    override fun visitPostfixOperation(x: JsPostfixOperation) {
        text = "\$${x.arg.getIndex()} ${x.operator.name}"
    }

    override fun visitPrefixOperation(x: JsPrefixOperation) {
        text = "${x.operator.name} \$${x.arg.getIndex()}"
    }

    override fun visitRegExp(x: JsRegExp) {
        text = x.pattern
    }

    override fun visitReturn(x: JsReturn) {
        text = x.expression?.let { "\$${it.getIndex()}" } ?: ""
    }

    override fun visitString(x: JsStringLiteral) {
        text = x.value
    }

    override fun visit(x: JsSwitch) {
        text = "\$${x.expression.getIndex()}"
    }

    override fun visitThrow(x: JsThrow) {
        text = "\$${x.expression.getIndex()}"
    }

    override fun visitTry(x: JsTry) {
        val indexes = x.catches.map { it.getIndex() } + x.finallyBlock.singletonOrEmptyList().map { it.getIndex() }
        text = indexes.map { "\$$it" }.joinToString(", ")
    }

    override fun visitCatch(x: JsCatch) {
        text = x.parameter.name.ident
    }

    override fun visitVars(x: JsVars) {
        text = x.vars.map { decl -> "${decl.name}" + (decl.initExpression?.let { " = \$${it.getIndex()}" } ?: "") }.joinToString("; ")
    }

    override fun visitWhile(x: JsWhile) {
        text = "\$${x.condition.getIndex()}"
    }

    private fun JsNode.getIndex() = nodeIndexes.getOrPut(this) { nodeIndexes.size }
}