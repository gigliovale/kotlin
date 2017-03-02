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
import java.util.*

class DeadCodeElimination(private val root: JsStatement, program: JsProgram) {
    private val nodes = mutableMapOf<JsName, Node>()
    private val worklist: Queue<() -> Unit> = ArrayDeque()
    private val functionNodes = mutableMapOf<JsFunction, Node>()
    private val processedFunctions = mutableSetOf<JsFunction>()
    private val nodeMap = mutableMapOf<JsNode, Node>()
    private val globalScopeCache = mutableMapOf<String, Node>()
    private val globalNode = NodeImpl(null, "")

    companion object {
        private val PROTO = "__proto__"
    }

    val globalScopeContributors = mutableListOf<GlobalScopeContributor>()

    fun apply() {
        root.accept(visitor)
        while (worklist.isNotEmpty()) {
            worklist.remove()()
        }
        eliminator.accept(root)
    }

    private val eliminator = object : JsVisitorWithContextImpl() {
        override fun endVisit(x: JsFunction, ctx: JsContext<in JsNode>) {
            if (shouldRemoveFunction(x)) {
                ctx.replaceMe(JsPrefixOperation(JsUnaryOperator.VOID, program.getNumberLiteral(0)))
            }
        }

        override fun visit(x: JsExpressionStatement, ctx: JsContext<*>): Boolean {
            val expression = x.expression
            return expression !is JsFunction || !shouldRemoveFunction(expression)
        }

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
                val node = functionNodes[x]!!
                if (!node.isUsed) {
                    return true
                }
                else {
                    x.body.statements.clear()
                    x.parameters.clear()
                }
            }
            return false
        }

        override fun endVisit(x: JsObjectLiteral, ctx: JsContext<*>) {
            x.propertyInitializers.removeAll { property ->
                val node = nodeMap[property.valueExpr]
                node != null && !node.isUsed
            }
        }
    }

    private val dynamicNode: Node by lazy {
        val node = NodeImpl(null, "[dynamic]")
        node.addHandler(object : NodeEventHandler {
            override fun memberAdded(name: String, value: Node) {
                value.connectTo(node)
                node.connectTo(value)
            }

            override fun parameterAdded(index: Int, value: Node) {
                value.connectTo(dynamicNode)
            }

            override fun returnValueAdded(value: Node) {
                dynamicNode.connectTo(value)
            }

            override fun dynamicMemberAdded(value: Node) {
                value.connectTo(node)
                node.connectTo(value)
            }
        })
        node.use()
        node
    }

    private var resultNodes: List<Node> = listOf()
    private var returnNode: Node? = null
    private var thisNode: Node = globalNode
    private val visitor = object : RecursiveJsVisitor() {
        override fun visitBinaryExpression(x: JsBinaryOperation) {
            if (x.operator == JsBinaryOperator.ASG) {
                x.arg1.accept(this)
                val lhsNodes = resultNodes
                x.arg2.accept(this)
                val rhsNodes = resultNodes
                for (lhsNode in lhsNodes) {
                    for (rhsNode in rhsNodes) {
                        rhsNode.connectTo(lhsNode)
                    }
                }
            }
            else if (x.operator == JsBinaryOperator.OR) {
                x.arg1.accept(this)
                val first = resultNodes
                x.arg2.accept(this)
                val second = resultNodes
                resultNodes = first + second
            }
            else {
                super.visitBinaryExpression(x)
            }
        }

        override fun visitFunction(x: JsFunction) {
            val node = NodeImpl(x, "")
            val tag = FunctionTag(x)
            /*setType(node, getGlobalScopeNode("Function"))
            val prototypeMember = node.getMember("prototype")

            val prototypeNode = NodeImpl(null)
            setType(prototypeNode, getGlobalScopeNode("Object"))
            prototypeNode.getMember("constructor").addFunction(tag)
            prototypeNode.connectTo(prototypeMember)*/

            functionNodes[x] = node
            x.name?.let {
                nodes[it] = node
            }
            node.addFunction(tag)
            resultNodes = listOf(node)
        }

        override fun visitObjectLiteral(x: JsObjectLiteral) {
            val node = NodeImpl(x, "")
            //setType(node, getGlobalScopeNode("Object"))
            for (propertyInitializer in x.propertyInitializers) {
                propertyInitializer.valueExpr.accept(this)
                val propertyNodes = resultNodes
                val label = propertyInitializer.labelExpr
                when (label) {
                    is JsNameRef -> {
                        propertyNodes.forEach { it.connectTo(node.getMember(label.ident)) }
                    }
                    is JsStringLiteral -> {
                        propertyNodes.forEach { it.connectTo(node.getMember(label.value)) }
                    }
                    else -> {
                        propertyNodes.forEach { it.connectTo(node.getDynamicMember()) }
                    }
                }
            }
            resultNodes = listOf(node)
        }

        override fun visit(x: JsVars.JsVar) {
            val node = NodeImpl(x, "")
            nodes[x.name] = node
            x.initExpression?.let {
                accept(it)
                resultNodes.forEach { it.connectTo(node) }
            }
            resultNodes = listOf()
        }

        override fun visitNameRef(nameRef: JsNameRef) {
            val qualifier = nameRef.qualifier
            resultNodes = if (qualifier == null) {
                val name = nameRef.name
                val node = if (name != null) {
                    nodes[name]
                }
                else {
                    dynamicNode
                }
                node?.let { nodeMap[nameRef] = it }
                listOfNotNull(node)
            }
            else {
                accept(qualifier)
                resultNodes.map { resultNode ->
                    resultNode.getMember(nameRef.ident).also { nodeMap[nameRef] = it }
                }
            }
        }

        override fun visitArrayAccess(x: JsArrayAccess) {
            accept(x.arrayExpression)
            val arrayNodes = resultNodes

            val indexExpr = x.indexExpression
            resultNodes = if (indexExpr is JsStringLiteral) {
                arrayNodes.map { it.getMember(indexExpr.value) }
            }
            else {
                accept(indexExpr)
                resultNodes.forEach { it.use() }
                arrayNodes.map { it.getDynamicMember() }
            }
        }

        override fun visitInvocation(invocation: JsInvocation) {
            val node = NodeImpl(invocation, "")
            val qualifier = invocation.qualifier

            val (qualifierNodes, receiverNodes) = if (qualifier is JsNameRef) {
                accept(qualifier.qualifier)
                Pair(resultNodes.map { it.getMember(qualifier.ident) }, resultNodes)
            }
            else {
                accept(qualifier)
                Pair(resultNodes, emptyList())
            }
            val argumentsNodes = invocation.arguments.map {
                accept(it)
                resultNodes
            }

            resultNodes = qualifierNodes.map { qualifierNode ->
                qualifierNode.addHandler(object : NodeEventHandler {
                    override fun functionAdded(function: FunctionTag) {
                        val jsFunction = function.jsFunction
                        if (jsFunction != null) {
                            val functionNode = functionNodes[jsFunction]!!
                            functionNode.connectTo(functionNode)
                            if (processedFunctions.add(jsFunction)) {
                                processFunction(jsFunction, functionNode)
                            }
                            functionNode.use()
                        }
                    }
                })
                for ((argumentIndex, argumentNodes) in argumentsNodes.withIndex()) {
                    argumentNodes.forEach { it.connectTo(qualifierNode.getParameter(argumentIndex + 1)) }
                }
                receiverNodes.forEach { it.connectTo(qualifierNode.getParameter(0)) }
                node
            }
        }

        override fun visitReturn(x: JsReturn) {
            x.expression?.let {
                accept(it)
                resultNodes.forEach { resultNode ->
                    returnNode?.let { resultNode.connectTo(it) }
                }
            }
            resultNodes = emptyList()
        }

        override fun visitNew(x: JsNew) {
            val node = NodeImpl(x, "")
            accept(x.constructorExpression)
            val constructorNodes = resultNodes
            val prototypeNodes = resultNodes.map { it.getMember("prototype") }

            prototypeNodes.forEach { setType(node, it) }
            constructorNodes.forEach { it.use() }

            resultNodes = listOf(node)
        }

        override fun visitThis(x: JsLiteral.JsThisRef) {
            resultNodes = listOf(thisNode)
        }
    }

    private fun setType(node: Node, type: Node) {
        type.connectTo(node.getMember(PROTO))
        type.addHandler(object : NodeEventHandler {
            override fun memberAdded(name: String, value: Node) {
                value.connectTo(node.getMember(name))
            }

            override fun dynamicMemberAdded(value: Node) {
                value.connectTo(node.getDynamicMember())
            }
        })
    }

    private fun getGlobalScopeNode(name: String): Node = globalScopeCache.getOrPut(name) {
        val existingObject = globalScopeContributors.mapNotNull { it.getGlobalObjectNode({ NodeImpl(null, "") }, name) }.firstOrNull()
        existingObject ?: NodeImpl(null, "").also { dynamicNode.connectTo(it) }
    }

    private fun processFunction(function: JsFunction, node: Node) {
        returnNode = node.getReturnValue()
        for ((index, param) in function.parameters.withIndex()) {
            val parameterNode = NodeImpl(param, "")
            nodes[param.name] = parameterNode
            node.getParameter(index + 1).connectTo(parameterNode)
        }
        function.body.accept(visitor)
    }

    private fun defer(action: () -> Unit) {
        worklist += action
    }

    internal inner class NodeImpl(override val jsNode: JsNode?, val path: String) : Node {
        private var functions: MutableSet<FunctionTag>? = null
        private var members: MutableMap<String, NodeImpl>? = null
        private var dynamicMemberImpl: NodeImpl? = null
        private var parameters: MutableList<NodeImpl?>? = null
        private var returnValueImpl: NodeImpl? = null
        private var handlers: MutableList<NodeEventHandler>? = null
        private var successors: MutableSet<Node>? = null

        init {
            if (jsNode != null) {
                nodeMap[jsNode] = this
            }
        }

        override var isUsed: Boolean = false
            get
            private set

        override fun getFunctions(): Set<FunctionTag> = functions ?: emptySet()

        override fun addFunction(function: FunctionTag) {
            val set = functions ?: mutableSetOf<FunctionTag>().also { functions = it }
            if (set.add(function)) {
                defer { handlers?.toList()?.forEach { it.functionAdded(function) } }
            }
        }

        override fun getMember(name: String): NodeImpl {
            val members = this.members ?: mutableMapOf<String, NodeImpl>().also { this.members = it }
            return members.getOrPut(name) {
                NodeImpl(jsNode, "$path.$name").also { newNode ->
                    newNode.addHandler(object : NodeEventHandler {
                        override fun used(value: Node) {
                            use()
                        }
                    })
                    defer { handlers?.toList()?.forEach { it.memberAdded(name, newNode) } }
                }
            }
        }

        override fun getDynamicMember(): NodeImpl {
            return dynamicMemberImpl ?: NodeImpl(jsNode, "$path.*").also { newNode ->
                dynamicMemberImpl = newNode
                defer { handlers?.toList()?.forEach { it.dynamicMemberAdded(newNode) } }
                addHandler(object : NodeEventHandler {
                    override fun memberAdded(name: String, value: Node) {
                        newNode.connectTo(value)
                        value.connectTo(newNode)
                    }

                    override fun used(value: Node) {
                        use()
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

        override fun connectTo(other: Node) {
            val successors = this.successors ?: mutableSetOf<Node>().also { this.successors = it }
            if (successors.add(other)) {
                addHandler(object : NodeEventHandler {
                    override fun functionAdded(function: FunctionTag) {
                        other.addFunction(function)
                    }

                    override fun parameterAdded(index: Int, value: Node) {
                        value.connectTo(other.getParameter(index))
                    }

                    override fun returnValueAdded(value: Node) {
                        getReturnValue().connectTo(value)
                    }

                    override fun dynamicMemberAdded(value: Node) {
                        other.getDynamicMember().connectTo(value)
                        value.connectTo(other.getDynamicMember())
                    }

                    override fun memberAdded(name: String, value: Node) {
                        value.connectTo(other.getMember(name))
                        other.getMember(name).connectTo(value)
                    }
                })
                other.addHandler(object : NodeEventHandler {
                    override fun returnValueAdded(value: Node) {
                        value.connectTo(getReturnValue())
                    }

                    override fun dynamicMemberAdded(value: Node) {
                        getDynamicMember().connectTo(value)
                        value.connectTo(getDynamicMember())
                    }

                    override fun memberAdded(name: String, value: Node) {
                        value.connectTo(getMember(name))
                        getMember(name).connectTo(value)
                    }
                })
            }
        }

        override fun addHandler(handler: NodeEventHandler) {
            val list = handlers ?: mutableListOf<NodeEventHandler>().also { handlers = it }
            list += handler

            functions?.forEach { function ->
                defer { handler.functionAdded(function) }
            }

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
                defer { handler.used(this) }
            }
        }

        override fun use() {
            if (!isUsed) {
                isUsed = true
                defer { handlers?.toList()?.forEach { it.used(this) } }
            }
        }
    }
}