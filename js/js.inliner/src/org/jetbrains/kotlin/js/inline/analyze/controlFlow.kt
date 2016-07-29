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
import org.jetbrains.kotlin.utils.DFS

interface GraphNode<T> {
    val data: T

    val successors: Set<GraphNode<T>>

    val predecessors: Set<GraphNode<T>>
}

enum class ConnectionType {
    /**
     * Marks successor which acts as exception handler of current block. Control flow may be passed to exception handler
     * after any instruction of the block.
     */
    EXCEPTION_HANDLER,

    /**
     * Marks successor which acts as a finally block to the current block. Control flow is passed to finally block
     * right before return, throw or last statement of inner finally block (in case of abnormal exit), and then returns
     * back to the last instruction.
     */
    FINALLY_BLOCK
}

class BasicBlock : GraphNode<List<JsNode>> {
    private var mutableSuccessors: MutableSet<BasicBlock>? = null
    private var mutablePredecessors: MutableSet<BasicBlock>? = null
    private var ordinaryMutableSuccessors: MutableSet<BasicBlock>? = null
    private var typedMutableSuccessors: MutableMap<ConnectionType, BasicBlock>? = null

    /**
     * Represents AST nodes that act as instructions in basic blocks. Instructions follow in the order of execution, i.e.
     * for expression like `2 + 3` we get three instructions in the order: `2`, `3` and `+`.
     *
     * Some instructions have special meaning when they are the last instructions of the block:
     *
     *   * [JsIf] and [JsSwitch] indicates that control flow may be passed to one of the successors, depending on the condition.
     *   * [JsConditional] is similar to [JsIf], except for some other block contains duplicate of [JsConditional]
     *     as the first instruction which yields value of `? :` expression.
     *   * [JsReturn] and [JsThrow] indicates that control flow goes to `finally` blocks and then stops.
     *   * [JsBinaryOperation] is similar is [JsConditional] in case of AND and OR operations.
     *
     * If a block has [JsTry] as its first instruction, this block is a *landing pad* of exception handler.
     */
    val nodes = mutableListOf<JsNode>()

    var finallyNode: JsTry? = null

    override val data: List<JsNode>
        get() = nodes

    /**
     * Represents all successors, no matter of what type they are. See [ordinarySuccessors] and [typedSuccessors].
     */
    override val successors: Set<BasicBlock>
        get() = mutableSuccessors.orEmpty()

    /**
     * Represents ordinary control flow successors. They represent normal control flow from the last instruction,
     * like `if`, `switch`, etc. Control passed to these successors right after the last instruction of block gets executed.
     */
    val ordinarySuccessors: Set<BasicBlock>
        get() = ordinaryMutableSuccessors.orEmpty()

    override val predecessors: Set<BasicBlock>
        get() = mutablePredecessors.orEmpty()

    /**
     * Represents typed successors which don't participate in normal control flow. See [ConnectionType].
     */
    val typedSuccessors: Map<ConnectionType, BasicBlock>
        get() = typedMutableSuccessors.orEmpty()

    var exceptionHandler: BasicBlock?
        get() = typedSuccessors[ConnectionType.EXCEPTION_HANDLER]
        set(value) {
            if (value != null) {
                connectTo(value, ConnectionType.EXCEPTION_HANDLER)
            }
            else {
                val oldValue = exceptionHandler
                if (oldValue != null) {
                    disconnectFrom(oldValue, ConnectionType.EXCEPTION_HANDLER)
                }
            }
        }

    var finallyBlock: BasicBlock?
        get() = typedSuccessors[ConnectionType.FINALLY_BLOCK]
        set(value) {
            if (value != null) {
                connectTo(value, ConnectionType.FINALLY_BLOCK)
            }
            else {
                val oldValue = exceptionHandler
                if (oldValue != null) {
                    disconnectFrom(oldValue, ConnectionType.FINALLY_BLOCK)
                }
            }
        }

    fun connectTo(other: BasicBlock, type: ConnectionType? = null) {
        connectToImpl(other)
        if (type == null) {
            val ordinarySuccessors = ordinaryMutableSuccessors ?: mutableSetOf<BasicBlock>().apply { ordinaryMutableSuccessors = this }
            ordinarySuccessors.add(other)
        }
        else {
            val typedSuccessors = typedMutableSuccessors ?:
                                  mutableMapOf<ConnectionType, BasicBlock>().apply { typedMutableSuccessors = this }
            val oldSuccessor = typedSuccessors[type]
            if (oldSuccessor != null && oldSuccessor != other) {
                disconnectFrom(oldSuccessor, type)
            }
            typedSuccessors[type] = other
        }
    }

    private fun connectToImpl(other: BasicBlock) {
        val successors = mutableSuccessors ?: mutableSetOf<BasicBlock>().apply { mutableSuccessors = this }
        successors.add(other)

        val predecessors = other.mutablePredecessors ?: mutableSetOf<BasicBlock>().apply { other.mutablePredecessors = this }
        predecessors.add(this)
    }

    fun disconnectFrom(other: BasicBlock, type: ConnectionType? = null) {
        if (type == null) {
            ordinaryMutableSuccessors?.remove(other)
        }
        else {
            val typedSuccessors = typedMutableSuccessors
            if (typedSuccessors != null) {
                if (typedSuccessors[type] == other) {
                    typedSuccessors.remove(type)
                }
            }
        }
        if (other !in ordinaryMutableSuccessors.orEmpty() && other !in typedMutableSuccessors?.values.orEmpty()) {
            mutableSuccessors?.remove(other)
            other.mutablePredecessors?.remove(this)
        }
    }

    fun disconnectFromAny(other: BasicBlock) {
        ordinaryMutableSuccessors?.remove(other)
        val typedSuccessors = typedMutableSuccessors
        if (typedSuccessors != null) {
            for (type in typedSuccessors.keys.toList()) {
                if (typedSuccessors[type] == other) {
                    typedSuccessors.remove(type)
                }
            }
        }
        mutableSuccessors?.remove(other)
        other.mutablePredecessors?.remove(this)
    }

    val isExceptionHandler: Boolean
        get() = nodes.isNotEmpty() && nodes[0] is JsCatch

    val isTerminal: Boolean
        get() = successors.isEmpty()
}

fun BasicBlock.getReachableBlocks(): List<BasicBlock> {
    val reachable = mutableListOf<BasicBlock>()
    DFS.dfs(listOf(this), DFS.Neighbors { it.successors.reversed() }, object : DFS.AbstractNodeHandler<BasicBlock, Unit>() {
        override fun afterChildren(current: BasicBlock?) {
            if (current != null) {
                reachable += current
            }
        }
        override fun result() = Unit
    })

    return reachable.asReversed()
}