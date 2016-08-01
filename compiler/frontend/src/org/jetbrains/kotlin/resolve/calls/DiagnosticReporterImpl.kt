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

package org.jetbrains.kotlin.resolve.calls

import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.diagnostics.*
import org.jetbrains.kotlin.psi.Call
import org.jetbrains.kotlin.psi.ValueArgument
import org.jetbrains.kotlin.resolve.BindingTrace
import org.jetbrains.kotlin.resolve.calls.model.*
import java.util.*

object CallDiagnosticToDiagnostic {
    private val diagnosticMap: MutableMap<Class<out CallDiagnostic>, CallDiagnostic.(PsiElement) -> ParametrizedDiagnostic<*>> = HashMap()

    private fun <E: PsiElement, C: CallDiagnostic> checkPut(klass: Class<C>, factory: C.(PsiElement) -> ParametrizedDiagnostic<E>?) {
        @Suppress("UNCHECKED_CAST")
        diagnosticMap.put(klass, factory as CallDiagnostic.(PsiElement) -> ParametrizedDiagnostic<*>)
    }

    private inline fun <reified E: PsiElement, C: CallDiagnostic> put(factory0: DiagnosticFactory0<E>, klass: Class<C>) {
        checkPut<E, C>(klass) {
            (it as? E)?.let { factory0.on(it) }
        }
    }

    private inline fun <reified E: PsiElement, A, C: CallDiagnostic> put(factory1: DiagnosticFactory1<E, A>, klass: Class<C>, crossinline getA: C.() -> A) {
        checkPut<E, C>(klass) {
            (it as? E)?.let { factory1.on(it, getA()) }
        }
    }

    private inline fun <reified E: PsiElement, A, B, C: CallDiagnostic> put(
            factory2: DiagnosticFactory2<E, A, B>, klass: Class<C>, crossinline getA: C.() -> A, crossinline getB: C.() -> B) {
        checkPut<E, C>(klass) {
            (it as? E)?.let { factory2.on(it, getA(), getB()) }
        }
    }

    init {
//        put(Errors.UNSAFE_CALL, UnsafeCallDiagnostic::class.java, UnsafeCallDiagnostic::receiverType)
        put(Errors.TYPE_MISMATCH, TypeMismatchDiagnostic::class.java, TypeMismatchDiagnostic::expectedType, TypeMismatchDiagnostic::actualType)
    }


    // null means, that E is not subtype of required type for diagnostic factory
    fun <E : PsiElement> toDiagnostic(element: E, diagnostic: CallDiagnostic): ParametrizedDiagnostic<E>? {
        val diagnosticClass = diagnostic.javaClass
        val factory = diagnosticMap[diagnosticClass] ?: error("Illegal call diagnostic class: ${diagnosticClass.canonicalName}")

        @Suppress("UNCHECKED_CAST")
        return factory(diagnostic, element) as ParametrizedDiagnostic<E>?
    }

}

abstract class DiagnosticReporterImpl(private val bindingTrace: BindingTrace, private val call: Call) : DiagnosticReporter {

    override fun onCallArgument(callArgument: CallArgument, diagnostic: CallDiagnostic) {
        val d = CallDiagnosticToDiagnostic.toDiagnostic((callArgument as ValueArgument).asElement(), diagnostic)
        if (d != null) {
            bindingTrace.report(d)
        }
    }

}