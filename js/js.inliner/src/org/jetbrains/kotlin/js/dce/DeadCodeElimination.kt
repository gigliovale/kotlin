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

package org.jetbrains.kotlin.js.dce

import org.jetbrains.kotlin.js.backend.ast.*
import org.jetbrains.kotlin.js.inline.util.collectLocalVariables
import org.jetbrains.kotlin.js.translate.utils.JsAstUtils
import org.jetbrains.kotlin.js.translate.utils.jsAstUtils.array
import org.jetbrains.kotlin.js.translate.utils.jsAstUtils.index
import org.jetbrains.kotlin.js.translate.utils.jsAstUtils.test
import java.util.*

class DeadCodeElimination(private val root: JsStatement) {
    private val nodes = mutableMapOf<JsName, Node>()
    private val worklist: Queue<() -> Unit> = ArrayDeque()
    private val processedFunctions = mutableSetOf<JsFunction>()
    private val nodeMap = mutableMapOf<JsNode, Node>()
    private val valueMap = mutableMapOf<JsNode, Value>()
    private val globalScope = ValueImpl(null, "<global>")
    private val globalScopeNode = NodeImpl(null, "")

    private val objectValue = ValueImpl(null, "<global>.Object")
    private val objectNode = nodeOfOneValue(objectValue)
    private val functionValue = ValueImpl(null, "<global>.Function")
    private val functionNode = nodeOfOneValue(functionValue)
    private val functionPrototypeValue = ValueImpl(null, "<global>.Function.prototype")
    private val arrayValue = ValueImpl(null, "<global>.Array")
    private val arrayNode = nodeOfOneValue(functionValue)
    private val primitiveValue = ValueImpl(null, "<primitive>")
    private val objectCreateValue = ValueImpl(null, "<global>.Object.create")
    private val objectDefineProperty = ValueImpl(null, "<global>.Object.defineProperty")

    private val functionApplyValue = ValueImpl(null, "<global>.Function.prototype.apply")
    private val functionCallValue = ValueImpl(null, "<global>.Function.prototype.call")

    companion object {
        private val PROTO = "__proto__"
    }

    fun apply() {
        primitiveValue.use()
        dynamicValue.use()

        globalScopeNode.addValue(globalScope)
        globalScope.getMember("Object").addValue(propertyDescriptorOfOneValue(objectValue))
        globalScope.getMember("Function").addValue(propertyDescriptorOfOneValue(functionValue))
        globalScope.getMember("Array").addValue(propertyDescriptorOfOneValue(arrayValue))

        objectValue.getMember("create").addValue(propertyDescriptorOfOneValue(objectCreateValue))
        objectValue.getMember("defineProperty").addValue(propertyDescriptorOfOneValue(objectDefineProperty))

        functionValue.getMember("prototype").addValue(propertyDescriptorOfOneValue(functionPrototypeValue))
        functionPrototypeValue.getMember("apply").addValue(propertyDescriptorOfOneValue(functionApplyValue))
        functionPrototypeValue.getMember("call").addValue(propertyDescriptorOfOneValue(functionCallValue))

        /*for (globalValue in listOf(globalScope, objectValue, functionValue, arrayValue, functionPrototypeValue)) {
            spreadGlobalValue(globalValue)
        }*/

        enterScope(root)
        root.accept(visitor)
        var lastSize = 0
        while (worklist.isNotEmpty()) {
            if (Math.abs(lastSize - worklist.size) > 100) {
                println(worklist.size)
                lastSize = worklist.size
            }
            worklist.remove()()
        }

        eliminator.accept(root)

        /*object : JsVisitorWithContextImpl() {
            override fun endVisit(x: JsFunction, ctx: JsContext<in JsNode>) {
                super.endVisit(x, ctx)
                if (x in processedFunctions) {
                    ctx.replaceMe(JsInvocation(JsNameRef("_reachable_"), x))
                }
            }
        }.accept(root)*/
    }

    private fun spreadGlobalValue(global: Value): Value {
        global.addHandler(object : ValueEventHandler {
            override fun memberAdded(name: String, value: Node) {
                if (value.getValues().isEmpty()) {
                    value.addValue(spreadGlobalValue(global))
                }
            }

            override fun returnValueAdded(value: Node) {
                if (value.getValues().isEmpty()) {
                    value.addValue(spreadGlobalValue(global))
                }
            }
        })
        return global
    }

    private val eliminator = object : JsVisitorWithContextImpl() {
        private val variablesInScope = mutableSetOf<JsName>()
        private var localVariablesInCurrentFunction = setOf<JsName>()
        private val localVariablesInOuterFunctions = ArrayDeque<Set<JsName>>()

        fun enterScope(names: Set<JsName>) {
            localVariablesInOuterFunctions.push(localVariablesInCurrentFunction)
            localVariablesInCurrentFunction = names
            variablesInScope += localVariablesInCurrentFunction
        }

        fun exitScope() {
            variablesInScope -= localVariablesInCurrentFunction
            localVariablesInCurrentFunction = localVariablesInOuterFunctions.pop()
        }

        override fun visit(x: JsFunction, ctx: JsContext<in JsNode>): Boolean {
            enterScope(x.collectLocalVariables())
            if (x !in processedFunctions) {
                x.parameters.clear()
                x.body.statements.clear()
                return false
            }
            return true
        }

        override fun endVisit(x: JsFunction, ctx: JsContext<*>) {
            exitScope()
        }

        override fun visit(x: JsArrayAccess, ctx: JsContext<in JsNode>): Boolean {
            if (shouldRemoveNode(x) && !propertyAccessHasSideEffect(x.array, getPossibleStringValues(x.index))) {
                ctx.replaceMe(exprSequence(accept(x.array), accept(x.index)))
                return false
            }
            return true
        }

        override fun visit(x: JsBinaryOperation, ctx: JsContext<in JsNode>): Boolean {
            when (x.operator) {
                JsBinaryOperator.ASG -> {
                    val lhs = x.arg1
                    if (lhs is JsArrayAccess) {
                        if (shouldRemoveNode(x) && !propertyModificationHasSideEffect(lhs.array, getPossibleStringValues(lhs.index))) {
                            ctx.replaceMe(exprSequence(accept(lhs.array), accept(lhs.index), x.arg2))
                            return false
                        }
                    }
                    else if (lhs is JsNameRef) {
                        val name = lhs.name
                        if (name == null || name !in variablesInScope) {
                            val qualifier = lhs.qualifier
                            val hasSideEffect = if (qualifier != null) {
                                propertyModificationHasSideEffect(qualifier, setOf(lhs.ident))
                            }
                            else {
                                propertyHasSideEffect(globalScopeNode, setOf(lhs.ident), "set")
                            }
                            if (shouldRemoveNode(x) && !hasSideEffect) {
                                ctx.replaceMe(exprSequence(qualifier ?: JsLiteral.NULL, accept(x.arg2)))
                                return false
                            }
                        }
                    }
                }

                JsBinaryOperator.AND,
                JsBinaryOperator.OR -> {}

                else -> {
                    if (shouldRemoveNode(x)) {
                        ctx.replaceMe(exprSequence(accept(x.arg1), accept(x.arg2)))
                        return false
                    }
                }
            }

            return true
        }

        override fun endVisit(x: JsArrayLiteral, ctx: JsContext<in JsNode>) {
            if (shouldRemoveNode(x)) {
                ctx.replaceMe(exprSequence(*x.expressions.toTypedArray(), JsLiteral.NULL))
            }
        }

        override fun endVisit(x: JsObjectLiteral, ctx: JsContext<in JsNode>) {
            if (shouldRemoveNode(x)) {
                ctx.replaceMe(exprSequence(*x.propertyInitializers.map { it.valueExpr }.toTypedArray()))
            }
        }

        override fun visit(x: JsInvocation, ctx: JsContext<in JsNode>): Boolean {
            if (x.isOnly(objectDefineProperty) && x.arguments.size == 3) {
                if (shouldRemoveNode(x.arguments[0]) || shouldRemoveNode(x.arguments[2])) {
                    ctx.replaceMe(exprSequence(*x.arguments.map { accept(it) }.toTypedArray()))
                }
            }
            return true
        }

        override fun visit(x: JsCatch, ctx: JsContext<*>): Boolean {
            variablesInScope += x.parameter.name
            return true
        }

        override fun endVisit(x: JsCatch, ctx: JsContext<*>) {
            variablesInScope -= x.parameter.name
        }

        override fun endVisit(x: JsExpressionStatement, ctx: JsContext<in JsNode>) {
            val expression = x.expression
            if (!hasSideEffect(expression)) {
                ctx.removeMe()
            }
            else if (expression is JsFunction) {
                val name = expression.name
                if (name == null && expression.body.statements.isEmpty()) {
                    ctx.removeMe()
                }
            }
        }

        private fun exprSequence(vararg expressions: JsExpression): JsExpression {
            if (expressions.isEmpty()) return JsLiteral.NULL
            val filteredExpressions = expressions.dropLast(1).filter { hasSideEffect(it) }
            val expandedExpressions = mutableListOf<JsExpression>()
            filteredExpressions.forEach { expandCommas(it, expandedExpressions) }
            val last = expressions.last()
            return if (expandedExpressions.isEmpty()) last else JsAstUtils.newSequence(expandedExpressions + last)
        }

        private fun expandCommas(expression: JsExpression, target: MutableList<JsExpression>) {
            if (expression is JsBinaryOperation && expression.operator == JsBinaryOperator.COMMA) {
                expandCommas(expression.arg1, target)
                expandCommas(expression.arg2, target)
            }
            else {
                target += expression
            }
        }

        private fun hasSideEffect(expression: JsExpression): Boolean {
            return when (expression) {
                is JsFunction,
                is JsLiteral.JsThisRef,
                is JsNullLiteral,
                is JsNumberLiteral,
                is JsStringLiteral -> false
                is JsNameRef -> expression.name?.let { it !in variablesInScope } ?: true
                else -> true
            }
        }

        private fun JsNode.isOnly(value: Value): Boolean {
            val node = nodeMap[this] ?: return false
            return node.getValues().singleOrNull() == value
        }
    }

    private fun shouldRemoveNode(x: JsNode): Boolean {
        val node = nodeMap[x]
        return node != null && !hasUsedValues(node)
    }

    private fun propertyAccessHasSideEffect(x: JsNode, names: Set<String>?): Boolean = propertyHasSideEffect(x, names, "get")

    private fun propertyModificationHasSideEffect(x: JsNode, names: Set<String>?): Boolean = propertyHasSideEffect(x, names, "set")

    private fun propertyHasSideEffect(x: JsNode, names: Set<String>?, kind: String): Boolean {
        val node = nodeMap[x] ?: return true
        return propertyHasSideEffect(node, names, kind)
    }

    private fun propertyHasSideEffect(node: Node, names: Set<String>?, kind: String): Boolean {
        val members = node.getValues().flatMap { value ->
            if (names == null) value.getAllMembers() else names.mapNotNull { value.getMemberIfExists(it) }
        }
        for (member in members) {
            if (member.getValues().any { it.hasMember(kind) && it.getMember(kind).getValues().any { it.isUsed } }) return true
        }
        return false
    }

    private fun hasUsedValues(node: Node) = node.getValues().any { it.isUsed }

    private fun getPossibleStringValues(x: JsNode): Set<String>? {
        val node = nodeMap[x] ?: return null
        val allAreStrings = node.getValues().all { it.stringConstant != null }
        return if (allAreStrings) node.getValues().map { it.stringConstant!! }.toSet() else null
    }

    private val dynamicValue: Value by lazy {
        val newValue = ValueImpl(null, "<dynamic>")
        newValue.addHandler(object : ValueEventHandler {
            override fun memberAdded(name: String, value: Node) {
                value.addValue(newValue)
            }

            override fun parameterAdded(index: Int, value: Node) {
                value.addValue(newValue)
            }

            override fun returnValueAdded(value: Node) {
                value.addValue(newValue)
            }

            override fun dynamicMemberAdded(value: Node) {
                value.addValue(newValue)
            }
        })
        newValue
    }

    private var resultNode: Node = NodeImpl(null, "")
    private var returnNode: Node? = null
    private var thisNode: Node = globalScopeNode
    private val visitor = object : RecursiveJsVisitor() {
        override fun visitBinaryExpression(x: JsBinaryOperation) {
            if (x.operator == JsBinaryOperator.ASG) {
                handleAssignment(x, x.arg1, x.arg2)
            }
            else if (x.operator == JsBinaryOperator.OR) {
                accept(x.arg1)
                val first = resultNode
                accept(x.arg2)
                val second = resultNode
                resultNode = createNode(x)
                first.connectTo(resultNode)
                second.connectTo(resultNode)
            }
            else if (x.operator == JsBinaryOperator.COMMA) {
                accept(x.arg1)
                accept(x.arg2)
                nodeMap[x] = resultNode
            }
            else {
                accept(x.arg1)
                resultNode.use()
                accept(x.arg2)
                resultNode.use()

                resultNode = createNode(x)
                resultNode.addValue(primitiveValue)
            }
        }

        override fun visitPrefixOperation(x: JsPrefixOperation) {
            accept(x.arg)
            resultNode.use()

            resultNode = createNode(x)
            resultNode.addValue(primitiveValue)
        }

        override fun visitPostfixOperation(x: JsPostfixOperation) {
            accept(x.arg)
            resultNode.use()

            resultNode = createNode(x)
            resultNode.addValue(primitiveValue)
        }

        override fun visitConditional(x: JsConditional) {
            accept(x.test)
            resultNode.use()

            accept(x.thenExpression)
            val thenNode = resultNode
            accept(x.elseExpression)
            val elseNode = resultNode

            resultNode = createNode(x)
            thenNode.connectTo(resultNode)
            elseNode.connectTo(resultNode)
        }

        private fun handleAssignment(baseNode: JsExpression, leftExpr: JsExpression, rightExpr: JsExpression) {
            accept(rightExpr)
            val rhsNode = resultNode
            when (leftExpr) {
                is JsNameRef -> {
                    val varNode = leftExpr.name?.let { nodes[it] }
                    if (varNode != null) {
                        rhsNode.connectTo(varNode)
                    }
                    else {
                        val qualifierNode = leftExpr.qualifier?.let { accept(it); resultNode } ?: globalScopeNode
                        qualifierNode.addHandler(object : NodeEventHandler {
                            override fun valueAdded(value: Value) {
                                value.writeProperty(leftExpr.ident, rhsNode)
                            }
                        })
                    }
                }
                is JsArrayAccess -> {
                    accept(leftExpr.array)
                    val arrayNode = resultNode
                    val indexExpr = leftExpr.index
                    if (indexExpr is JsStringLiteral) {
                        arrayNode.addHandler(object : NodeEventHandler {
                            override fun valueAdded(value: Value) {
                                value.writeProperty(indexExpr.value, rhsNode)
                            }
                        })
                    }
                    else {
                        accept(leftExpr.index)
                        arrayNode.addHandler(object : NodeEventHandler {
                            override fun valueAdded(value: Value) {
                                value.writeDynamicProperty(rhsNode)
                            }
                        })
                    }
                }
                else -> error("Unexpected LHS expression: $leftExpr")
            }

            resultNode = rhsNode
            nodeMap[baseNode] = rhsNode
        }

        override fun visitFunction(x: JsFunction) {
            val value = constructObject(functionNode, emptyList(), x)
            val prototypeValue = constructObject(objectNode, emptyList(), null)
            val prototypeDescriptor = ValueImpl(x, "${value.path}.prototype#descriptor")
            prototypeDescriptor.getMember("value").addValue(prototypeValue)
            value.getMember("prototype").addValue(prototypeDescriptor)

            valueMap[x] = value
            resultNode = x.name?.let { nodes[it]!! } ?: createNode(x)
            resultNode.addValue(value)
        }

        override fun visitObjectLiteral(x: JsObjectLiteral) {
            val objectValue = constructObject(objectNode, listOf(), x)
            for (propertyInitializer in x.propertyInitializers) {
                accept(propertyInitializer.valueExpr)
                val propertyNode = resultNode
                val label = propertyInitializer.labelExpr
                when (label) {
                    is JsNameRef -> {
                        objectValue.writeProperty(label.ident, propertyNode)
                    }
                    is JsStringLiteral -> {
                        objectValue.writeProperty(label.value, propertyNode)
                    }
                    else -> {
                        objectValue.writeDynamicProperty(propertyNode)
                    }
                }
            }
            resultNode = createNode(x)
            resultNode.addValue(objectValue)
            objectValue.getMember(PROTO).addValue(objectValue)
        }

        override fun visitArray(x: JsArrayLiteral) {
            val arrayValue = constructObject(arrayNode, listOf(), x)
            for (item in x.expressions) {
                accept(item)
                arrayValue.writeDynamicProperty(resultNode)
            }
            resultNode = createNode(x)
            resultNode.addValue(arrayValue)
        }

        override fun visit(x: JsVars.JsVar) {
            val node = nodes[x.name]!!
            x.initExpression?.let {
                accept(it)
                resultNode.connectTo(node)
            }
            resultNode = node
        }

        override fun visitNameRef(nameRef: JsNameRef) {
            val qualifier = nameRef.qualifier
            resultNode = if (qualifier == null) {
                val name = nameRef.name
                val varNode = name?.let { nodes[it] }
                if (varNode != null) {
                    nodeMap[nameRef] = varNode
                    varNode
                }
                else {
                    createNode(nameRef).also { globalScope.readProperty(nameRef.ident, it) }
                }
            }
            else {
                accept(qualifier)
                val newNode = createNode(nameRef)
                resultNode.addHandler(object : NodeEventHandler {
                    override fun valueAdded(value: Value) {
                        value.readProperty(nameRef.ident, newNode)
                    }
                })
                resultNode.use()
                newNode
            }
        }

        override fun visitArrayAccess(x: JsArrayAccess) {
            accept(x.arrayExpression)
            val arrayNode = resultNode
            arrayNode.use()

            val indexExpr = x.indexExpression
            val newNode = createNode(x)
            if (indexExpr is JsStringLiteral) {
                arrayNode.addHandler(object : NodeEventHandler {
                    override fun valueAdded(value: Value) {
                        value.readProperty(indexExpr.value, newNode)
                    }
                })
            }
            else {
                accept(indexExpr)
                resultNode.use()
                arrayNode.addHandler(object : NodeEventHandler {
                    override fun valueAdded(value: Value) {
                        value.readDynamicProperty(newNode)
                    }
                })
            }
            resultNode = newNode
        }

        override fun visitInvocation(invocation: JsInvocation) {
            val newNode = createNode(invocation)
            val qualifier = invocation.qualifier

            val qualifierNode: Node
            val receiverNode: Node

            if (qualifier is JsNameRef) {
                val functionNode = qualifier.name?.let { nodes[it] }
                if (functionNode != null) {
                    receiverNode = functionNode
                    qualifierNode = functionNode
                }
                else {
                    qualifierNode = createNode(qualifier)
                    accept(qualifier.qualifier)
                    receiverNode = resultNode
                    receiverNode.addHandler(object : NodeEventHandler {
                        override fun valueAdded(value: Value) {
                            value.readProperty(qualifier.ident, qualifierNode)
                        }
                    })
                }
            }
            else {
                qualifierNode = createNode(qualifier)
                receiverNode = NodeImpl(invocation, "")
                accept(qualifier)
                resultNode.connectTo(qualifierNode)
            }

            val argumentsNodes = invocation.arguments.map {
                accept(it)
                resultNode
            }

            qualifierNode.addHandler(object : NodeEventHandler {
                override fun valueAdded(value: Value) {
                    when (value) {
                        objectCreateValue -> {
                            handleObjectCreate(invocation, argumentsNodes.getOrNull(0), newNode)
                        }
                        objectDefineProperty -> {
                            if (argumentsNodes.size >= 3) {
                                handleObjectDefineProperty(argumentsNodes[0], argumentsNodes[1], argumentsNodes[2])
                            }
                            newNode.addValue(primitiveValue)
                        }
                        functionCallValue -> {
                            handleFunctionCall(receiverNode, argumentsNodes.getOrNull(0), argumentsNodes.drop(1), newNode)
                        }
                        functionApplyValue -> {
                            handleFunctionApply(receiverNode, argumentsNodes.getOrNull(0), argumentsNodes.getOrNull(1), newNode)
                        }
                        else -> {
                            receiverNode.connectTo(value.getParameter(0))
                            for ((index, argNode) in argumentsNodes.withIndex()) {
                                argNode.connectTo(value.getParameter(index + 1))
                            }
                            value.getReturnValue().connectTo(newNode)
                            processFunctionIfNecessary(value)
                        }
                    }
                }
            })
            receiverNode.use()
            qualifierNode.use()

            resultNode = newNode
        }

        private fun handleObjectCreate(jsNode: JsNode?, prototypeNode: Node?, resultNode: Node) {
            val objectValue = ValueImpl(jsNode, "Object.create")

            prototypeNode?.addHandler(object : NodeEventHandler {
                override fun valueAdded(value: Value) {
                    value.addHandler(object : ValueEventHandler {
                        override fun memberAdded(name: String, value: Node) {
                            if (name != PROTO) {
                                value.connectTo(objectValue.getMember(name))
                            }
                        }

                        override fun dynamicMemberAdded(value: Node) {
                            value.connectTo(objectValue.getDynamicMember())
                        }
                    })
                }
            })
            prototypeNode?.use()

            resultNode.addValue(objectValue)
        }

        private fun handleObjectDefineProperty(objectNode: Node, propertyNameNode: Node, descriptorNode: Node) {
            val descriptor = ValueImpl(objectNode.jsNode, "${objectNode.path}#descriptor")
            descriptorNode.addHandler(object : NodeEventHandler {
                override fun valueAdded(value: Value) {
                    value.readProperty("get", descriptor.getMember("get"))
                    value.readProperty("set", descriptor.getMember("set"))
                    value.readProperty("value", descriptor.getMember("value"))
                }
            })

            propertyNameNode.addHandler(object : NodeEventHandler {
                override fun valueAdded(value: Value) {
                    val propertyName = value
                    if (primitiveValue !in propertyNameNode.getValues()) {
                        val name = propertyName.stringConstant
                        if (name == null) {
                            objectNode.addHandler(object : NodeEventHandler {
                                override fun valueAdded(value: Value) {
                                    value.getDynamicMember().addValue(descriptor)
                                }
                            })
                        }
                        else {
                            objectNode.addHandler(object : NodeEventHandler {
                                override fun valueAdded(value: Value) {
                                    value.getMember(name).addValue(descriptor)
                                }
                            })
                        }
                    }
                }
            })
        }

        private fun handleFunctionCall(functionNode: Node, thisNode: Node?, argumentsNodes: List<Node>, resultNode: Node) {
            functionNode.addHandler(object : NodeEventHandler {
                override fun valueAdded(value: Value) {
                    processFunctionIfNecessary(value)
                    value.use()
                    thisNode?.connectTo(value.getParameter(0))
                    for ((index, arg) in argumentsNodes.withIndex()) {
                        arg.connectTo(value.getParameter(index + 1))
                    }
                    value.getReturnValue().connectTo(resultNode)
                }
            })
        }

        private fun handleFunctionApply(functionNode: Node, thisNode: Node?, argumentsNode: Node?, resultNode: Node) {
            functionNode.addHandler(object : NodeEventHandler {
                override fun valueAdded(value: Value) {
                    val function = value
                    processFunctionIfNecessary(value)
                    function.use()
                    thisNode?.connectTo(function.getParameter(0))
                    val argumentsHub = NodeImpl(functionNode.jsNode, "${functionNode.path}#args")
                    argumentsNode?.addHandler(object : NodeEventHandler {
                        override fun valueAdded(value: Value) {
                            value.addHandler(object : ValueEventHandler {
                                override fun dynamicMemberAdded(value: Node) {
                                    value.connectTo(argumentsHub)
                                }
                            })
                        }
                    })
                    function.addHandler(object : ValueEventHandler {
                        override fun parameterAdded(index: Int, value: Node) {
                            argumentsHub.connectTo(value)
                        }
                    })
                    function.getReturnValue().connectTo(resultNode)
                }
            })
        }

        override fun visitReturn(x: JsReturn) {
            x.expression?.let {
                accept(it)
                returnNode?.let { resultNode.connectTo(it) }
            }
            resultNode = createNode(x)
        }

        override fun visitNew(x: JsNew) {
            accept(x.constructorExpression)
            val constructorNode = resultNode

            val argumentsNodes = x.arguments.map {
                accept(it)
                resultNode
            }

            val newNode = createNode(x)
            newNode.addValue(constructObject(constructorNode, argumentsNodes, x))
            constructorNode.use()
            resultNode = newNode
        }

        override fun visitThis(x: JsLiteral.JsThisRef) {
            resultNode = thisNode
        }

        override fun visitBoolean(x: JsLiteral.JsBooleanLiteral) = handlePrimitive(x)

        override fun visitDouble(x: JsNumberLiteral.JsDoubleLiteral) = handlePrimitive(x)

        override fun visitInt(x: JsNumberLiteral.JsIntLiteral) = handlePrimitive(x)

        override fun visitNull(x: JsNullLiteral) = handlePrimitive(x)

        override fun visitRegExp(x: JsRegExp) = handlePrimitive(x)

        private fun handlePrimitive(x: JsNode) {
            resultNode = createNode(x)
            resultNode.addValue(primitiveValue)
        }

        override fun visitString(x: JsStringLiteral) {
            resultNode = createNode(x)
            resultNode.addValue(ValueImpl(x, "", x.value))
        }

        override fun visitThrow(x: JsThrow) {
            x.expression.accept(this)
            resultNode.use()
        }

        override fun visitForIn(x: JsForIn) {
            accept(x.objectExpression)
            val objectNode = resultNode
            val iterVarName = x.iterVarName
            val node = if (iterVarName != null) {
                nodes[iterVarName]!!
            }
            else {
                accept(x.iterExpression)
                resultNode
            }

            objectNode.addHandler(object : NodeEventHandler {
                override fun valueAdded(value: Value) {
                    value.addHandler(object : ValueEventHandler {
                        override fun dynamicMemberAdded(value: Node) {
                            node.addValue(primitiveValue)
                        }

                        override fun memberAdded(name: String, value: Node) {
                            if (primitiveValue !in node.getValues()) {
                                node.addValue(ValueImpl(x, "", name))
                            }
                        }
                    })
                }
            })

            x.body?.accept(this)
        }
    }

    private fun createNode(jsNode: JsNode?): Node {
        return NodeImpl(jsNode, "").also {
            if (jsNode != null) {
                nodeMap[jsNode] = it
            }
        }
    }

    private fun constructObject(constructorNode: Node, argumentsNodes: List<Node>, jsNode: JsNode?): Value {
        val objectValue = ValueImpl(jsNode, "")
        val prototypeNode = objectValue.getMember(PROTO)
        constructorNode.addHandler(object : NodeEventHandler {
            override fun valueAdded(value: Value) {
                value.getParameter(0).addValue(objectValue)
                for ((index, argumentNode) in argumentsNodes.withIndex()) {
                    argumentNode.connectTo(value.getParameter(index + 1))
                }

                processFunctionIfNecessary(value)
                value.readProperty("prototype", prototypeNode)
            }
        })
        constructorNode.use()

        prototypeNode.addHandler(object : NodeEventHandler {
            override fun valueAdded(value: Value) {
                value.addHandler(object : ValueEventHandler {
                    override fun memberAdded(name: String, value: Node) {
                        if (name != PROTO) {
                            value.connectTo(objectValue.getMember(name))
                        }
                    }

                    override fun dynamicMemberAdded(value: Node) {
                        value.connectTo(objectValue.getDynamicMember())
                    }
                })
            }
        })
        prototypeNode.use()

        return objectValue
    }

    private fun processFunctionIfNecessary(value: Value) {
        val jsFunction = value.jsNode as? JsFunction
        if (jsFunction != null) {
            if (processedFunctions.add(jsFunction)) {
                processFunction(jsFunction, value)
            }
        }
    }

    fun enterScope(scope: JsNode) {
        scope.accept(object : RecursiveJsVisitor() {
            override fun visitFunction(x: JsFunction) {
                x.name?.let { nodes[it] = createNode(x) }
            }

            override fun visit(x: JsVars.JsVar) {
                super.visit(x)
                nodes[x.name] = createNode(x)
            }
        })
    }

    private fun processFunction(function: JsFunction, value: Value) {
        returnNode = value.getReturnValue()
        for ((index, param) in function.parameters.withIndex()) {
            val parameterNode = createNode(param)
            nodes[param.name] = parameterNode
            value.getParameter(index + 1).connectTo(parameterNode)
        }
        thisNode = value.getParameter(0)
        enterScope(function.body)
        function.body.accept(visitor)
    }

    private fun Value.readProperty(name: String, to: Node) {
        readProperty(to) { getMember(name) }
    }

    private fun Value.readDynamicProperty(to: Node) {
        readProperty(to) { getDynamicMember() }
    }

    private fun Value.readProperty(to: Node, descriptorSupplier: () -> Node) {
        val propertyDescriptorNode = descriptorSupplier()
        propertyDescriptorNode.addHandler(object : NodeEventHandler {
            override fun valueAdded(value: Value) {
                val propertyDescriptor = value
                propertyDescriptor.getMember("value").connectTo(to)
                propertyDescriptor.getMember("get").addHandler(object : NodeEventHandler {
                    override fun valueAdded(value: Value) {
                        val getter = value
                        getter.getParameter(0).addValue(this@readProperty)
                        getter.getReturnValue().connectTo(to)
                        processFunctionIfNecessary(getter)
                    }
                })
            }
        })
    }

    private fun Value.writeProperty(name: String, newValue: Node) {
        writeProperty(newValue) { getMember(name) }
    }

    private fun Value.writeDynamicProperty(newValue: Node) {
        writeProperty(newValue) { getDynamicMember() }
    }

    private fun Value.writeProperty(newValue: Node, descriptorSupplier: () -> Node) {
        val newPropertyDescriptor = ValueImpl(newValue.jsNode, "${newValue.path}#descriptor")
        newValue.connectTo(newPropertyDescriptor.getMember("value"))

        val propertyDescriptorNode = descriptorSupplier()
        propertyDescriptorNode.addValue(newPropertyDescriptor)
        propertyDescriptorNode.addHandler(object : NodeEventHandler {
            override fun valueAdded(value: Value) {
                value.getMember("set").addHandler(object : NodeEventHandler {
                    override fun valueAdded(value: Value) {
                        val setter = value
                        setter.getParameter(0).addValue(this@writeProperty)
                        newValue.connectTo(setter.getParameter(1))
                        processFunctionIfNecessary(setter)
                    }
                })
            }
        })
    }

    private fun nodeOfOneValue(value: Value): Node {
        val node = NodeImpl(value.jsNode, value.path)
        node.addValue(value)
        return node
    }

    private fun propertyDescriptorOfOneValue(value: Value): Value {
        val pd = ValueImpl(value.jsNode, "${value.path}#descriptor")
        pd.getMember("value").addValue(value)
        return pd
    }

    private fun defer(action: () -> Unit) {
        worklist += action
    }

    internal inner class ValueImpl(
            override val jsNode: JsNode?,
            override val path: String,
            override val stringConstant: String? = null
    ) : Value {
        private var members: MutableMap<String, NodeImpl>? = null
        private var dynamicMemberImpl: NodeImpl? = null
        private var parameters: MutableList<NodeImpl?>? = null
        private var returnValueImpl: NodeImpl? = null
        private var handlers: MutableList<ValueEventHandler>? = null

        override fun getMember(name: String): NodeImpl {
            val members = this.members ?: mutableMapOf<String, NodeImpl>().also { this.members = it }
            return members.getOrPut(name) {
                NodeImpl(jsNode, "$path.$name").also { newNode ->
                    defer { handlers?.toList()?.forEach { it.memberAdded(name, newNode) } }
                }
            }
        }

        override fun getDynamicMember(): NodeImpl {
            return dynamicMemberImpl ?: NodeImpl(jsNode, "$path[*]").also { newNode ->
                dynamicMemberImpl = newNode
                defer { handlers?.toList()?.forEach { it.dynamicMemberAdded(newNode) } }
                addHandler(object : ValueEventHandler {
                    override fun memberAdded(name: String, value: Node) {
                        if (name != PROTO) {
                            newNode.connectTo(value)
                            value.connectTo(newNode)
                        }
                    }
                })
            }
        }

        override fun getAllMembers(): List<Node> {
            val dynamicMember = dynamicMemberImpl
            return if (dynamicMember != null) listOf(dynamicMember) else members?.values?.toList().orEmpty()
        }

        override fun getMemberIfExists(name: String): Node? {
            return dynamicMemberImpl ?: members?.get(name)
        }

        override fun hasMember(name: String): Boolean = dynamicMemberImpl != null || members?.containsKey(name) ?: false

        override fun getParameter(index: Int): NodeImpl {
            val list = parameters ?: mutableListOf<NodeImpl?>().also { parameters = it }
            while (list.lastIndex < index) {
                list.add(null)
            }
            return list[index] ?: NodeImpl(jsNode, "$path|$index").also { param ->
                list[index] = param
                defer { handlers?.toList()?.forEach { it.parameterAdded(index, param) } }
            }
        }

        override fun getReturnValue(): NodeImpl {
            return returnValueImpl ?: NodeImpl(jsNode, "$path|return").also { newReturnValue ->
                returnValueImpl = newReturnValue
                defer { handlers?.toList()?.forEach { it.returnValueAdded(newReturnValue) } }
            }
        }

        override fun addHandler(handler: ValueEventHandler) {
            val list = handlers ?: mutableListOf<ValueEventHandler>().also { handlers = it }
            list += handler

            dynamicMemberImpl?.let { dynamicMember ->
                defer { handler.dynamicMemberAdded(dynamicMember) }
            }

            members?.let {
                for ((name, value) in it) {
                    defer { handler.memberAdded(name, value) }
                }
            }

            parameters?.let {
                for ((index, param) in it.withIndex()) {
                    if (param != null) {
                        defer { handler.parameterAdded(index, param) }
                    }
                }
            }

            returnValueImpl?.let {
                defer { handler.returnValueAdded(it) }
            }

            if (isUsed) {
                defer { handler.used() }
            }
        }

        override var isUsed: Boolean = false
            get
            private set

        override fun use() {
            if (!isUsed) {
                isUsed = true
                for (handler in handlers.orEmpty()) {
                    handler.used()
                }
            }
        }
    }

    internal inner class NodeImpl(override val jsNode: JsNode?, override val path: String) : Node {
        private var values: MutableSet<Value>? = null
        private var handlers: MutableList<NodeEventHandler>? = null
        private var successors: MutableSet<Node>? = null

        override fun getValues(): Set<Value> = values ?: emptySet()

        override fun addValue(value: Value) {
            val set = values ?: mutableSetOf<Value>().also { values = it }
            if (set.add(value)) {
                if (isUsed) {
                    value.use()
                }
                defer { handlers?.toList()?.forEach { it.valueAdded(value) } }
            }
        }

        override fun connectTo(other: Node) {
            val successors = this.successors ?: mutableSetOf<Node>().also { this.successors = it }
            if (successors.add(other)) {
                addHandler(object : NodeEventHandler {
                    override fun valueAdded(value: Value) {
                        other.addValue(value)
                    }
                })
            }
        }

        override fun addHandler(handler: NodeEventHandler) {
            val list = handlers ?: mutableListOf<NodeEventHandler>().also { handlers = it }
            list += handler

            values?.forEach { value ->
                defer { handler.valueAdded(value) }
            }
        }

        override var isUsed: Boolean = false
            get
            private set

        override fun use() {
            if (!isUsed) {
                isUsed = true
                for (value in values.orEmpty()) {
                    value.use()
                }
            }
        }
    }
}