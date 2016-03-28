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

package org.jetbrains.kotlin.asJava

import com.intellij.openapi.util.TextRange
import com.intellij.psi.*
import com.intellij.psi.impl.light.LightElement
import com.intellij.util.IncorrectOperationException
import org.jetbrains.annotations.NonNls
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtEnumEntry
import org.jetbrains.kotlin.psi.KtNamedDeclaration

interface KtLightField : PsiField, KtLightDeclaration<KtDeclaration, PsiField>

// Copied from com.intellij.psi.impl.light.LightField
sealed class KtLightFieldImpl(
        private val lightMemberOrigin: LightMemberOrigin?,
        private val delegate: PsiField,
        private val containingClass: KtLightClass
) : LightElement(delegate.manager, KotlinLanguage.INSTANCE), KtLightField {
    private val lightIdentifier = KtLightIdentifier(this, getOrigin() as? KtNamedDeclaration)

    @Throws(IncorrectOperationException::class)
    override fun setInitializer(initializer: PsiExpression?) = throw IncorrectOperationException("Not supported")

    override fun getUseScope() = getOrigin()?.useScope ?: super.getUseScope()

    override fun getName() = delegate.name

    override fun getNameIdentifier() = lightIdentifier

    override fun getDocComment() = delegate.docComment

    override fun isDeprecated() = delegate.isDeprecated

    override fun getContainingClass() = containingClass

    override fun getType() = delegate.type

    override fun getTypeElement() = delegate.typeElement

    override fun getInitializer() = delegate.initializer

    override fun hasInitializer() = delegate.hasInitializer()

    @Throws(IncorrectOperationException::class)
    override fun normalizeDeclaration() = throw IncorrectOperationException("Not supported")

    override fun computeConstantValue() = delegate.computeConstantValue()

    @Throws(IncorrectOperationException::class)
    override fun setName(@NonNls name: String) = throw IncorrectOperationException("Not supported")

    private val _modifierList by lazy { delegate.modifierList?.let { KtLightModifierList(it, this) } }

    override fun getModifierList() = _modifierList

    override fun hasModifierProperty(@NonNls name: String) = delegate.hasModifierProperty(name)

    override fun getText() = getOrigin()?.text ?: ""

    override fun getTextRange() = getOrigin()?.textRange ?: TextRange.EMPTY_RANGE

    override fun isValid() = containingClass.isValid

    override fun toString(): String = "${this.javaClass.simpleName}:$name"

    override fun getOrigin() = lightMemberOrigin?.originalElement

    override fun getDelegate() = delegate

    override fun getNavigationElement() = getOrigin() ?: super.getNavigationElement()

    override fun isEquivalentTo(another: PsiElement?): Boolean {
        if (another is KtLightField && getOrigin() == another.getOrigin() && delegate == another.getDelegate()) {
            return true
        }
        return super.isEquivalentTo(another)
    }

    override fun isWritable() = getOrigin()?.isWritable ?: false

    override fun copy() = Factory.create(lightMemberOrigin?.copy(), delegate, containingClass)

    class KtLightEnumConstant(
            origin: LightMemberOrigin?,
            enumConstant: PsiEnumConstant,
            containingClass: KtLightClass,
            private val initializingClass: PsiEnumConstantInitializer?
    ) : KtLightFieldImpl(origin, enumConstant, containingClass), PsiEnumConstant {
        override fun getDelegate() = super.getDelegate() as PsiEnumConstant

        // NOTE: we don't use "delegation by" because the compiler would generate method calls to ALL of PsiEnumConstant members,
        // but we need only members whose implementations are not present in KotlinLightField
        override fun getArgumentList() = getDelegate().argumentList

        override fun getInitializingClass(): PsiEnumConstantInitializer? = initializingClass
        override fun getOrCreateInitializingClass(): PsiEnumConstantInitializer =
                initializingClass ?: throw UnsupportedOperationException("Can't create enum constant body: ${getDelegate().name}")

        override fun resolveConstructor() = getDelegate().resolveConstructor()
        override fun resolveMethod() = getDelegate().resolveMethod()
        override fun resolveMethodGenerics() = getDelegate().resolveMethodGenerics()
    }

    class KtLightFieldForDeclaration(origin: LightMemberOrigin?, delegate: PsiField, containingClass: KtLightClass) :
            KtLightFieldImpl(origin, delegate, containingClass)

    companion object Factory {
        fun create(origin: LightMemberOrigin?, delegate: PsiField, containingClass: KtLightClass): KtLightField {
            when (delegate) {
                is PsiEnumConstant -> {
                    val kotlinEnumEntry = origin?.originalElement as? KtEnumEntry
                    val initializingClass = if (kotlinEnumEntry != null && kotlinEnumEntry.declarations.isNotEmpty()) {
                        val enumConstantFqName = FqName(containingClass.getFqName().asString() + "." + kotlinEnumEntry.name)
                        KtLightClassForEnumEntry(enumConstantFqName, kotlinEnumEntry, delegate)
                    }
                    else null
                    return KtLightEnumConstant(origin, delegate, containingClass, initializingClass)
                }
                else -> return KtLightFieldForDeclaration(origin, delegate, containingClass)
            }
        }
    }
}
