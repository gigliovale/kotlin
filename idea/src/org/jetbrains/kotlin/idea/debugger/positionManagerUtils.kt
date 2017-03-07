/*
 * Copyright 2010-2015 KtBrains s.r.o.
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

package org.jetbrains.kotlin.idea.debugger

import com.intellij.debugger.SourcePosition
import com.intellij.debugger.engine.DebugProcess
import com.intellij.debugger.engine.DebuggerUtils
import com.intellij.openapi.roots.libraries.LibraryUtil
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.sun.jdi.AbsentInformationException
import com.sun.jdi.ReferenceType
import org.jetbrains.kotlin.codegen.AsmUtil
import org.jetbrains.kotlin.descriptors.PropertyDescriptor
import org.jetbrains.kotlin.fileClasses.NoResolveFileClassesProvider
import org.jetbrains.kotlin.fileClasses.getFileClassInternalName
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.caches.resolve.resolveToDescriptor
import org.jetbrains.kotlin.idea.util.application.runReadAction
import org.jetbrains.kotlin.load.java.JvmAbi
import org.jetbrains.kotlin.load.kotlin.PackagePartClassUtils
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getElementTextWithContext
import org.jetbrains.kotlin.psi.psiUtil.isObjectLiteral
import org.jetbrains.kotlin.resolve.calls.callUtil.getResolvedCall
import org.jetbrains.kotlin.resolve.inline.InlineUtil

fun KtClassOrObject.getNameForNonAnonymousClass(addTraitImplSuffix: Boolean = true): String? {
    if (isLocal()) return null
    if (this.isObjectLiteral()) return null

    val name = getName() ?: return null

    val parentClass = PsiTreeUtil.getParentOfType(this, KtClassOrObject::class.java, true)
    if (parentClass != null) {
        val shouldAddTraitImplSuffix = !(parentClass is KtClass && this is KtObjectDeclaration && this.isCompanion())
        val parentName = parentClass.getNameForNonAnonymousClass(shouldAddTraitImplSuffix)
        if (parentName == null) {
            return null
        }
        return parentName + "$" + name
    }

    val className = if (addTraitImplSuffix && this is KtClass && this.isInterface()) name + JvmAbi.DEFAULT_IMPLS_SUFFIX else name

    val packageFqName = this.getContainingKtFile().getPackageFqName()
    return if (packageFqName.isRoot()) className else packageFqName.asString() + "." + className
}

private fun PsiElement.getElementToCalculateClassName(): KtElement? {
    val result = PsiTreeUtil.getParentOfType(this,
                                             KtFile::class.java,
                                             KtClassOrObject::class.java,
                                             KtFunctionLiteral::class.java,
                                             KtClassInitializer::class.java,
                                             KtNamedFunction::class.java,
                                             KtPropertyAccessor::class.java,
                                             KtProperty::class.java)
    return result as? KtElement
}

private fun PsiElement.getClassOfFileClassName(): KtElement? {
    return PsiTreeUtil.getParentOfType(this, KtFile::class.java, KtClassOrObject::class.java)
}

fun DebugProcess.getAllClasses(position: SourcePosition): List<ReferenceType> {

    var depthToLocalOrAnonymousClass = 0

    fun calc(element: KtElement?, previousChild: KtElement): String? {
        when {
            element == null -> return null
            element is KtClassOrObject -> {
                if (element.isLocal()) {
                    depthToLocalOrAnonymousClass++
                    return calc(element.getElementToCalculateClassName(), element)
                }

                return element.getNameForNonAnonymousClass()
            }
            element is KtFunctionLiteral -> {
                if (!isInlinedLambda(element)) {
                    depthToLocalOrAnonymousClass++
                }
                return calc(element.getElementToCalculateClassName(), element)
            }
            element is KtClassInitializer -> {
                val parent = element.getParent().getElementToCalculateClassName()

                if (parent is KtObjectDeclaration && parent.isCompanion()) {
                    // Companion-object initializer
                    return calc(parent.getElementToCalculateClassName(), parent)
                }

                return calc(parent, element)
            }
            element is KtPropertyAccessor -> {
                return calc(element.getClassOfFileClassName(), element)
            }
            element is KtProperty -> {
                if (element.isTopLevel() || element.isLocal()) {
                    return calc(element.getElementToCalculateClassName(), element)
                }

                val containingClass = element.getClassOfFileClassName()
                if (containingClass is KtObjectDeclaration && containingClass.isCompanion()) {
                    // Properties from companion object are moved into class
                    val descriptor = element.resolveToDescriptor() as PropertyDescriptor
                    if (AsmUtil.isPropertyWithBackingFieldCopyInOuterClass(descriptor)) {
                        return calc(containingClass.getElementToCalculateClassName(), containingClass)
                    }
                }

                return calc(containingClass, element)
            }
            element is KtNamedFunction -> {
                if (element.isLocal()) {
                    depthToLocalOrAnonymousClass++
                }
                val parent = element.getElementToCalculateClassName()
                if (parent is KtClassInitializer) {
                    // TODO BUG? anonymous functions from companion object constructor should be inner class of companion object, not class
                    return calc(parent.getElementToCalculateClassName(), parent)
                }

                return calc(parent, element)
            }
            element is KtFile -> {
                /*val isInLibrary = LibraryUtil.findLibraryEntry(element.getVirtualFile(), element.getProject()) != null
                if (isInLibrary) {
                    val elementAtForLibraryFile = getElementToCreateTypeMapperForLibraryFile(previousChild)
                    assert(elementAtForLibraryFile != null) {
                        "Couldn't find element at breakpoint for library file " + element.getContainingKtFile().getName() +
                        ", element = " + element.getElementTextWithContext()
                    }
                    return NoResolveFileClassesProvider.getFileClassInternalName(element)
                }*/
                return NoResolveFileClassesProvider.getFileClassInternalName(element)
            }
            else -> throw IllegalStateException("Unsupported container ${element.javaClass}")
        }
    }

    val className = runReadAction {
        val elementToCalcClassName = position.getElementAt().getElementToCalculateClassName()
        calc(elementToCalcClassName, PsiTreeUtil.getParentOfType(position.getElementAt(), KtElement::class.java)!!)
    }

    if (className == null) {
        return emptyList()
    }

    if (depthToLocalOrAnonymousClass == 0) {
        return getVirtualMachineProxy().classesByName(className)
    }

    // the name is a parent class for a local or anonymous class
    val outers = getVirtualMachineProxy().classesByName(className)
    return outers.map { findNested(it, 0, depthToLocalOrAnonymousClass, position) }.filterNotNull()
}

private fun getElementToCreateTypeMapperForLibraryFile(element: PsiElement?) =
        if (element is KtDeclaration) element else PsiTreeUtil.getParentOfType(element, KtDeclaration::class.java)

private fun DebugProcess.findNested(
        fromClass: ReferenceType,
        currentDepth: Int,
        requiredDepth: Int,
        position: SourcePosition
): ReferenceType? {
    val vmProxy = getVirtualMachineProxy()
    if (fromClass.isPrepared()) {
        try {
            if (currentDepth < requiredDepth) {
                val nestedTypes = vmProxy.nestedTypes(fromClass)
                for (nested in nestedTypes) {
                    val found = findNested(nested, currentDepth + 1, requiredDepth, position)
                    if (found != null) {
                        return found
                    }
                }
                return null
            }

            for (location in fromClass.allLineLocations()) {
                val locationLine = location.lineNumber() - 1
                if (locationLine <= 0) {
                    // such locations are not correspond to real lines in code
                    continue
                }
                val method = location.method()
                if (method == null || DebuggerUtils.isSynthetic(method) || method.isBridge()) {
                    // skip synthetic methods
                    continue
                }

                val positionLine = position.getLine()
                if (positionLine == locationLine) {
                    if (position.getElementAt() == null) return fromClass

                    val candidatePosition = KotlinPositionManager(this).getSourcePosition(location)
                    if (candidatePosition?.getElementAt() == position.getElementAt()) {
                        return fromClass
                    }
                }
            }
        }
        catch (ignored: AbsentInformationException) {
        }

    }
    return null
}

public fun isInlinedLambda(functionLiteral: KtFunctionLiteral): Boolean {
    val functionLiteralExpression = functionLiteral.getParent() ?: return false

    var parent = functionLiteralExpression.getParent()

    var valueArgument: PsiElement = functionLiteralExpression
    while (parent is KtParenthesizedExpression || parent is KtBinaryExpressionWithTypeRHS || parent is KtLabeledExpression) {
        valueArgument = parent
        parent = parent.getParent()
    }

    while (parent is ValueArgument || parent is KtValueArgumentList) {
        parent = parent.getParent()
    }

    if (parent !is KtElement) return false

    return InlineUtil.isInlinedArgument(functionLiteral, functionLiteral.analyze(), false)
}