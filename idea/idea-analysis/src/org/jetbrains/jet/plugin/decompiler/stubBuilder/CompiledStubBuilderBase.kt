/*
 * Copyright 2010-2014 JetBrains s.r.o.
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

package org.jetbrains.jet.plugin.decompiler.stubBuilder

import com.intellij.psi.stubs.StubElement
import org.jetbrains.jet.descriptors.serialization.Flags
import org.jetbrains.jet.descriptors.serialization.NameResolver
import org.jetbrains.jet.descriptors.serialization.ProtoBuf
import org.jetbrains.jet.lang.psi.stubs.impl.KotlinFileStubImpl
import org.jetbrains.jet.lang.psi.stubs.impl.KotlinFunctionStubImpl
import org.jetbrains.jet.lang.psi.stubs.impl.KotlinPropertyStubImpl
import org.jetbrains.jet.lang.resolve.name.FqName
import com.intellij.psi.PsiElement
import org.jetbrains.jet.lang.psi.stubs.impl.KotlinPlaceHolderStubImpl
import org.jetbrains.jet.lang.psi.stubs.elements.JetStubElementType
import org.jetbrains.jet.lang.psi.stubs.elements.JetStubElementTypes
import org.jetbrains.jet.lang.psi.JetPackageDirective
import org.jetbrains.jet.lang.psi.stubs.KotlinNameReferenceExpressionStub
import org.jetbrains.jet.lang.psi.stubs.impl.KotlinNameReferenceExpressionStubImpl
import org.jetbrains.jet.lang.psi.stubs.KotlinPlaceHolderStub
import org.jetbrains.jet.lang.psi.JetElement
import org.jetbrains.jet.lang.psi.JetDotQualifiedExpression
import org.jetbrains.jet.lang.psi.stubs.elements.JetDotQualifiedExpressionElementType

public abstract class CompiledStubBuilderBase(
        protected val nameResolver: NameResolver,
        protected val packageFqName: FqName
) {
    protected fun createCallableStub(parentStub: StubElement<out PsiElement>, callableProto: ProtoBuf.Callable) {
        val callableKind = Flags.CALLABLE_KIND.get(callableProto.getFlags())
        val callableName = nameResolver.getName(callableProto.getName()).asString()
        val callableFqName = getInternalFqName(callableName)
        val callableNameRef = callableName.ref()
        when (callableKind) {
            ProtoBuf.Callable.CallableKind.FUN ->
                KotlinFunctionStubImpl(
                        parentStub,
                        callableNameRef,
                        isTopLevel = callableFqName != null,
                        fqName = callableFqName,
                        isExtension = callableProto.hasReceiverType(),
                        hasBlockBody = true,
                        hasBody = true,
                        hasTypeParameterListBeforeFunctionName = true
                )
            ProtoBuf.Callable.CallableKind.VAL ->
                KotlinPropertyStubImpl(
                        parentStub, callableNameRef,
                        isVar = false,
                        isTopLevel = callableFqName != null,
                        hasDelegate = false,
                        hasDelegateExpression = false,
                        hasInitializer = false,
                        hasReceiverTypeRef = false,
                        hasReturnTypeRef = true,
                        fqName = callableFqName
                )
            ProtoBuf.Callable.CallableKind.VAR ->
                KotlinPropertyStubImpl(
                        parentStub,
                        callableNameRef,
                        isVar = true,
                        isTopLevel = callableFqName != null,
                        hasDelegate = false,
                        hasDelegateExpression = false,
                        hasInitializer = false,
                        hasReceiverTypeRef = false,
                        hasReturnTypeRef = true,
                        fqName = callableFqName
                )

            ProtoBuf.Callable.CallableKind.CONSTRUCTOR -> throw IllegalStateException("Stubs for constructors are not supported!")
        }
    }

    protected abstract fun getInternalFqName(name: String): FqName?
}

public fun createFileStub(packageFqName: FqName): KotlinFileStubImpl {
    val fileStub = KotlinFileStubImpl(null, packageFqName.asString(), packageFqName.isRoot())
    val packageDirectiveStub = KotlinPlaceHolderStubImpl<JetPackageDirective>(fileStub, JetStubElementTypes.PACKAGE_DIRECTIVE)
    createStubForPackageName(packageDirectiveStub, packageFqName)
    return fileStub
}

private fun createStubForPackageName(packageDirectiveStub: KotlinPlaceHolderStubImpl<JetPackageDirective>, packageFqName: FqName) {
    val segments = packageFqName.pathSegments().toArrayList()
    var current: StubElement<out JetElement> = packageDirectiveStub
    while (segments.notEmpty) {
        val head = segments.first()
        segments.remove(0)
        if (segments.empty) {
            current = KotlinNameReferenceExpressionStubImpl(current, head.asString().ref())
        }
        else {
            current = KotlinPlaceHolderStubImpl<JetDotQualifiedExpression>(current, JetStubElementTypes.DOT_QUALIFIED_EXPRESSION)
            KotlinNameReferenceExpressionStubImpl(current, head.asString().ref())
        }
    }
}