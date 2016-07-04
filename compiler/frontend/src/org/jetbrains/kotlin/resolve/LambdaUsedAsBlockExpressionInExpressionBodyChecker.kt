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

package org.jetbrains.kotlin.resolve

import org.jetbrains.kotlin.builtins.isFunctionType
import org.jetbrains.kotlin.descriptors.CallableDescriptor
import org.jetbrains.kotlin.descriptors.ClassifierDescriptorWithTypeParameters
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.diagnostics.DiagnosticSink
import org.jetbrains.kotlin.diagnostics.Errors
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtNamedFunction

object LambdaUsedAsBlockExpressionInExpressionBodyChecker : SimpleDeclarationChecker {
    override fun check(
            declaration: KtDeclaration,
            descriptor: DeclarationDescriptor,
            diagnosticHolder: DiagnosticSink,
            bindingContext: BindingContext
    ) {
        if (declaration !is KtNamedFunction) return
        if (descriptor !is CallableDescriptor) return

        val returnType = descriptor.returnType
        if (returnType == null || !returnType.isFunctionType) return

        val returnTypeClassifier = returnType.constructor.declarationDescriptor as? ClassifierDescriptorWithTypeParameters
        if (returnTypeClassifier?.declaredTypeParameters?.size != 1) return

        if (declaration.hasDeclaredReturnType()) return
        if (declaration.equalsToken == null) return

        val body = declaration.bodyExpression as? KtLambdaExpression ?: return
        if (body.hasArrowToken()) return

        diagnosticHolder.report(Errors.LAMBDA_USED_AS_BLOCK_EXPRESSION_IN_FUN.on(declaration))
    }
}

private fun KtLambdaExpression.hasArrowToken() =
        functionLiteral.node.findChildByType(KtTokens.ARROW) != null
