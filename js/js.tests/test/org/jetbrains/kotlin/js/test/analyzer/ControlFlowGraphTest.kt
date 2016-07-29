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
import com.google.gwt.dev.js.rhino.CodePosition
import com.google.gwt.dev.js.rhino.ErrorReporter
import com.intellij.openapi.util.io.FileUtil
import org.jetbrains.kotlin.js.inline.analyze.buildControlFlowGraph
import org.jetbrains.kotlin.js.inline.analyze.printControlFlowGraph
import org.jetbrains.kotlin.js.parser.parse
import org.jetbrains.kotlin.js.test.BasicTest
import org.junit.Assert
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestName
import java.io.File

class ControlFlowGraphTest {
    @Rule
    @JvmField
    var testName = TestName()

    @Test fun simple() = runTest()

    @Test fun arrays() = runTest()

    @Test fun assignment() = runTest()

    @Test fun unary() = runTest()

    @Test fun objectLiteral() = runTest()

    @Test fun conditional() = runTest()

    @Test fun conditionalAsExpressionPart() = runTest()

    @Test fun vars() = runTest()

    @Test fun ifStatement() = runTest()

    @Test fun ifStatementWithoutElse() = runTest()

    @Test fun ifStatementWithEmptyThen() = runTest()

    @Test fun emptyIfStatement() = runTest()

    @Test fun ifWithConditionalCondition() = runTest()

    @Test fun labeledIf() = runTest()

    @Test fun whileStatement() = runTest()

    @Test fun emptyWhileStatement() = runTest()

    @Test fun doWhileStatement() = runTest()

    @Test fun forStatement() = runTest()

    @Test fun forStatementWithoutIncrement() = runTest()

    @Test fun emptyForStatement() = runTest()

    @Test fun forInStatement() = runTest()

    @Test fun switchStatement() = runTest()

    @Test fun switchStatementWithoutDefault() = runTest()

    @Test fun switchWithEmptyCases() = runTest()

    @Test fun continueStatement() = runTest()

    @Test fun breakStatement() = runTest()

    @Test fun nestedContinueStatement() = runTest()

    @Test fun nestedBreakStatement() = runTest()

    @Test fun nestedContinueStatementWithLabel() = runTest()

    @Test fun nestedBreakStatementWithLabel() = runTest()

    @Test fun doContinue() = runTest()

    @Test fun forContinue() = runTest()

    @Test fun ifWithAndAnd() = runTest()

    @Test fun ifWithOrOr() = runTest()

    @Test fun whileWithUnconditionedBreak() = runTest()

    @Test fun labeledBlock() = runTest()

    @Test fun nestedEmptyBlock() = runTest()

    @Test fun tryCatch() = runTest()

    @Test fun tryCatchFinally() = runTest()

    @Test fun tryFinallyWithComplexBody() = runTest()

    @Test fun rethrowException() = runTest()

    @Test fun nestedFinally() = runTest()

    @Test fun breakFromFinally() = runTest()

    private fun runTest() {
        val methodName = testName.methodName
        checkControlFlowGraph("${BasicTest.TEST_DATA_DIR_PATH}/js-cfg/$methodName")
    }

    private fun checkControlFlowGraph(fileName: String) {
        val function = parseJS(fileName + ".js")
        val cfg = function.buildControlFlowGraph()
        val cfgActual = cfg.printControlFlowGraph()
        val cfgExpected = FileUtil.loadFile(File(fileName + ".txt"))

        Assert.assertEquals(cfgExpected, cfgActual)
    }

    private fun parseJS(fileName: String): JsFunction {
        val sourceCode = FileUtil.loadFile(File(fileName))
        val parserScope = JsFunctionScope(JsRootScope(JsProgram("<js checker>")), "<js fun>")
        val statements = parse(sourceCode, errorReporter, parserScope)
        return statements.asSequence()
                .mapNotNull { it as? JsExpressionStatement }
                .mapNotNull { it.expression as? JsFunction }
                .firstOrNull() ?: error("'box' function not found")
    }

    private val errorReporter = object : ErrorReporter {
        override fun warning(message: String, startPosition: CodePosition, endPosition: CodePosition) { }

        override fun error(message: String, startPosition: CodePosition, endPosition: CodePosition) {
            Assert.fail("Error parsing JS file: $message at $startPosition")
        }
    }
}