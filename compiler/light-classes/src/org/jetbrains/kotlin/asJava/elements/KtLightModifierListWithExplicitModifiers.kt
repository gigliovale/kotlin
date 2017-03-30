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

import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiModifierList
import com.intellij.psi.PsiModifierListOwner
import com.intellij.psi.impl.light.LightModifierList
import org.jetbrains.annotations.NonNls
import org.jetbrains.kotlin.asJava.LightClassGenerationSupport
import org.jetbrains.kotlin.asJava.classes.lazyPub
import org.jetbrains.kotlin.descriptors.PropertyDescriptor
import org.jetbrains.kotlin.descriptors.annotations.AnnotationDescriptor
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtModifierListOwner
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameUnsafe
import org.jetbrains.kotlin.resolve.source.getPsi

class KtLightModifierListWithExplicitModifiers(
        private val owner: KtLightElement<KtModifierListOwner, PsiModifierListOwner>,
        modifiers: Array<String>
) : KtLightElement<KtElement, PsiModifierList>, LightModifierList(owner.manager, KotlinLanguage.INSTANCE, *modifiers) {

    override val clsDelegate: PsiModifierList
        get() = owner.clsDelegate.modifierList!!

    override val kotlinOrigin: KtElement?
        get() = owner.kotlinOrigin?.modifierList

    private val _annotations by lazyPub { computeAnnotations(this)  }

    override fun getParent() = owner

    override fun getAnnotations(): Array<out PsiAnnotation> = _annotations.toTypedArray()

    override fun getApplicableAnnotations() = clsDelegate.applicableAnnotations

    override fun findAnnotation(@NonNls qualifiedName: String) = annotations.firstOrNull { it.qualifiedName == qualifiedName }

    override fun addAnnotation(@NonNls qualifiedName: String) = clsDelegate.addAnnotation(qualifiedName)
}

internal fun computeAnnotations(
        annotationOwner: KtLightElement<*, PsiModifierList>
): List<KtLightAnnotation> {
    val lightOwner = annotationOwner.parent as? KtLightElement<*, *>
    val declaration = lightOwner?.kotlinOrigin as? KtDeclaration
    if (declaration != null && !declaration.isValid) return emptyList()

    val annotationDescriptors = getAnnotatedDescriptor(declaration, lightOwner)
    return annotationDescriptors.map { descriptor ->
        val annotationFqName = descriptor.type.constructor.declarationDescriptor?.fqNameUnsafe?.asString() ?: return emptyList()
        val entry = descriptor.source.getPsi() as? KtAnnotationEntry ?: return emptyList()
        KtLightAnnotation(annotationFqName, entry, annotationOwner) {
            annotationOwner.clsDelegate.findAnnotation(annotationFqName)!!
        }
    }
}

internal fun getAnnotatedDescriptor(declaration: KtDeclaration?, lightOwner: KtLightElement<*, *>?): List<AnnotationDescriptor> {
    val descriptor = declaration?.let { LightClassGenerationSupport.getInstance(it.project).resolveToDescriptor(it) }
    val annotatedDescriptor = when {
        descriptor !is PropertyDescriptor || lightOwner !is KtLightMethod -> descriptor
        lightOwner.isGetter -> descriptor.getter
        lightOwner.isSetter -> descriptor.setter
        else -> descriptor
    } ?: return emptyList()
    return annotatedDescriptor.annotations.getAllAnnotations().map { it.annotation }
}
