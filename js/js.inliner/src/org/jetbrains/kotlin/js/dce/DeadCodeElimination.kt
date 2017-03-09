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
import org.jetbrains.kotlin.js.translate.utils.jsAstUtils.array
import org.jetbrains.kotlin.js.translate.utils.jsAstUtils.index
import java.util.*

class DeadCodeElimination(private val root: JsStatement) {
    private val nodes = mutableMapOf<JsName, Node>()
    private val worklist: Queue<() -> Unit> = ArrayDeque()
    private val processedFunctions = mutableSetOf<JsFunction>()
    private val nodeMap = mutableMapOf<JsNode, Node>()
    private val valueMap = mutableMapOf<JsNode, Value>()
    private val globalScope = ValueImpl(null)
    private val globalScopeNode = NodeImpl(null, "")

    private val objectValue = ValueImpl(null)
    private val functionValue = ValueImpl(null)
    private val functionPrototypeValue = ValueImpl(null)
    private val arrayValue = ValueImpl(null)
    private val primitiveValue = ValueImpl(null)
    private val objectCreateValue = ValueImpl(null)
    private val functionApplyValue = ValueImpl(null)
    private val functionCallValue = ValueImpl(null)

    companion object {
        private val PROTO = "__proto__"
    }

    fun apply() {
        primitiveValue.use()
        dynamicValue.use()

        globalScopeNode.addValue(globalScope)
        globalScope.getMember("Object").addValue(objectValue)
        globalScope.getMember("Function").addValue(functionValue)
        globalScope.getMember("Array").addValue(arrayValue)

        objectValue.getMember("create").addValue(objectCreateValue)

        functionValue.getMember("prototype").addValue(functionPrototypeValue)
        functionPrototypeValue.getMember("apply").addValue(functionApplyValue)
        functionPrototypeValue.getMember("call").addValue(functionCallValue)

        root.accept(visitor)
        while (worklist.isNotEmpty()) {
            worklist.remove()()
        }
        eliminator.accept(root)
    }

    private val eliminator = object : JsVisitorWithContextImpl() {
        override fun endVisit(x: JsExpressionStatement, ctx: JsContext<in JsNode>) {
            val expression = x.expression
            if (expression is JsFunction && shouldRemoveFunction(expression)) {
                ctx.removeMe()
            }
            else {
                super.endVisit(x, ctx)
            }
        }

        private fun shouldRemoveFunction(x: JsFunction): Boolean {
            if (x !in processedFunctions) {
                val node = nodeMap[x]
                if (node != null && !hasUsedValues(node)) return true
                x.body.statements.clear()
                x.parameters.clear()
            }
            return false
        }

        override fun endVisit(x: JsObjectLiteral, ctx: JsContext<*>) {
            x.propertyInitializers.removeAll { prop ->
                val label = prop.labelExpr
                val value = prop.valueExpr
                if (label is JsStringLiteral) {
                    val node = nodeMap[value]
                    if (value is JsFunction && shouldRemoveFunction(value) || node != null && !hasUsedValues(node)) {
                        true
                    }
                    else {
                        accept(value)
                        false
                    }
                }
                else {
                    accept(label)
                    accept(value)
                    false
                }
            }
        }

        override fun endVisit(x: JsNameRef, ctx: JsContext<in JsNode>) {
            val node = x.name?.let { nodes[it] }
            if (node != null && !hasUsedValues(node)) {
                ctx.replaceMe(JsLiteral.NULL)
            }
        }

        private fun hasUsedValues(node: Node) = node.getValues().any { it.isUsed }
    }

    private val dynamicValue: Value by lazy {
        val newValue = ValueImpl(null)
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
                handleAssignment(x.arg1, x.arg2)
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

        private fun handleAssignment(leftExpr: JsExpression, rightExpr: JsExpression) {
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
                                rhsNode.connectTo(value.getMember(leftExpr.ident))
                            }
                        })
                    }
                }
                is JsArrayAccess -> {
                    accept(leftExpr.array)
                    val arrayNode = resultNode
                    accept(leftExpr.index)
                    arrayNode.addHandler(object : NodeEventHandler {
                        override fun valueAdded(value: Value) {
                            rhsNode.connectTo(value.getDynamicMember())
                        }
                    })
                }
                else -> error("Unexpected LHS expression: $leftExpr")
            }
        }

        override fun visitFunction(x: JsFunction) {
            val value = constructObject(globalScope.getMember("Function"), emptyList(), x)
            val prototypeValue = constructObject(globalScope.getMember("Object"), emptyList(), x)
            value.getMember("prototype").addValue(prototypeValue)

            valueMap[x] = value
            resultNode = createNode(x)
            x.name?.let {
                nodes[it] = resultNode
            }
            resultNode.addValue(value)
        }

        override fun visitObjectLiteral(x: JsObjectLiteral) {
            val objectValue = constructObject(globalScope.getMember("Object"), listOf(), x)
            for (propertyInitializer in x.propertyInitializers) {
                accept(propertyInitializer.valueExpr)
                val propertyNode = resultNode
                val label = propertyInitializer.labelExpr
                when (label) {
                    is JsNameRef -> {
                        propertyNode.connectTo(objectValue.getMember(label.ident))
                    }
                    is JsStringLiteral -> {
                        propertyNode.connectTo(objectValue.getMember(label.value))
                    }
                    else -> {
                        propertyNode.connectTo(objectValue.getDynamicMember())
                    }
                }
            }
            resultNode = createNode(x)
            resultNode.addValue(objectValue)
            objectValue.getMember(PROTO).addValue(objectValue)
        }

        override fun visitArray(x: JsArrayLiteral) {
            val arrayValue = constructObject(globalScope.getMember("Array"), listOf(), x)
            for (item in x.expressions) {
                accept(item)
                resultNode.connectTo(arrayValue.getDynamicMember())
            }
            resultNode = createNode(x)
            resultNode.addValue(arrayValue)
        }

        override fun visit(x: JsVars.JsVar) {
            val node = createNode(x)
            nodes[x.name] = node
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
                val node = name?.let { nodes[it] } ?: globalScope.getMember(nameRef.ident)
                node.also { nodeMap[nameRef] = it }
            }
            else {
                accept(qualifier)
                val newNode = createNode(nameRef)
                resultNode.addHandler(object : NodeEventHandler {
                    override fun valueAdded(value: Value) {
                        val memberNode = value.getMember(nameRef.ident)
                        memberNode.connectTo(newNode)
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
                        value.getMember(indexExpr.value).connectTo(newNode)
                    }
                })
            }
            else {
                accept(indexExpr)
                resultNode.use()
                arrayNode.addHandler(object : NodeEventHandler {
                    override fun valueAdded(value: Value) {
                        value.getDynamicMember().connectTo(newNode)
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
                            value.getMember(qualifier.ident).connectTo(qualifierNode)
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
            receiverNode.use()

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
            qualifierNode.use()

            resultNode = newNode
        }

        private fun processFunctionIfNecessary(value: Value) {
            val jsFunction = value.jsNode as? JsFunction
            if (jsFunction != null) {
                if (processedFunctions.add(jsFunction)) {
                    processFunction(jsFunction, value)
                }
            }
        }

        private fun handleObjectCreate(jsNode: JsNode?, prototypeNode: Node?, resultNode: Node) {
            val objectValue = ValueImpl(jsNode)

            prototypeNode?.addHandler(object : NodeEventHandler {
                override fun valueAdded(value: Value) {
                    value.addHandler(object : ValueEventHandler {
                        override fun memberAdded(name: String, value: Node) {
                            value.connectTo(objectValue.getMember(name))
                        }

                        override fun dynamicMemberAdded(value: Node) {
                            value.connectTo(objectValue.getDynamicMember())
                        }
                    })
                }
            })

            resultNode.addValue(objectValue)
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
                    val argumentsHub = NodeImpl(null, "")
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

            val argumentsNodes = x.arguments.map {
                accept(it)
                resultNode
            }

            val constructorNode = resultNode
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

        override fun visitString(x: JsStringLiteral) = handlePrimitive(x)

        override fun visitNull(x: JsNullLiteral) = handlePrimitive(x)

        override fun visitRegExp(x: JsRegExp) = handlePrimitive(x)

        private fun handlePrimitive(x: JsNode) {
            resultNode = createNode(x)
            resultNode.addValue(primitiveValue)
        }

        override fun visitThrow(x: JsThrow) {
            x.expression.accept(this)
            resultNode.use()
        }

        override fun visitForIn(x: JsForIn) {
            x.iterVarName?.let {
                val node = createNode(x)
                node.addValue(primitiveValue)
                nodes[it] = node
            }
            super.visitForIn(x)
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
        val objectValue = ValueImpl(jsNode)
        val prototypeNode = objectValue.getMember(PROTO)
        constructorNode.addHandler(object : NodeEventHandler {
            override fun valueAdded(value: Value) {
                value.getParameter(0).addValue(objectValue)
                for ((index, argumentNode) in argumentsNodes.withIndex()) {
                    argumentNode.connectTo(value.getParameter(index + 1))
                }

                val jsFunction = value.jsNode as? JsFunction
                if (jsFunction != null) {
                    if (processedFunctions.add(jsFunction)) {
                        processFunction(jsFunction, value)
                    }
                }
                value.getMember("prototype").connectTo(prototypeNode)
            }
        })

        prototypeNode.addHandler(object : NodeEventHandler {
            override fun valueAdded(value: Value) {
                value.addHandler(object : ValueEventHandler {
                    override fun memberAdded(name: String, value: Node) {
                        value.connectTo(objectValue.getMember(name))
                    }

                    override fun dynamicMemberAdded(value: Node) {
                        value.connectTo(objectValue.getDynamicMember())
                    }
                })
            }
        })

        return objectValue
    }

    private fun processFunction(function: JsFunction, value: Value) {
        returnNode = value.getReturnValue()
        for ((index, param) in function.parameters.withIndex()) {
            val parameterNode = createNode(param)
            nodes[param.name] = parameterNode
            value.getParameter(index + 1).connectTo(parameterNode)
        }
        thisNode = value.getParameter(0)
        function.body.accept(visitor)
    }

    private fun defer(action: () -> Unit) {
        worklist += action
    }

    internal inner class ValueImpl(override val jsNode: JsNode?) : Value {
        private var members: MutableMap<String, NodeImpl>? = null
        private var dynamicMemberImpl: NodeImpl? = null
        private var parameters: MutableList<NodeImpl?>? = null
        private var returnValueImpl: NodeImpl? = null
        private var handlers: MutableList<ValueEventHandler>? = null

        override fun getMember(name: String): NodeImpl {
            val members = this.members ?: mutableMapOf<String, NodeImpl>().also { this.members = it }
            return members.getOrPut(name) {
                NodeImpl(jsNode, ".$name").also { newNode ->
                    defer { handlers?.toList()?.forEach { it.memberAdded(name, newNode) } }
                }
            }
        }

        override fun getDynamicMember(): NodeImpl {
            return dynamicMemberImpl ?: NodeImpl(jsNode, ".*").also { newNode ->
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

        override fun getParameter(index: Int): NodeImpl {
            val list = parameters ?: mutableListOf<NodeImpl?>().also { parameters = it }
            while (list.lastIndex < index) {
                list.add(null)
            }
            return list[index] ?: NodeImpl(jsNode, "|$index").also { param ->
                list[index] = param
                defer { handlers?.toList()?.forEach { it.parameterAdded(index, param) } }
            }
        }

        override fun getReturnValue(): NodeImpl {
            return returnValueImpl ?: NodeImpl(jsNode, "|return").also { newReturnValue ->
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

    internal inner class NodeImpl(override val jsNode: JsNode?, val path: String) : Node {
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