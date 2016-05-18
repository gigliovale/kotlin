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

package org.jetbrains.kotlin.js.inline.clean

import com.google.dart.compiler.backend.js.ast.*
import com.google.dart.compiler.backend.js.ast.metadata.HasMetadata
import com.google.dart.compiler.backend.js.ast.metadata.synthetic
import org.jetbrains.kotlin.js.inline.util.collectDefinedNames
import org.jetbrains.kotlin.js.inline.util.collectLastStatements
import org.jetbrains.kotlin.js.translate.utils.JsAstUtils

class ReturnDistribution(val root: JsBlock) {
    private var changed = false
    private val localVariables = mutableSetOf<JsName>()
    private val replacements = mutableMapOf<JsStatement, JsReturn>()
    private val returnsToRemove = mutableSetOf<JsReturn>()

    fun apply(): Boolean {
        analyze()
        replace()
        return changed
    }

    fun analyze() {
        localVariables += collectDefinedNames(root)

        root.accept(object : RecursiveJsVisitor() {
            override fun visitFunction(x: JsFunction) { }

            override fun visitBlock(x: JsBlock) {
                for ((i, statement) in x.statements.asSequence().withIndex().drop(1)) {
                    if (statement is JsReturn) {
                        handleDistributableReturn(statement, x.statements[i - 1])
                        break
                    }
                }
                super.visitBlock(x)
            }

            private fun handleDistributableReturn(statement: JsReturn, previousStatement: JsStatement) {
                val returnValue = statement.expression
                if (returnValue !is JsNameRef || returnValue.qualifier != null) return

                val name = returnValue.name
                if (name !in localVariables) return

                for (predecessor in previousStatement.collectLastStatements().filter { it is HasMetadata && it.synthetic  }) {
                    when (predecessor) {
                        is JsExpressionStatement -> {
                            val assignment = JsAstUtils.decomposeAssignmentToVariable(predecessor.expression)
                            if (assignment != null) {
                                val (targetName, value) = assignment
                                if (targetName == name) {
                                    addReplacement(value, predecessor, statement)
                                }
                            }
                        }
                        is JsVars -> {
                            if (predecessor.vars.size == 1) {
                                val declaration = predecessor.vars[0]
                                val value = declaration.initExpression
                                if (value != null && declaration.name == name) {
                                    addReplacement(value, predecessor, statement)
                                }
                            }
                        }
                    }
                }

                return
            }

            private fun addReplacement(value: JsExpression, statement: JsStatement, returnStatement: JsReturn) {
                val replacement = JsReturn(value).apply { synthetic = true }
                replacements[statement] = replacement
                returnsToRemove += returnStatement
                changed = true
            }
        })
    }

    fun replace() {
        object : JsVisitorWithContextImpl() {
            override fun visit(x: JsExpressionStatement, ctx: JsContext<JsNode>): Boolean {
                return replace(x, ctx) { super.visit(x, ctx) }
            }

            override fun visit(x: JsVars, ctx: JsContext<JsNode>): Boolean {
                return replace(x, ctx) { super.visit(x, ctx) }
            }

            private fun replace(x: JsStatement, ctx: JsContext<JsNode>, ifNoReplacement: () -> Boolean): Boolean {
                val replacement = replacements[x]
                return if (replacement != null) {
                    ctx.replaceMe(replacement)
                    return false
                }
                else {
                    ifNoReplacement()
                }
            }
        }.accept(root)
    }
}
