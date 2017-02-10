/*
 * Copyright 2010-2017 JetBrains s.r.o.
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

package org.jetbrains.kotlin.idea.search

import com.intellij.psi.PsiClass
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.annotations.TestOnly
import org.jetbrains.kotlin.idea.stubindex.KotlinClassShortNameIndex
import org.jetbrains.kotlin.idea.stubindex.KotlinTypeAliasShortNameIndex
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtSimpleNameExpression
import org.jetbrains.kotlin.psi.KtUserType
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType

/**
 * Can quickly check whether a short name reference in a given file can resolve to the class/interface/type alias
 * with the given qualified name.
 */
class PsiBasedClassResolver(private val targetClassFqName: String) {
    private val targetShortName = targetClassFqName.substringAfterLast('.')
    private val targetPackage = targetClassFqName.substringBeforeLast('.', "")
    private val conflictingPackages = mutableListOf<String>()
    private var forceAmbiguity: Boolean = false

    constructor(target: PsiClass): this(target.qualifiedName ?: "") {
        if (target.qualifiedName == null || target.containingClass != null) {
            forceAmbiguity = true
            return
        }

        findPotentialClassConflicts(target)
        findPotentialTypeAliasConflicts(target)
    }

    private fun findPotentialClassConflicts(target: PsiClass) {
        val candidates = KotlinClassShortNameIndex.getInstance().get(targetShortName, target.project, target.project.allScope())
        for (candidate in candidates) {
            // An inner class can be referenced by short name in subclasses without an explicit import
            if (candidate.containingClassOrObject != null) {
                forceAmbiguity = true
                break
            }

            if (candidate.fqName?.asString() == target.qualifiedName) {
                if (candidate.containingFile != target.navigationElement.containingFile) {
                    forceAmbiguity = true
                    break
                }
            }
            else {
                candidate.fqName?.parent()?.asString()?.let { conflictingPackages.add(it) }
            }
        }
    }

    private fun findPotentialTypeAliasConflicts(target: PsiClass) {
        val candidates = KotlinTypeAliasShortNameIndex.getInstance().get(targetShortName, target.project, target.project.allScope())
        for (candidate in candidates) {
            conflictingPackages.add(candidate.containingKtFile.packageFqName.asString())
        }
    }

    @TestOnly fun addConflict(fqName: String) {
        conflictingPackages.add(fqName.substringBeforeLast('.'))
    }

    /**
     * Checks if a reference with the short name of [targetClassFqName] in the given file will resolve
     * to the target class.
     *
     * @return true if it will definitely resolve to that class, false if it will definitely resolve to something else,
     * null if full resolve is required to answer that question.
     */
    fun canBeTargetReference(ref: KtSimpleNameExpression): Boolean? {
        // The names can be different if the target was imported via an import alias
        if (ref.text != targetShortName) {
            return null
        }

        if (forceAmbiguity) {
            return null
        }

        if (isQualifiedReferenceToTarget(ref)) {
            return null
        }

        val file = ref.containingKtFile
        var result: Result = Result.NothingFound
        val filePackage = file.packageFqName.asString()
        if (filePackage == targetPackage) {
            result = result.changeTo(Result.Found)
        }
        else if (filePackage in conflictingPackages) {
            result = result.changeTo(Result.FoundConflict)
        }

        for (importDirective in file.importDirectives) {
            val qName = importDirective.importedFqName
            if (!importDirective.isAllUnder) {
                if (qName?.asString() == targetClassFqName && importDirective.aliasName == null) {
                    result = result.changeTo(Result.Found)
                }
                else if (qName?.parent()?.asString() in conflictingPackages && importDirective.aliasName == null) {
                    result = result.changeTo(Result.FoundConflict)
                }
                else if (importDirective.aliasName == targetShortName) {
                    result = result.changeTo(Result.FoundConflict)
                }
            }
            else {
                if (qName?.asString() == targetPackage) {
                    result = result.changeTo(Result.Found)
                }
                else if (qName?.asString() in conflictingPackages) {
                    result = result.changeTo(Result.FoundConflict)
                }
            }
        }

        return result.returnValue
    }

    private fun isQualifiedReferenceToTarget(ref: KtSimpleNameExpression): Boolean {
        // A qualified name can resolve to the target element even if it's not imported,
        // but it can also resolve to something else e.g. if the file defines a class with the same name
        // as the top-level package of the target class.
        val qualifier = (ref.parent as? KtUserType)?.qualifier
        if (qualifier != null && qualifier.text == targetPackage) return true

        val dotQualifiedExpression = ref.getParentOfType<KtDotQualifiedExpression>(true)
        if (dotQualifiedExpression != null && PsiTreeUtil.isAncestor(dotQualifiedExpression.selectorExpression, ref, false) &&
            dotQualifiedExpression.receiverExpression.text == targetPackage) {
            return true
        }
        return false
    }

    enum class Result(val returnValue: Boolean?) {
        NothingFound(false),
        Found(true),
        FoundConflict(false),
        Ambiguity(null)
    }

    fun Result.changeTo(newResult: Result): Result {
        if (this == Result.NothingFound || this.returnValue == newResult.returnValue) {
            return newResult
        }
        return Result.Ambiguity
    }
}
