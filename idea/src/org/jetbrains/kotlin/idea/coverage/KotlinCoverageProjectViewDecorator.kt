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

package org.jetbrains.kotlin.idea.coverage

import com.intellij.coverage.*
import com.intellij.ide.projectView.PresentationData
import com.intellij.ide.projectView.ProjectViewNode
import com.intellij.openapi.project.Project
import com.intellij.packageDependencies.ui.PackageDependenciesNode
import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.ui.ColoredTreeCellRenderer
import org.jetbrains.kotlin.asJava.LightClassGenerationSupport
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtFile

class KotlinCoverageProjectViewDecorator(coverageDataManager: CoverageDataManager) : AbstractCoverageProjectViewNodeDecorator(coverageDataManager) {
    override fun decorate(node: ProjectViewNode<*>, data: PresentationData) {
        val coverageDataManager = coverageDataManager
        val currentSuite = coverageDataManager.currentSuitesBundle

        val project = node.project!!
        val javaCovAnnotator = getCovAnnotator(currentSuite, project) ?: return
        // This decorator is applicable only to JavaCoverageAnnotator

        val value = node.value
        val element: PsiElement? = when(value) {
            is PsiElement -> value
            is SmartPsiElementPointer<*> -> (value as SmartPsiElementPointer<PsiElement>).element
            else -> null
        }

        if (element is KtFile) {
            val info = KotlinCoverageExtension.getSummaryCoverageForFile(javaCovAnnotator, element)
            data.locationString = renderCoverageInfo(info)
        }
        else if (element is KtClass) {
            val qName = LightClassGenerationSupport.getInstance(project).getLightClass(element)?.qualifiedName
            if (qName != null) {
                data.locationString = javaCovAnnotator.getClassCoverageInformationString(qName, coverageDataManager)
            }
        }
    }

    override fun decorate(node: PackageDependenciesNode?, cellRenderer: ColoredTreeCellRenderer?) {
    }

    private fun getCovAnnotator(currentSuite: CoverageSuitesBundle?, project: Project)
        = currentSuite?.getAnnotator(project) as? JavaCoverageAnnotator

    private fun renderCoverageInfo(info: PackageAnnotator.ClassCoverageInfo?): String? {
        if (info == null) return null

        if (info.totalMethodCount == 0 || info.totalLineCount == 0) return null
        if (coverageDataManager.isSubCoverageActive) {
            return if (info.coveredMethodCount + info.fullyCoveredLineCount + info.partiallyCoveredLineCount > 0) "covered" else null
        }
        val coveredMethodPercentage = (info.coveredMethodCount.toDouble() / info.totalMethodCount * 100).toInt()
        val coveredLinePercentage = ((info.fullyCoveredLineCount + info.partiallyCoveredLineCount).toDouble() / info.totalLineCount * 100).toInt()
        return "$coveredMethodPercentage% methods, $coveredLinePercentage% lines covered"
    }
}