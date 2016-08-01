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

package org.jetbrains.kotlin.resolve.calls.model

import org.jetbrains.kotlin.resolve.calls.inference.model.ConstraintPosition
import org.jetbrains.kotlin.resolve.calls.inference.model.NewTypeVariable
import org.jetbrains.kotlin.resolve.calls.tower.ResolutionCandidateApplicability
import org.jetbrains.kotlin.resolve.calls.tower.ResolutionCandidateApplicability.INAPPLICABLE
import org.jetbrains.kotlin.types.KotlinType

abstract class CallDiagnostic(val candidateApplicability: ResolutionCandidateApplicability) {
    abstract fun report(reporter: DiagnosticReporter)
}

interface DiagnosticReporter {
    fun onExplicitReceiver(diagnostic: CallDiagnostic)

    fun onCall(diagnostic: CallDiagnostic)

    fun onTypeArguments(diagnostic: CallDiagnostic)

    fun onCallName(diagnostic: CallDiagnostic)

    fun onTypeArgument(typeArgument: TypeArgument, diagnostic: CallDiagnostic)

    fun onCallReceiver(callReceiver: SimpleCallArgument, diagnostic: CallDiagnostic)

    fun onCallArgument(callArgument: CallArgument, diagnostic: CallDiagnostic)
    fun onCallArgumentName(callArgument: CallArgument, diagnostic: CallDiagnostic)
    fun onCallArgumentSpread(callArgument: CallArgument, diagnostic: CallDiagnostic)

    fun constraintError(diagnostic: CallDiagnostic)
}

class TypeMismatchDiagnostic(
        val callArgument: CallArgument,
        val expectedType: KotlinType,
        val actualType: KotlinType
) : CallDiagnostic(INAPPLICABLE) {
    override fun report(reporter: DiagnosticReporter) = reporter.onCallArgument(callArgument, this)
}