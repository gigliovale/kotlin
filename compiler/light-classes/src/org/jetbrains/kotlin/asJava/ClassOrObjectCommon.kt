package org.jetbrains.kotlin.asJava

import com.intellij.openapi.util.Key
import com.intellij.psi.CommonClassNames
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiModifier
import com.intellij.psi.impl.DebugUtil
import com.intellij.psi.impl.java.stubs.PsiJavaFileStub
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValuesManager
import org.jetbrains.kotlin.codegen.binding.PsiCodegenPredictor
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.fileClasses.NoResolveFileClassesProvider
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.FqNameUnsafe
import org.jetbrains.kotlin.platform.JavaToKotlinClassMap
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtObjectDeclaration
import org.jetbrains.kotlin.psi.KtPsiUtil
import org.jetbrains.kotlin.resolve.DescriptorUtils
import org.jetbrains.kotlin.resolve.jvm.JvmClassName
import java.util.*

private val JAVA_API_STUB = Key.create<CachedValue<WithFileStubAndExtraDiagnostics>>("JAVA_API_STUB")

val jetTokenToPsiModifier = listOf(
        KtTokens.PUBLIC_KEYWORD to PsiModifier.PUBLIC,
        KtTokens.INTERNAL_KEYWORD to  PsiModifier.PUBLIC,
        KtTokens.PROTECTED_KEYWORD to PsiModifier.PROTECTED,
        KtTokens.FINAL_KEYWORD to PsiModifier.FINAL)


fun createLightClass(classOrObject: KtClassOrObject): KtLightClass? {
    if (classOrObject is KtObjectDeclaration && classOrObject.isObjectLiteral()) {
        if (classOrObject.containingFile.virtualFile == null) {
            return null
        }

        return KtLightClassForAnonymousDeclaration(classOrObject)
    }

    val fqName = predictFqName(classOrObject) ?: return null
    return KtLightClassForExplicitDeclaration(fqName, classOrObject)
}

fun predictFqName(classOrObject: KtClassOrObject): FqName? {
    if (classOrObject.isLocal()) {
        if (classOrObject.containingFile.virtualFile == null) return null
        val data = getLightClassDataExactly(classOrObject)
        return data?.jvmQualifiedName
    }
    val internalName = PsiCodegenPredictor.getPredefinedJvmInternalName(classOrObject, NoResolveFileClassesProvider)
    return if (internalName == null) null else JvmClassName.byInternalName(internalName).fqNameForClassNameWithoutDollars
}

fun getLightClassData(classOrObject: KtClassOrObject): LightClassData {
    return getLightClassCachedValue(classOrObject).value
}

fun getLightClassCachedValue(classOrObject: KtClassOrObject): CachedValue<WithFileStubAndExtraDiagnostics> {
    val outermostClassOrObject = getOutermostClassOrObject(classOrObject)
    var value = outermostClassOrObject.getUserData(JAVA_API_STUB)
    if (value == null) {
        value = CachedValuesManager.getManager(classOrObject.project).createCachedValue(
                LightClassDataProviderForClassOrObject(outermostClassOrObject), false)
        value = outermostClassOrObject.putUserDataIfAbsent(JAVA_API_STUB, value)
    }
    return value
}

private fun getLightClassDataExactly(classOrObject: KtClassOrObject): LightClassDataForKotlinClass? {
    val data = getLightClassData(classOrObject) as? OutermostKotlinClassLightClassData ?: return null
    return data.dataForClass(classOrObject)
}

fun getOutermostClassOrObject(classOrObject: KtClassOrObject): KtClassOrObject {
    val outermostClass = KtPsiUtil.getOutermostClassOrObject(classOrObject) ?:
                         throw IllegalStateException("Attempt to build a light class for a local class: " + classOrObject.text)

    return outermostClass
}

fun checkSuperTypeByFQName(classDescriptor: ClassDescriptor, qualifiedName: String, deep: Boolean): Boolean {
    if (CommonClassNames.JAVA_LANG_OBJECT == qualifiedName) return true

    if (qualifiedName == DescriptorUtils.getFqName(classDescriptor).asString()) return true

    val fqName = FqNameUnsafe(qualifiedName)
    val mappedDescriptor = if (fqName.isSafe) JavaToKotlinClassMap.INSTANCE.mapJavaToKotlin(fqName.toSafe()) else null
    val mappedQName = if (mappedDescriptor == null) null else DescriptorUtils.getFqName(mappedDescriptor).asString()
    if (qualifiedName == mappedQName) return true

    for (superType in classDescriptor.typeConstructor.supertypes) {
        val superDescriptor = superType.constructor.declarationDescriptor

        if (superDescriptor is ClassDescriptor) {
            val superQName = DescriptorUtils.getFqName(superDescriptor).asString()
            if (superQName == qualifiedName || superQName == mappedQName) return true

            if (deep) {
                if (checkSuperTypeByFQName(superDescriptor, qualifiedName, true)) {
                    return true
                }
            }
        }
    }

    return false
}

fun getOwnInnerClasses(classOrObject: KtClassOrObject, fqName: FqName): List<PsiClass> {
    val result = ArrayList<PsiClass>()
    classOrObject.declarations.filterIsInstance<KtClassOrObject>().mapNotNullTo(result) { createLightClass(it) }

    if (classOrObject.hasInterfaceDefaultImpls) {
        result.add(KtLightClassForInterfaceDefaultImpls(fqName.defaultImplsChild(), classOrObject))
    }

    return result
}

fun getClsDelegate(javaFileStub: PsiJavaFileStub, classOrObject: KtClassOrObject, fqName: FqName): PsiClass {
    return LightClassUtil.findClass(fqName, javaFileStub) ?: run {
        val outermostClassOrObject = getOutermostClassOrObject(classOrObject)
        val ktFileText: String? = try {
            outermostClassOrObject.containingFile.text
        }
        catch (e: Exception) {
            "Can't get text for outermost class"
        }

        val stubFileText = DebugUtil.stubTreeToString(javaFileStub)
        throw IllegalStateException("Class was not found $fqName\nin $ktFileText\nstub: \n$stubFileText")
    }
}
