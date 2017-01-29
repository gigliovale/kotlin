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

package org.jetbrains.kotlin.js.inline.clean

import org.jetbrains.kotlin.js.backend.ast.*
import org.jetbrains.kotlin.js.inline.util.isCallInvocation

// Replaces Kotlin.toBoxedChar(Kotlin.unboxChar(e)) with Kotlin.toBoxedChar(e)
//          Kotlin.unboxChar(Kotlin.toBoxedChar(e)) with Kotlin.unboxChar(e)
//          Kotlin.unboxChar(97) with 97
class RedundantBoxUnboxElimination(private val root: JsBlock) {
    private var changed = false

    fun apply(): Boolean {
        (object : JsVisitorWithContextImpl() {
            override fun visit(x: JsInvocation, ctx: JsContext<JsNode>): Boolean {
                tryEliminate(x, ctx)

                return super.visit(x, ctx)
            }

            private fun tryEliminate(invocation: JsInvocation, ctx: JsContext<JsNode>) {
                if (isCallInvocation(invocation)) return

                val outer = decomposeBuiltin(invocation.qualifier) ?: return
                val args = invocation.arguments
                if (args.size != 1) return

                val boxingFunctions = listOf("unboxChar", "toBoxedChar");
                if (outer in boxingFunctions) {
                    val arg = args[0]
                    when (arg) {
                        is JsNumberLiteral -> if (outer == "unboxChar") {
                            ctx.replaceMe(arg)
                            changed = true
                        }
                        is JsInvocation -> if (decomposeBuiltin(arg.qualifier) in boxingFunctions) {
                            args[0] = arg.arguments[0]
                            changed = true
                            return
                        }
                    }
                }
            }

            // If q is in form of Kotlin.foo returns foo
            // Returns null otherwise
            private fun decomposeBuiltin(q: JsExpression?): String? {
                val kotlinDotfoo = q as? JsNameRef ?: return null
                val kotlin = (kotlinDotfoo.qualifier as? JsNameRef) ?: return null
                if (kotlin.qualifier != null || kotlin.ident != "Kotlin") return null
                return kotlinDotfoo.ident
            }
        }).accept(root)

        return changed
    }
}
