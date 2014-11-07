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
import org.jetbrains.jet.descriptors.serialization.ProtoBuf.Type
import org.jetbrains.jet.lang.psi.stubs.impl.KotlinUserTypeStubImpl
import org.jetbrains.jet.lang.resolve.name.Name
import java.util.ArrayList
import org.jetbrains.jet.lang.psi.JetTypeReference
import org.jetbrains.jet.lang.psi.stubs.impl.KotlinModifierListStubImpl
import org.jetbrains.jet.lang.psi.stubs.impl.ModifierMaskUtils
import org.jetbrains.jet.descriptors.serialization.ProtoBuf.Modality
import org.jetbrains.jet.lexer.JetModifierKeywordToken
import org.jetbrains.jet.lexer.JetTokens
import org.jetbrains.jet.descriptors.serialization.ProtoBuf.Visibility
import org.jetbrains.jet.lang.psi.JetParameterList
import org.jetbrains.jet.lang.psi.stubs.impl.KotlinParameterStubImpl

public abstract class CompiledStubBuilderBase(
        protected val nameResolver: NameResolver,
        protected val packageFqName: FqName
) {
    protected fun createCallableStub(parentStub: StubElement<out PsiElement>, callableProto: ProtoBuf.Callable) {
        val callableStub = doCreateCallableStub(callableProto, parentStub)
        createModifierListStub(callableStub, callableProto.getFlags(), ignoreModality = true)
        createValueParametersStub(callableStub, callableProto)
        createTypeReferenceStub(callableStub, callableProto.getReturnType())
    }

    private fun createTypeReferenceStub(parent: StubElement<out PsiElement>, typeProto: ProtoBuf.Type) {
        val typeReference = KotlinPlaceHolderStubImpl<JetTypeReference>(parent, JetStubElementTypes.TYPE_REFERENCE)
        createTypeStub(typeProto, typeReference)
    }

    private fun createValueParametersStub(callableStub: StubElement<out PsiElement>, callableProto: ProtoBuf.Callable) {
        val parameterListStub = KotlinPlaceHolderStubImpl<JetParameterList>(callableStub, JetStubElementTypes.VALUE_PARAMETER_LIST)
        for (valueParameter in callableProto.getValueParameterList()) {
            val name = nameResolver.getName(valueParameter.getName())
            val parameterStub = KotlinParameterStubImpl(
                    parameterListStub,
                    name = name.asString().ref(),
                    fqName = null,
                    hasDefaultValue = false,
                    hasValOrValNode = false,
                    isMutable = false
            )
            createTypeReferenceStub(parameterStub, valueParameter.getType())
        }
    }

    private fun doCreateCallableStub(callableProto: ProtoBuf.Callable, parentStub: StubElement<out PsiElement>): StubElement<out PsiElement> {
        val callableKind = Flags.CALLABLE_KIND.get(callableProto.getFlags())
        val callableName = nameResolver.getName(callableProto.getName()).asString()
        val callableFqName = getInternalFqName(callableName)
        val callableNameRef = callableName.ref()
        return when (callableKind) {
            ProtoBuf.Callable.CallableKind.FUN ->
                KotlinFunctionStubImpl(
                        parentStub,
                        callableNameRef,
                        isTopLevel = callableFqName != null,
                        fqName = callableFqName,
                        isExtension = callableProto.hasReceiverType(),
                        hasBlockBody = true,
                        hasBody = true,
                        hasTypeParameterListBeforeFunctionName = false
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
            else -> throw IllegalStateException("Unknown callable kind $callableKind")
        }
    }

    protected abstract fun getInternalFqName(name: String): FqName?

    private fun createTypeStub(type: ProtoBuf.Type, parent: StubElement<out PsiElement>) {
        val id = type.getConstructor().getId()
        when (type.getConstructor().getKind()) {
            ProtoBuf.Type.Constructor.Kind.CLASS -> {
                val fqName = nameResolver.getFqName(id)
                createStubForType(fqName, parent)
            }
            ProtoBuf.Type.Constructor.Kind.TYPE_PARAMETER -> {
                //TODO: rocket science goes here
                throw IllegalStateException("Unexpected $type")
            }
        }
    }
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
        val head = segments.popFirst()
        if (segments.empty) {
            current = KotlinNameReferenceExpressionStubImpl(current, head.asString().ref())
        }
        else {
            current = KotlinPlaceHolderStubImpl<JetDotQualifiedExpression>(current, JetStubElementTypes.DOT_QUALIFIED_EXPRESSION)
            KotlinNameReferenceExpressionStubImpl(current, head.asString().ref())
        }
    }
}

private fun createStubForType(typeFqName: FqName, parent: StubElement<out PsiElement>) {
    val segments = typeFqName.pathSegments().toArrayList()
    assert(segments.notEmpty)
    var current: StubElement<out PsiElement> = parent
    var next: StubElement<out PsiElement>? = null
    while (true) {
        val lastSegment = segments.popLast()
        current = next ?: KotlinUserTypeStubImpl(current, isAbsoluteInRootPackage = false)
        if (segments.notEmpty) {
            next = KotlinUserTypeStubImpl(current, isAbsoluteInRootPackage = false)
        }
        KotlinNameReferenceExpressionStubImpl(current, lastSegment.asString().ref())
        if (segments.isEmpty()) {
            break
        }
    }
}

private fun <T> MutableList<T>.popLast(): T {
    val last = this.last
    this.remove(lastIndex)
    return last!!
}

private fun <T> MutableList<T>.popFirst(): T {
    val first = this.head
    this.remove(0)
    return first!!
}

fun createModifierListStub(
        parent: StubElement<out PsiElement>,
        flags: Int,
        ignoreModality: Boolean = false
) {
    val modifiers = arrayListOf(visibilityToModifier(Flags.VISIBILITY.get(flags)))
    if (!ignoreModality) {
        modifiers.add(modalityToModifier(Flags.MODALITY.get(flags)))
    }

    KotlinModifierListStubImpl(
            parent,
            ModifierMaskUtils.computeMask { it in modifiers },
            JetStubElementTypes.MODIFIER_LIST
    )
}

private fun modalityToModifier(modality: Modality): JetModifierKeywordToken {
    return when (modality) {
        ProtoBuf.Modality.ABSTRACT -> JetTokens.ABSTRACT_KEYWORD
        ProtoBuf.Modality.FINAL -> JetTokens.FINAL_KEYWORD
        ProtoBuf.Modality.OPEN -> JetTokens.OPEN_KEYWORD
        else -> throw IllegalStateException("Unexpected modality: $modality")
    }
}

private fun visibilityToModifier(visibility: Visibility): JetModifierKeywordToken {
    return when (visibility) {
        ProtoBuf.Visibility.PRIVATE -> JetTokens.PRIVATE_KEYWORD
        ProtoBuf.Visibility.INTERNAL -> JetTokens.INTERNAL_KEYWORD
        ProtoBuf.Visibility.PROTECTED -> JetTokens.PROTECTED_KEYWORD
        ProtoBuf.Visibility.PRIVATE -> JetTokens.PRIVATE_KEYWORD
    //TODO: support extra visibility
        else -> throw IllegalStateException("Unexpected visibility: $visibility")
    }
}