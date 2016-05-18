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

package org.jetbrains.kotlin.js.test.analyzer

import com.google.dart.compiler.backend.js.ast.*
import com.google.dart.compiler.backend.js.ast.metadata.HasMetadata
import com.google.gwt.dev.js.rhino.CodePosition
import com.google.gwt.dev.js.rhino.ErrorReporter
import com.intellij.openapi.util.io.FileUtil
import org.jetbrains.kotlin.js.inline.util.collectLastStatements
import org.jetbrains.kotlin.js.parser.parse
import org.jetbrains.kotlin.js.test.BasicTest
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestName
import java.io.File

class LastStatementCollectorTest {
    @Rule
    @JvmField
    var testName = TestName()

    @Test fun block() = box()

    @Test fun singleStatement() = box()

    @Test fun ifStatement() = box()

    @Test fun ifStatementExits() = box()

    @Test fun singleBranchOfIfExits() = box()

    @Test fun ifWithoutElse() = box()

    @Test fun ifWithoutElseExits() = box()

    @Test fun breakBlock() = box()

    @Test fun whileStatement() = box()

    @Test fun breakWhile() = box()

    @Test fun lastWrappedInBlock() = box()

    @Test fun breakNestedWhile() = box()

    @Test fun tryCatch() = box()

    @Test fun tryCatchFinallyExits() = box()

    @Test fun tryCatchExits() = box()

    @Test fun switchStatement() = box()

    @Test fun switchStatementExits() = box()

    @Test fun switchStatementWithoutDefault() = box()

    private fun box() {
        val methodName = testName.methodName
        val baseName = "${BasicTest.TEST_DATA_DIR_PATH}/js-analyzers/last-statement"
        val fileName = "$baseName/$methodName.js"

        val code = FileUtil.loadFile(File(fileName))
        val parserScope = JsFunctionScope(JsRootScope(JsProgram("<js checker>")), "<js fun>")
        val ast = parse(code, errorReporter, parserScope)
        val expected = findExpectedStatements(code, ast)

        var statement = ast[0]
        if (statement is JsExpressionStatement) {
            val expression = statement.expression
            if (expression is JsFunction) {
                statement = expression.body
            }
        }
        val actual = statement.collectLastStatements().asSequence().filter {
            it !is JsIf && it !is JsBlock && it !is JsSwitch && it !is JsWhile && it !is JsLabel
        }

        assertEquals(expected, actual.toSet())
    }

    private val errorReporter = object : ErrorReporter {
        override fun warning(message: String, startPosition: CodePosition, endPosition: CodePosition) { }

        override fun error(message: String, startPosition: CodePosition, endPosition: CodePosition) {
            fail("Error parsing JS file: $message at $startPosition")
        }
    }

    private fun findExpectedStatements(code: String, ast: List<JsStatement>): Set<JsStatement> {
        val comments = findSpecialComments(code)
        val expectedSet = mutableSetOf<JsStatement>()

        for (stmt in ast) {
            object : RecursiveJsVisitor() {
                override fun visitElement(node: JsNode) {
                    if (node is JsStatement && node is HasMetadata) {
                        val line = node.getData<Int?>("line")
                        if (line != null && line in comments.indices && comments[line]) {
                            expectedSet += node
                        }
                    }
                    super.visitElement(node)
                }
            }.accept(stmt)
        }

        return expectedSet
    }

    private fun findSpecialComments(code: String): List<Boolean> {
        val parts = code.lines()
        return parts.map { it.contains("/*final*/") }
    }
}
