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

package org.jetbrains.kotlin.asJava.elements

import com.intellij.openapi.util.TextRange
import com.intellij.psi.*
import com.intellij.psi.impl.light.LightElement
import org.jetbrains.kotlin.asJava.LightClassGenerationSupport
import org.jetbrains.kotlin.asJava.classes.lazyPub
import org.jetbrains.kotlin.descriptors.PropertyDescriptor
import org.jetbrains.kotlin.descriptors.annotations.AnnotationDescriptor
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameUnsafe
import org.jetbrains.kotlin.resolve.source.getPsi

abstract class KtLightModifierList<out T : KtLightElement<KtModifierListOwner, PsiModifierListOwner>>(protected val owner: T)
    : LightElement(owner.manager, KotlinLanguage.INSTANCE), PsiModifierList, KtLightElement<KtModifierList, PsiModifierList> {
    override val clsDelegate by lazyPub { owner.clsDelegate.modifierList!! }
    private val _annotations by lazyPub { computeAnnotations(this) }
    override val kotlinOrigin: KtModifierList?
        get() = owner.kotlinOrigin?.modifierList

    override fun hasExplicitModifier(name: String) = hasModifierProperty(name)

    override fun setModifierProperty(name: String, value: Boolean) = clsDelegate.setModifierProperty(name, value)
    override fun checkSetModifierProperty(name: String, value: Boolean) = clsDelegate.checkSetModifierProperty(name, value)
    override fun addAnnotation(qualifiedName: String) = clsDelegate.addAnnotation(qualifiedName)
    override fun getApplicableAnnotations(): Array<out PsiAnnotation> = annotations
    override fun getAnnotations(): Array<out PsiAnnotation> = _annotations.toTypedArray()
    override fun findAnnotation(qualifiedName: String) = annotations.firstOrNull { it.qualifiedName == qualifiedName }
    override fun getParent() = owner
    override fun getText(): String? = ""
    override fun getTextRange() = TextRange.EMPTY_RANGE
    override fun getReferences() = PsiReference.EMPTY_ARRAY
    override fun isEquivalentTo(another: PsiElement?) =
            another is KtLightModifierList<*> && owner == another.owner

    override fun toString() = "Light modifier list of $owner"
}

class KtLightSimpleModifierList(
        owner: KtLightElement<KtModifierListOwner, PsiModifierListOwner>, private val modifiers: Set<String>
) : KtLightModifierList<KtLightElement<KtModifierListOwner, PsiModifierListOwner>>(owner) {
    override fun hasModifierProperty(name: String) = name in modifiers

    override fun copy() = KtLightSimpleModifierList(owner, modifiers)
}

private fun computeAnnotations(lightModifierList: KtLightModifierList<*>): List<PsiAnnotation> {
    val annotationsForEntries = lightAnnotationsForEntries(lightModifierList)
    val modifierListOwner = lightModifierList.parent
    if ((modifierListOwner is KtLightMember<*> && modifierListOwner !is KtLightFieldImpl.KtLightEnumConstant)
        || modifierListOwner is KtLightParameter) {
        return annotationsForEntries +
               @Suppress("UNCHECKED_CAST")
               listOf(KtLightNullabilityAnnotation(modifierListOwner as KtLightElement<*, PsiModifierListOwner>, lightModifierList))
    }
    return annotationsForEntries
}

private fun lightAnnotationsForEntries(lightModifierList: KtLightModifierList<*>): List<KtLightAnnotationForSourceEntry> {
    val lightModifierListOwner = lightModifierList.parent
    val annotatedKtDeclaration = lightModifierListOwner.kotlinOrigin as? KtDeclaration

    if (annotatedKtDeclaration == null || !annotatedKtDeclaration.isValid || !hasAnnotationsInSource(annotatedKtDeclaration)) {
        return emptyList()
    }

    return getAnnotationDescriptors(annotatedKtDeclaration, lightModifierListOwner).map { descriptor ->
        val annotationFqName = descriptor.type.constructor.declarationDescriptor?.fqNameUnsafe?.asString() ?: return emptyList()
        val entry = descriptor.source.getPsi() as? KtAnnotationEntry ?: return emptyList()
        KtLightAnnotationForSourceEntry(annotationFqName, entry, lightModifierList) {
            lightModifierList.clsDelegate.findAnnotation(annotationFqName) ?: KtLightNonExistentAnnotation(lightModifierList)
        }
    }
}

private fun getAnnotationDescriptors(declaration: KtDeclaration?, lightOwner: KtLightElement<*, *>?): List<AnnotationDescriptor> {
    val descriptor = declaration?.let { LightClassGenerationSupport.getInstance(it.project).resolveToDescriptor(it) }
    val annotatedDescriptor = when {
        descriptor !is PropertyDescriptor || lightOwner !is KtLightMethod -> descriptor
        lightOwner.isGetter -> descriptor.getter
        lightOwner.isSetter -> descriptor.setter
        else -> descriptor
    } ?: return emptyList()
    return annotatedDescriptor.annotations.getAllAnnotations().map { it.annotation }
}

private fun hasAnnotationsInSource(declaration: KtDeclaration): Boolean {
    if (declaration.annotationEntries.isNotEmpty()) {
        return true
    }

    if (declaration is KtProperty) {
        return declaration.accessors.any { hasAnnotationsInSource(it) }
    }

    return false
}
