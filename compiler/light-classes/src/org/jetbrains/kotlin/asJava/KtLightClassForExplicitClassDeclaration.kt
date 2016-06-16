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

import com.google.common.collect.Lists
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.Comparing
import com.intellij.psi.*
import com.intellij.psi.impl.java.stubs.PsiJavaFileStub
import com.intellij.psi.impl.light.LightClass
import com.intellij.psi.impl.light.LightMethod
import com.intellij.psi.scope.PsiScopeProcessor
import com.intellij.psi.search.SearchScope
import com.intellij.psi.stubs.IStubElementType
import com.intellij.psi.stubs.StubElement
import com.intellij.util.IncorrectOperationException
import com.intellij.util.containers.ContainerUtil
import org.jetbrains.annotations.NonNls
import org.jetbrains.kotlin.builtins.KotlinBuiltIns
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.lexer.KtTokens.*
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getElementTextWithContext
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import org.jetbrains.kotlin.psi.stubs.KotlinClassOrObjectStub
import org.jetbrains.kotlin.resolve.DescriptorUtils
import javax.swing.Icon

open class KtLightClassForExplicitClassDeclaration(
        protected val classFqName: FqName,
        protected val classOrObject: KtClassOrObject)
: KtWrappingLightClass(classOrObject.manager), KtJavaMirrorMarker, StubBasedPsiElement<KotlinClassOrObjectStub<out KtClassOrObject>> {
    private val lightIdentifier = KtLightIdentifier(this, classOrObject)

    private val _extendsList by lazy(LazyThreadSafetyMode.PUBLICATION) {
        val listDelegate = super.getExtendsList() ?: return@lazy null
        KtLightPsiReferenceList(listDelegate, this)
    }

    private val _implementsList by lazy(LazyThreadSafetyMode.PUBLICATION) {
        val listDelegate = super.getImplementsList() ?: return@lazy null
        KtLightPsiReferenceList(listDelegate, this)
    }

    private fun getLocalClassParent(): PsiElement? {
        fun getParentByPsiMethod(method: PsiMethod?, name: String?, forceMethodWrapping: Boolean): PsiElement? {
            if (method == null || name == null) return null

            var containingClass: PsiClass? = method.containingClass ?: return null

            val currentFileName = classOrObject.containingFile.name

            var createWrapper = forceMethodWrapping
            // Use PsiClass wrapper instead of package light class to avoid names like "FooPackage" in Type Hierarchy and related views
            if (containingClass is KtLightClassForFacade) {
                containingClass = object : LightClass(containingClass as KtLightClassForFacade, KotlinLanguage.INSTANCE) {
                    override fun getName(): String? {
                        return currentFileName
                    }
                }
                createWrapper = true
            }

            if (createWrapper) {
                return object : LightMethod(myManager, method, containingClass!!, KotlinLanguage.INSTANCE) {
                    override fun getParent(): PsiElement {
                        return getContainingClass()!!
                    }

                    override fun getName(): String {
                        return name
                    }
                }
            }

            return method
        }

        var declaration: PsiElement? = KtPsiUtil.getTopmostParentOfTypes(
                classOrObject,
                KtNamedFunction::class.java,
                KtConstructor::class.java,
                KtProperty::class.java,
                KtAnonymousInitializer::class.java,
                KtParameter::class.java)

        if (declaration is KtParameter) {
            declaration = declaration.getStrictParentOfType<KtNamedDeclaration>()
        }

        if (declaration is KtFunction) {
            return getParentByPsiMethod(LightClassUtil.getLightClassMethod(declaration), declaration.name, false)
        }

        // Represent the property as a fake method with the same name
        if (declaration is KtProperty) {
            return getParentByPsiMethod(LightClassUtil.getLightClassPropertyMethods(declaration).getter, declaration.name, true)
        }

        if (declaration is KtAnonymousInitializer) {
            val parent = declaration.parent
            val grandparent = parent.parent

            if (parent is KtClassBody && grandparent is KtClassOrObject) {
                return grandparent.toLightClass()
            }
        }

        if (declaration is KtClass) {
            return declaration.toLightClass()
        }
        return null
    }

    private val _parent: PsiElement? by lazy(LazyThreadSafetyMode.PUBLICATION) {
        if (classOrObject.isLocal())
            getLocalClassParent()
        else if (classOrObject.isTopLevel())
            containingFile
        else
            containingClass
    }

    override val kotlinOrigin: KtClassOrObject = classOrObject

    override fun getFqName(): FqName = classFqName

    override fun copy(): PsiElement {
        return KtLightClassForExplicitClassDeclaration(classFqName, classOrObject.copy() as KtClassOrObject)
    }

    override val clsDelegate: PsiClass by lazy(LazyThreadSafetyMode.PUBLICATION) {
        getClsDelegate(getJavaFileStub(), classOrObject, classFqName)
    }

    private fun getJavaFileStub(): PsiJavaFileStub = getLightClassData().javaFileStub

    protected fun getDescriptor(): ClassDescriptor? {
        return LightClassGenerationSupport.getInstance(project).resolveToDescriptor(classOrObject) as? ClassDescriptor
    }

    private fun getLightClassData(): OutermostKotlinClassLightClassData {
        val lightClassData = getLightClassData(classOrObject)
        if (lightClassData !is OutermostKotlinClassLightClassData) {
            LOG.error("Invalid light class data for existing light class:\n$lightClassData\n${classOrObject.getElementTextWithContext()}")
        }
        return lightClassData as OutermostKotlinClassLightClassData
    }

    private val _containingFile: PsiFile by lazy(LazyThreadSafetyMode.PUBLICATION) {
        classOrObject.containingFile.virtualFile ?: error("No virtual file for " + classOrObject.text)

        object : FakeFileForLightClass(
                classOrObject.getContainingKtFile(),
                { if (classOrObject.isTopLevel()) this else createLightClass(getOutermostClassOrObject(classOrObject))!! },
                { getJavaFileStub() }
        ) {
            override fun findReferenceAt(offset: Int) = ktFile.findReferenceAt(offset)

            override fun processDeclarations(
                    processor: PsiScopeProcessor,
                    state: ResolveState,
                    lastParent: PsiElement?,
                    place: PsiElement): Boolean {
                if (!super.processDeclarations(processor, state, lastParent, place)) return false

                // We have to explicitly process package declarations if current file belongs to default package
                // so that Java resolve can find classes located in that package
                val packageName = packageName
                if (!packageName.isEmpty()) return true

                val aPackage = JavaPsiFacade.getInstance(myManager.project).findPackage(packageName)
                if (aPackage != null && !aPackage.processDeclarations(processor, state, null, place)) return false

                return true
            }
        }
    }

    override fun getContainingFile(): PsiFile? = _containingFile

    override fun getNavigationElement(): PsiElement = classOrObject

    override fun isEquivalentTo(another: PsiElement?): Boolean {
        return another is PsiClass && Comparing.equal(another.qualifiedName, qualifiedName)
    }

    override fun getElementIcon(flags: Int): Icon? {
        throw UnsupportedOperationException("This should be done by JetIconProvider")
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false

        val aClass = other as KtLightClassForExplicitClassDeclaration

        if (classFqName != aClass.classFqName) return false

        return true
    }

    override fun hashCode(): Int = classFqName.hashCode()

    override fun getContainingClass(): PsiClass? {
        if (classOrObject.parent === classOrObject.containingFile) return null

        val containingClassOrObject = (classOrObject.parent as? KtClassBody)?.parent as? KtClassOrObject
        if (containingClassOrObject != null) {
            return createLightClass(containingClassOrObject)
        }

        // TODO: should return null
        return super.getContainingClass()
    }

    override fun getParent(): PsiElement? = _parent

    private val _typeParameterList: PsiTypeParameterList by lazy(LazyThreadSafetyMode.PUBLICATION) {
        LightClassUtil.buildLightTypeParameterList(this, classOrObject)
    }

    override fun getTypeParameterList(): PsiTypeParameterList? = _typeParameterList

    override fun getTypeParameters(): Array<PsiTypeParameter> = _typeParameterList.typeParameters

    override fun getName(): String? = classFqName.shortName().asString()

    override fun getQualifiedName(): String? = classFqName.asString()

    private val _modifierList : PsiModifierList by lazy(LazyThreadSafetyMode.PUBLICATION) {
        object : KtLightModifierListWithExplicitModifiers(this@KtLightClassForExplicitClassDeclaration, computeModifiers()) {
            override val delegate: PsiAnnotationOwner
                get() = this@KtLightClassForExplicitClassDeclaration.delegate.modifierList!!
        }
    }

    override fun getModifierList(): PsiModifierList? = _modifierList

    protected open fun computeModifiers(): Array<String> {
        val psiModifiers = hashSetOf<String>()

        // PUBLIC, PROTECTED, PRIVATE, ABSTRACT, FINAL
        //noinspection unchecked

        for (tokenAndModifier in jetTokenToPsiModifier) {
            if (classOrObject.hasModifier(tokenAndModifier.first)) {
                psiModifiers.add(tokenAndModifier.second)
            }
        }

        if (classOrObject.hasModifier(PRIVATE_KEYWORD)) {
            // Top-level private class has PACKAGE_LOCAL visibility in Java
            // Nested private class has PRIVATE visibility
            psiModifiers.add(if (classOrObject.isTopLevel()) PsiModifier.PACKAGE_LOCAL else PsiModifier.PRIVATE)
        }
        else if (!psiModifiers.contains(PsiModifier.PROTECTED)) {
            psiModifiers.add(PsiModifier.PUBLIC)
        }


        // FINAL
        if (isAbstract() || isSealed()) {
            psiModifiers.add(PsiModifier.ABSTRACT)
        }
        else if (!(classOrObject.hasModifier(OPEN_KEYWORD) || (classOrObject is KtClass && classOrObject.isEnum()))) {
            psiModifiers.add(PsiModifier.FINAL)
        }

        if (!classOrObject.isTopLevel() && !classOrObject.hasModifier(INNER_KEYWORD)) {
            psiModifiers.add(PsiModifier.STATIC)
        }

        return psiModifiers.toTypedArray()
    }

    private fun isAbstract(): Boolean = classOrObject.hasModifier(ABSTRACT_KEYWORD) || isInterface

    private fun isSealed(): Boolean = classOrObject.hasModifier(SEALED_KEYWORD)

    override fun hasModifierProperty(@NonNls name: String): Boolean = modifierList?.hasModifierProperty(name) ?: false

    override fun isDeprecated(): Boolean {
        val jetModifierList = classOrObject.modifierList ?: return false

        val deprecatedFqName = KotlinBuiltIns.FQ_NAMES.deprecated
        val deprecatedName = deprecatedFqName.shortName().asString()

        for (annotationEntry in jetModifierList.annotationEntries) {
            val typeReference = annotationEntry.typeReference ?: continue

            val typeElement = typeReference.typeElement
            if (typeElement !is KtUserType) continue // If it's not a user type, it's definitely not a ref to deprecated

            val fqName = toQualifiedName(typeElement) ?: continue

            if (deprecatedFqName == fqName) return true
            if (deprecatedName == fqName.asString()) return true
        }
        return false
    }

    private fun toQualifiedName(userType: KtUserType): FqName? {
        val reversedNames = Lists.newArrayList<String>()

        var current: KtUserType? = userType
        while (current != null) {
            val name = current.referencedName ?: return null

            reversedNames.add(name)
            current = current.qualifier
        }

        return FqName.fromSegments(ContainerUtil.reverse(reversedNames))
    }

    override fun isInterface(): Boolean {
        if (classOrObject !is KtClass) return false
        return classOrObject.isInterface() || classOrObject.isAnnotation()
    }

    override fun isAnnotationType(): Boolean = classOrObject is KtClass && classOrObject.isAnnotation()

    override fun isEnum(): Boolean = classOrObject is KtClass && classOrObject.isEnum()

    override fun hasTypeParameters(): Boolean = classOrObject is KtClass && !classOrObject.typeParameters.isEmpty()

    override fun isValid(): Boolean = classOrObject.isValid

    override fun isInheritor(baseClass: PsiClass, checkDeep: Boolean): Boolean {
        val qualifiedName: String?
        if (baseClass is KtLightClassForExplicitClassDeclaration) {
            val baseDescriptor = baseClass.getDescriptor()
            qualifiedName = if (baseDescriptor != null) DescriptorUtils.getFqName(baseDescriptor).asString() else null
        }
        else {
            qualifiedName = baseClass.qualifiedName
        }

        val thisDescriptor = getDescriptor()
        return qualifiedName != null && thisDescriptor != null && checkSuperTypeByFQName(thisDescriptor, qualifiedName, checkDeep)
    }

    @Throws(IncorrectOperationException::class)
    override fun setName(@NonNls name: String): PsiElement {
        kotlinOrigin.setName(name)
        return this
    }

    override fun toString() = "${KtLightClass::class.java.simpleName}:$classFqName"

    override fun getOwnInnerClasses(): List<PsiClass> {
        return getOwnInnerClasses(classOrObject, classFqName)
    }

    override fun getUseScope(): SearchScope = kotlinOrigin.useScope

    override fun getElementType(): IStubElementType<out StubElement<*>, *>? = classOrObject.elementType
    override fun getStub(): KotlinClassOrObjectStub<out KtClassOrObject>? = classOrObject.stub

    override fun getNameIdentifier(): KtLightIdentifier? = lightIdentifier

    override fun getExtendsList() = _extendsList

    override fun getImplementsList() = _implementsList

    companion object {
        private val LOG = Logger.getInstance(KtLightClassForExplicitClassDeclaration::class.java)
    }
}
