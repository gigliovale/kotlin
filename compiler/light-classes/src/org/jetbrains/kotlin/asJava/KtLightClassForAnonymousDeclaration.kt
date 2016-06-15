/*
 * Copyright 2010-2015 JetBrains s.r.o.
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

import com.intellij.openapi.diagnostic.Logger
import com.intellij.psi.*
import com.intellij.psi.impl.DebugUtil
import com.intellij.psi.impl.InheritanceImplUtil
import com.intellij.psi.impl.java.stubs.PsiJavaFileStub
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.reference.SoftReference
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.stubs.KotlinClassOrObjectStub
import org.jetbrains.kotlin.resolve.DescriptorUtils
import java.util.*

internal open class KtLightClassForAnonymousDeclaration(protected val classOrObject: KtClassOrObject) :
        KtWrappingLightClass(classOrObject.manager),
        KtJavaMirrorMarker,
        StubBasedPsiElement<KotlinClassOrObjectStub<out KtClassOrObject>>,
        PsiAnonymousClass {
    private fun getJavaFileStub(): PsiJavaFileStub = getLightClassData().javaFileStub

    private fun getLightClassData(): OutermostKotlinClassLightClassData {
        val lightClassData = KtLightClassForExplicitDeclaration.getLightClassData(classOrObject)
        return lightClassData as OutermostKotlinClassLightClassData
    }

    protected val classFqName : FqName by lazy(LazyThreadSafetyMode.PUBLICATION) {
        KtLightClassForExplicitDeclaration.predictFqName(classOrObject) ?: FqName.ROOT
    }

    override fun getOwnInnerClasses(): List<PsiClass> {
        val result = ArrayList<PsiClass>()
        classOrObject.declarations.filterIsInstance<KtClassOrObject>().mapNotNullTo(result) { KtLightClassForExplicitDeclaration.create(it) }

        if (classOrObject.hasInterfaceDefaultImpls) {
            result.add(KtLightClassForInterfaceDefaultImpls(classFqName.defaultImplsChild(), classOrObject))
        }

        return result
    }

    override fun copy(): PsiElement = KtLightClassForAnonymousDeclaration(classOrObject)
    override val kotlinOrigin = classOrObject

    override fun getFqName() = classFqName

    override val clsDelegate: PsiClass by lazy(LazyThreadSafetyMode.PUBLICATION) {
        val javaFileStub = getJavaFileStub()

        LightClassUtil.findClass(classFqName, javaFileStub) ?: run {
            val outermostClassOrObject = KtLightClassForExplicitDeclaration.getOutermostClassOrObject(classOrObject)
            val ktFileText: String? = try {
                outermostClassOrObject.containingFile.text
            }
            catch (e: Exception) {
                "Can't get text for outermost class"
            }

            val stubFileText = DebugUtil.stubTreeToString(javaFileStub)
            throw IllegalStateException("Class was not found $classFqName\nin $ktFileText\nstub: \n$stubFileText")
        }
    }

    override fun getStub() = classOrObject.stub
    override fun getElementType() = classOrObject.elementType

    private var cachedBaseType: SoftReference<PsiClassType>? = null

    override fun getBaseClassReference(): PsiJavaCodeReferenceElement {
        return JavaPsiFacade.getElementFactory(classOrObject.project).createReferenceElementByType(baseClassType)
    }

    private val firstSupertypeFQName: String
        get() {
            val descriptor = getDescriptor() ?: return CommonClassNames.JAVA_LANG_OBJECT

            val superTypes = descriptor.typeConstructor.supertypes

            if (superTypes.isEmpty()) return CommonClassNames.JAVA_LANG_OBJECT

            val superType = superTypes.iterator().next()
            val superClassDescriptor = superType.constructor.declarationDescriptor

            if (superClassDescriptor == null) {
                LOG.error("No declaration descriptor for supertype " + superType + " of " + getDescriptor())

                // return java.lang.Object for recovery
                return CommonClassNames.JAVA_LANG_OBJECT
            }

            return DescriptorUtils.getFqName(superClassDescriptor).asString()
        }

    @Synchronized override fun getBaseClassType(): PsiClassType {
        var type: PsiClassType? = null
        if (cachedBaseType != null) type = cachedBaseType!!.get()
        if (type != null && type.isValid) return type

        val firstSupertypeFQName = firstSupertypeFQName
        for (superType in superTypes) {
            val superClass = superType.resolve()
            if (superClass != null && firstSupertypeFQName == superClass.qualifiedName) {
                type = superType
                break
            }
        }

        if (type == null) {
            val project = classOrObject.project
            type = PsiType.getJavaLangObject(PsiManager.getInstance(project), GlobalSearchScope.allScope(project))
        }

        cachedBaseType = SoftReference<PsiClassType>(type)
        return type
    }

    override fun getArgumentList(): PsiExpressionList? = null

    override fun isInQualifiedNew(): Boolean {
        return false
    }

    override fun getName(): String? = null

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false

        val aClass = other as KtLightClassForAnonymousDeclaration

        return classOrObject == aClass.classOrObject
    }

    override fun hashCode(): Int {
        return classOrObject.hashCode()
    }

    override fun isInheritor(baseClass: PsiClass, checkDeep: Boolean): Boolean {
        if (baseClass is KtLightClassForExplicitDeclaration) {
            return super.isInheritor(baseClass, checkDeep)
        }

        return InheritanceImplUtil.isInheritor(this, baseClass, checkDeep)
    }

    override fun getNameIdentifier() = null
    override fun getQualifiedName(): String? = null
    override fun getModifierList(): PsiModifierList? = null
    override fun hasModifierProperty(name: String): Boolean = name == PsiModifier.FINAL
    override fun getExtendsList() = null
    override fun getImplementsList() = null
    override fun getContainingClass(): PsiClass? = null
    override fun isInterface() = false
    override fun isAnnotationType() = false
    override fun getTypeParameterList() = null
    override fun isEnum() = false

    protected fun getDescriptor(): ClassDescriptor? {
        return LightClassGenerationSupport.getInstance(project).resolveToDescriptor(classOrObject) as? ClassDescriptor
    }

    companion object {
        private val LOG = Logger.getInstance(KtLightClassForAnonymousDeclaration::class.java)
    }
}
