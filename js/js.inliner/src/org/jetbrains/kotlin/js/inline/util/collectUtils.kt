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

package org.jetbrains.kotlin.js.inline.util

import com.google.dart.compiler.backend.js.ast.*
import com.google.dart.compiler.backend.js.ast.metadata.staticRef

import org.jetbrains.kotlin.js.inline.util.collectors.InstanceCollector
import org.jetbrains.kotlin.js.inline.util.collectors.PropertyCollector
import org.jetbrains.kotlin.js.translate.expression.*
import java.util.*

fun collectFunctionReferencesInside(scope: JsNode): List<JsName> =
        collectReferencedNames(scope).filter { it.staticRef is JsFunction }

private fun collectReferencedNames(scope: JsNode): Set<JsName> {
    val references = IdentitySet<JsName>()

    object : RecursiveJsVisitor() {
        override fun visitBreak(x: JsBreak) { }

        override fun visitContinue(x: JsContinue) { }

        override fun visit(x: JsVars.JsVar) {
            val initializer = x.initExpression
            if (initializer != null) {
                accept(initializer)
            }
        }

        override fun visitNameRef(nameRef: JsNameRef) {
            super.visitNameRef(nameRef)
            val name = nameRef.name
            if (name != null) {
                references += name
            }
        }
    }.accept(scope)

    return references
}

fun collectUsedNames(scope: JsNode): Set<JsName> {
    val references = IdentitySet<JsName>()

    object : RecursiveJsVisitor() {
        override fun visitBreak(x: JsBreak) { }

        override fun visitContinue(x: JsContinue) { }

        override fun visit(x: JsVars.JsVar) {
            val initializer = x.initExpression
            if (initializer != null) {
                accept(initializer)
            }
        }

        override fun visitNameRef(nameRef: JsNameRef) {
            super.visitNameRef(nameRef)
            val name = nameRef.name
            if (name != null && nameRef.qualifier == null) {
                references.add(name)
            }
        }

        override fun visitFunction(x: JsFunction) {
            references += x.collectFreeVariables()
        }
    }.accept(scope)

    return references
}

fun collectDefinedNames(scope: JsNode): Set<JsName> {
    val names: MutableMap<String, JsName> = HashMap()

    object : RecursiveJsVisitor() {
        override fun visit(x: JsVars.JsVar) {
            val initializer = x.initExpression
            if (initializer != null) {
                accept(initializer)
            }
            addNameIfNeeded(x.name)
        }

        override fun visitExpressionStatement(x: JsExpressionStatement) {
            val expression = x.expression
            if (expression is JsFunction) {
                val name = expression.name
                if (name != null) {
                    addNameIfNeeded(name)
                }
            }
            super.visitExpressionStatement(x)
        }

        // Skip function expression, since it does not introduce name in scope of containing function.
        // The only exception is function statement, that is handled with the code above.
        override fun visitFunction(x: JsFunction) { }

        private fun addNameIfNeeded(name: JsName) {
            val ident = name.ident
            val nameCollected = names[ident]
            assert(nameCollected == null || nameCollected === name) { "ambiguous identifier $name" }
            names[ident] = name
        }
    }.accept(scope)

    return names.values.toSet()
}

fun JsFunction.collectFreeVariables() = collectUsedNames(body) - collectDefinedNames(body) - parameters.map { it.name }

fun collectJsProperties(scope: JsNode): IdentityHashMap<JsName, JsExpression> {
    val collector = PropertyCollector()
    collector.accept(scope)
    return collector.properties
}

fun collectNamedFunctions(scope: JsNode): IdentityHashMap<JsName, JsFunction> {
    val namedFunctions = IdentityHashMap<JsName, JsFunction>()

    for ((name, value) in collectJsProperties(scope)) {
        val function: JsFunction? = when (value) {
            is JsFunction -> value
            else -> InlineMetadata.decompose(value)?.function
        }

        if (function != null) {
            namedFunctions[name] = function
        }
    }

    return namedFunctions
}

fun <T : JsNode> collectInstances(klass: Class<T>, scope: JsNode): List<T> {
    return with(InstanceCollector(klass, visitNestedDeclarations = false)) {
        accept(scope)
        collected
    }
}

fun JsStatement.collectBreakContinueTargets(): (JsContinue) -> JsStatement? {
    val resultMap = mutableMapOf<JsContinue, JsStatement>()

    accept(object : RecursiveJsVisitor() {
        private var defaultBreakTarget: JsStatement? = null
        private var defaultContinueTarget: JsStatement? = null
        private val labels = mutableMapOf<JsName, JsStatement>()

        override fun visitFunction(x: JsFunction) { }

        override fun visitLabel(x: JsLabel) {
            labels[x.name] = x.statement
            accept(x.statement)
            labels.remove(x.name)
        }

        override fun visitFor(x: JsFor) {
            breakTarget(x, x) { accept(x.body) }
        }

        override fun visitForIn(x: JsForIn) {
            breakTarget(x, x) { accept(x.body) }
        }

        override fun visitWhile(x: JsWhile) {
            breakTarget(x, x) { accept(x.body) }
        }

        override fun visit(x: JsSwitch) {
            breakTarget(x, null) { super.visit(x) }
        }

        override fun visitContinue(x: JsContinue) {
            (labels[x.label?.name] ?: defaultContinueTarget)?.let { resultMap[x] = it }
        }

        override fun visitBreak(x: JsBreak) {
            (labels[x.label?.name] ?: defaultBreakTarget)?.let { resultMap[x] = it }
        }

        private fun breakTarget(breakTarget: JsStatement?, continueTarget: JsStatement?, block: () -> Unit) {
            val oldBreakTarget = defaultBreakTarget
            val oldContinueTarget = defaultContinueTarget
            if (breakTarget != null) {
                defaultBreakTarget = breakTarget
            }
            if (continueTarget != null) {
                defaultContinueTarget = continueTarget
            }
            block()
            defaultBreakTarget = oldBreakTarget
            defaultContinueTarget = oldContinueTarget
        }
    })

    return { resultMap[it] }
}

fun JsStatement.collectLastStatements(): List<JsStatement> {
    val resultList = mutableListOf<JsStatement>()
    val breakTargets = collectBreakContinueTargets()

    accept(object : JsVisitor() {
        private val lastLabels = mutableSetOf<JsStatement>()
        private var last = true
        private var shouldStop = false
        private var breakFound = false

        override fun visitBlock(x: JsBlock) {
            if (last) {
                lastLabels += x
            }
            visitMany(x.statements)
        }

        private fun visitMany(statements: List<JsStatement>) {
            shouldStop = false
            breakFound = false
            visitManyResume(statements)
        }

        private fun visitManyResume(statements: List<JsStatement>) {
            val lastHere = last

            for ((index, statement) in statements.withIndex()) {
                last = lastHere && index == statements.lastIndex
                val lastBackup = last
                accept(statement)
                if (breakFound) {
                    if (index > 0) {
                        breakFound = false
                        resultList += statements[index - 1]
                    }
                }
                if (shouldStop) {
                    break
                }

                if (lastBackup) {
                    resultList += statements[index]
                }
            }
        }

        override fun visitIf(x: JsIf) {
            val lastHere = last

            accept(x.thenStatement)
            val shouldStopInThen = shouldStop

            var shouldStopInElse = false
            x.elseStatement?.let {
                last = lastHere
                accept(it)
                shouldStopInElse = shouldStop
            }

            shouldStop = shouldStopInThen && shouldStopInElse
        }

        override fun visit(x: JsSwitch) {
            if (last) {
                lastLabels += x
            }

            var shouldStopInDefault = false
            val lastHere = last
            var lastStopped = false

            for ((index, switchCase) in x.cases.withIndex()) {
                last = lastHere && index == x.cases.lastIndex
                if (lastStopped) {
                    visitMany(switchCase.statements)
                }
                else {
                    visitManyResume(switchCase.statements)
                    lastStopped = shouldStop
                }
                if (switchCase is JsDefault) {
                     shouldStopInDefault = shouldStop
                }
            }

            shouldStop = shouldStopInDefault && lastStopped
        }

        override fun visitTry(x: JsTry) {
            var shouldStopHere = true
            var shouldStopInFinally = false
            val lastHere = last

            x.finallyBlock?.let {
                last = lastHere
                accept(it)
                shouldStopInFinally = shouldStop
            }

            last = lastHere && !shouldStopInFinally
            accept(x.tryBlock)
            if (!shouldStop) {
                shouldStopHere = false
            }

            x.catches.forEach {
                last = lastHere && !shouldStopInFinally
                accept(it.body)
                if (!shouldStop) {
                    shouldStopHere = false
                }
            }

            shouldStop = shouldStopHere || shouldStopInFinally
        }

        override fun visitWhile(x: JsWhile) {
            if (last) {
                lastLabels += x
            }
            last = false
            accept(x.body)
        }

        override fun visitFor(x: JsFor) {
            if (last) {
                lastLabels += x
            }
            last = false
            accept(x.body)
        }

        override fun visitForIn(x: JsForIn) {
            if (last) {
                lastLabels += x
            }
            last = false
            accept(x.body)
        }

        override fun visitBreak(x: JsBreak) {
            shouldStop = true
            if (breakTargets(x) in lastLabels) {
                breakFound = true
            }
        }

        override fun visitReturn(x: JsReturn) {
            shouldStop = true
        }

        override fun visitContinue(x: JsContinue) {
            shouldStop = true
        }

        override fun visitThrow(x: JsThrow) {
            shouldStop = true
        }

        override fun visitLabel(x: JsLabel) {
            accept(x.statement)
        }
    })

    return resultList
}
