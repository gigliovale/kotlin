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

package org.jetbrains.kotlin.idea.refactoring.inline

import com.intellij.codeInsight.highlighting.HighlightManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.colors.EditorColors
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.psi.PsiElement
import com.intellij.refactoring.HelpID
import com.intellij.refactoring.RefactoringBundle
import com.intellij.refactoring.util.RefactoringMessageDialog
import com.intellij.usageView.UsageInfo
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.*

fun highlightExpressions(project: Project, editor: Editor?, elements: List<PsiElement>) {
    if (editor == null || ApplicationManager.getApplication().isUnitTestMode) return

    val editorColorsManager = EditorColorsManager.getInstance()
    val searchResultsAttributes = editorColorsManager.globalScheme.getAttributes(EditorColors.SEARCH_RESULT_ATTRIBUTES)
    val highlightManager = HighlightManager.getInstance(project)
    highlightManager.addOccurrenceHighlights(editor, elements.toTypedArray(), searchResultsAttributes, true, null)
}

fun showDialog(
        project: Project,
        name: String,
        title: String,
        declaration: KtNamedDeclaration,
        usages: List<KtElement>,
        helpTopic: String? = null
): Boolean {
    if (ApplicationManager.getApplication().isUnitTestMode) return true

    val kind = when (declaration) {
        is KtProperty -> if (declaration.isLocal) "local variable" else "property"
        is KtTypeAlias -> "type alias"
        else -> return false
    }
    val dialog = RefactoringMessageDialog(
            title,
            "Inline " + kind + " '" + name + "'? " + RefactoringBundle.message("occurences.string", usages.size),
            helpTopic,
            "OptionPane.questionIcon",
            true,
            project
    )
    dialog.show()
    return dialog.isOK
}

internal var KtSimpleNameExpression.internalUsageInfos: MutableMap<FqName, (KtSimpleNameExpression) -> UsageInfo?>?
        by CopyableUserDataProperty(Key.create("INTERNAL_USAGE_INFOS"))