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

    private val nodesToEliminate = mutableSetOf<JsNode>()

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

        for (globalValue in listOf(globalScope, objectValue, functionValue, arrayValue, functionPrototypeValue)) {
            spreadGlobalValue(globalValue)
        }

        root.accept(visitor)
        var lastSize = 0
        while (worklist.isNotEmpty()) {
            if (Math.abs(lastSize - worklist.size) > 100) {
                println(worklist.size)
                lastSize = worklist.size
            }
            worklist.remove()()
        }

        marker.accept(root)
        eliminator.accept(root)
    }

    private fun spreadGlobalValue(value: Value): Value {
        value.addHandler(object : ValueEventHandler {
            override fun memberAdded(name: String, value: Node) {
                if (value.getValues().isEmpty()) {
                    value.addValue(spreadGlobalValue(ValueImpl(value.jsNode, "${value.path}.$name")))
                }
            }

            override fun returnValueAdded(value: Node) {
                if (value.getValues().isEmpty()) {
                    value.addValue(spreadGlobalValue(ValueImpl(value.jsNode, "${value.path}|return")))
                }
            }
        })
        return value
    }

    private val marker = object : RecursiveJsVisitor() {
        override fun visitExpressionStatement(x: JsExpressionStatement) {
            super.visitExpressionStatement(x)
            if (x.expression in nodesToEliminate) {
                nodesToEliminate += x
            }
        }

        override fun visitFunction(x: JsFunction) {
            if (shouldRemoveFunction(x)) {
                nodesToEliminate += x
            }
            super.visitFunction(x)
        }

        override fun visitObjectLiteral(x: JsObjectLiteral) {
            super.visitObjectLiteral(x)
            for (propertyInitializer in x.propertyInitializers) {
                if (propertyInitializer.valueExpr in nodesToEliminate &&
                    (propertyInitializer.labelExpr is JsNameRef || propertyInitializer.labelExpr is JsStringLiteral)
                ) {
                    nodesToEliminate += propertyInitializer
                }
            }
            if (shouldRemoveNode(x) && x.propertyInitializers.all { it in nodesToEliminate }) {
                nodesToEliminate += x
            }
        }

        override fun visitBinaryExpression(x: JsBinaryOperation) {
            super.visitBinaryExpression(x)
            if (x.operator == JsBinaryOperator.ASG) {
                val lhs = x.arg1
                if (lhs is JsNameRef) {
                    val qualifier = lhs.qualifier
                    if (qualifier != null && shouldRemoveNode(qualifier)) {
                        nodesToEliminate += lhs
                    }
                }
                if (x.arg1 in nodesToEliminate || x.arg2 in nodesToEliminate) {
                    nodesToEliminate += x
                }
            }
        }

        override fun visitVars(x: JsVars) {
            super.visitVars(x)
            if (x.vars.all { it in nodesToEliminate }) {
                nodesToEliminate.add(x)
            }
        }

        override fun visit(x: JsVars.JsVar) {
            super.visit(x)
            val node = nodes[x.name]
            if (node != null && !hasUsedValues(node)) {
                nodesToEliminate += x
            }
        }

        override fun visitNameRef(nameRef: JsNameRef) {
            super.visitNameRef(nameRef)
            if (shouldRemoveNode(nameRef)) {
                nodesToEliminate += nameRef
            }
        }

        override fun visitInvocation(invocation: JsInvocation) {
            super.visitInvocation(invocation)
            if (isDefinePropertyUnused(invocation)) {
                nodesToEliminate += invocation
            }
        }

        private fun isDefinePropertyUnused(invocation: JsInvocation): Boolean {
            val node = nodeMap[invocation.qualifier] ?: return false
            val functions = node.getValues()
            if (functions.singleOrNull() != objectDefineProperty) return false

            val descriptorArg = invocation.arguments.getOrNull(2) ?: return false
            val descriptorNode = nodeMap[descriptorArg] ?: return false
            return !hasUsedValues(descriptorNode)
        }
    }

    private val eliminator = object : JsVisitorWithContextImpl() {
        override fun endVisit(x: JsExpressionStatement, ctx: JsContext<in JsNode>) {
            if (x in nodesToEliminate) {
                ctx.removeMe()
            }
        }

        override fun endVisit(x: JsObjectLiteral, ctx: JsContext<in JsNode>) {
            if (x in nodesToEliminate) {
                ctx.replaceMe(JsLiteral.NULL)
            }
            else {
                x.propertyInitializers.removeAll { it in nodesToEliminate }
            }
        }

        override fun endVisit(x: JsNameRef, ctx: JsContext<in JsNode>) {
            if (x in nodesToEliminate) {
                ctx.replaceMe(JsLiteral.NULL)
            }
        }

        override fun endVisit(x: JsVars, ctx: JsContext<in JsNode>) {
            if (x in nodesToEliminate) {
                ctx.removeMe()
            }
            else {
                x.vars.removeAll { it in nodesToEliminate }
            }
        }

        override fun endVisit(x: JsFunction, ctx: JsContext<in JsNode>) {
            if (x in nodesToEliminate) {
                ctx.replaceMe(JsLiteral.NULL)
            }
        }

        override fun endVisit(x: JsBinaryOperation, ctx: JsContext<in JsNode>) {
            if (x in nodesToEliminate) {
                ctx.replaceMe(JsLiteral.NULL)
            }
        }
    }

    private fun shouldRemoveFunction(x: JsFunction): Boolean {
        if (x !in processedFunctions) {
            if (shouldRemoveNode(x)) return true
            x.body.statements.clear()
            x.parameters.clear()
        }
        return false
    }

    private fun shouldRemoveNode(x: JsNode): Boolean {
        val node = nodeMap[x]
        return node != null && !hasUsedValues(node)
    }

    private fun hasUsedValues(node: Node) = node.getValues().any { it.isUsed }

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
        }

        override fun visitFunction(x: JsFunction) {
            val value = constructObject(functionNode, emptyList(), x)
            val prototypeValue = constructObject(objectNode, emptyList(), null)
            val prototypeDescriptor = ValueImpl(x, "${value.path}.prototype#descriptor")
            prototypeDescriptor.getMember("value").addValue(prototypeValue)
            value.getMember("prototype").addValue(prototypeDescriptor)

            valueMap[x] = value
            resultNode = createNode(x)
            x.name?.let {
                nodes[it] = resultNode
            }
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
                createNode(x).also { nodes[iterVarName] = it }
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
                val propertyDescriptor = value
                newValue.connectTo(propertyDescriptor.getMember("value"))
                propertyDescriptor.getMember("set").addHandler(object : NodeEventHandler {
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