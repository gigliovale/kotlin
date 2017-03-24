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

import com.google.gwt.dev.js.rhino.CodePosition
import com.google.gwt.dev.js.rhino.ErrorReporter
import com.intellij.openapi.util.io.FileUtil
import org.jetbrains.kotlin.js.backend.ast.JsProgram
import org.jetbrains.kotlin.js.inline.util.fixForwardNameReferences
import org.jetbrains.kotlin.js.parser.parse
import java.io.File

fun main(args: Array<String>) {
    val file = File(args[0])
    val code = FileUtil.loadFile(file)
    val program = JsProgram()
    program.globalBlock.statements += parse(code, reporter, program.scope)
    program.globalBlock.fixForwardNameReferences()

    val dce = DeadCodeElimination(program.globalBlock)
    dce.apply()

    val newName = file.nameWithoutExtension + ".min.js"
    FileUtil.writeToFile(File(file.parentFile, newName), program.globalBlock.toString())
}

val reporter = object : ErrorReporter {
    override fun warning(message: String, startPosition: CodePosition, endPosition: CodePosition) {
        println("[WARN] at ${startPosition.line}, ${startPosition.offset}: $message")
    }

    override fun error(message: String, startPosition: CodePosition, endPosition: CodePosition) {
        println("[ERRO] at ${startPosition.line}, ${startPosition.offset}: $message")
    }
}