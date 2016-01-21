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

import com.intellij.codeInsight.CodeInsightTestCase
import com.intellij.coverage.*
import com.intellij.execution.Executor
import com.intellij.execution.RunnerRegistry
import com.intellij.execution.actions.ConfigurationContext
import com.intellij.execution.configurations.CommandLineState
import com.intellij.execution.configurations.RunConfigurationBase
import com.intellij.execution.configurations.coverage.JavaCoverageEnabledConfiguration
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.actionSystem.LangDataKeys
import com.intellij.openapi.extensions.Extensions
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.impl.JavaAwareProjectJdkTableImpl
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.JavaPsiFacade
import com.intellij.testFramework.TestDataProvider
import com.intellij.ui.classFilter.ClassFilter
import com.intellij.util.containers.hash.HashMap
import org.jetbrains.kotlin.idea.test.PluginTestCaseBase
import org.jetbrains.kotlin.test.MockLibraryUtil
import java.io.File

class KotlinCoverageIntegrationTest : CodeInsightTestCase() {
    override fun getTestDataPath(): String {
        return PluginTestCaseBase.getTestDataPathBase() + "/coverage/integration/kotlinCoverage/"
    }

    inner class MyTestDataProvider : TestDataProvider(myProject) {
        override fun getData(dataId: String): Any? {
            if (LangDataKeys.MODULE.`is`(dataId)) {
                return myModule
            }
            return super.getData(dataId)
        }
    }

    override fun getTestProjectJdk(): Sdk? {
        return JavaAwareProjectJdkTableImpl.getInstanceEx().internalJdk
    }

    override fun setUpProject() {
        myProject = ProjectManagerEx.getInstanceEx().loadProject(testDataPath)
        ProjectManagerEx.getInstanceEx().openTestProject(myProject)
        myModule = ModuleManager.getInstance(myProject).modules.single()
        setUpJdk()
        runStartupActivities()

        CoverageOptionsProvider.getInstance(myProject).setOptionsToReplace(0)  // replace active suites

        val outDir = File(testDataPath, "out/production/kotlinCoverage")
        MockLibraryUtil.compileKotlin(File(testDataPath, "src").path, outDir)
        LocalFileSystem.getInstance().refreshIoFiles(listOf(outDir))
    }

    fun testSimple() {
        configureByFile("src/demo.kt")
        val coverageFilePath = executeRunConfigurationFromContext()
        val bundle = loadCoverageSuite(IDEACoverageRunner::class.java, coverageFilePath)

        val consumer = annotatePackage(bundle)
        val demoKtClassCoverage = consumer.classCoverageInfoMap["demo.DemoKt"]!!
        assertEquals(4, demoKtClassCoverage.fullyCoveredLineCount)
        assertEquals(4, demoKtClassCoverage.totalLineCount)
    }

    private fun executeRunConfigurationFromContext(): String {
        val configurationContext = ConfigurationContext.getFromContext(MyTestDataProvider())
        val runnerAndConfigurationSettings = configurationContext.configuration!!

        val coverageEnabledConfiguration = JavaCoverageEnabledConfiguration.getFrom(
                runnerAndConfigurationSettings.configuration as RunConfigurationBase)!!
        coverageEnabledConfiguration.coveragePatterns = arrayOf(ClassFilter("demo.*"))

        val coverageExecutor = Extensions.getExtensions(Executor.EXECUTOR_EXTENSION_NAME).single {
            it.id == CoverageExecutor.EXECUTOR_ID
        }
        val runner = RunnerRegistry.getInstance().findRunnerById("Cover")!!
        val executionEnvironment = ExecutionEnvironment(coverageExecutor, runner, runnerAndConfigurationSettings, project)
        val runProfileState = executionEnvironment.state as CommandLineState
        runProfileState.consoleBuilder = null
        val executionResult = runProfileState.execute(coverageExecutor, runner)
        executionResult.processHandler.startNotify()
        assertTrue(executionResult.processHandler.waitFor())
        Disposer.dispose(executionEnvironment)

        return coverageEnabledConfiguration.coverageFilePath!!
    }

    private fun loadCoverageSuite(coverageRunnerClass: Class<out CoverageRunner>, coverageDataPath: String): CoverageSuitesBundle {
        val runner = CoverageRunner.getInstance(coverageRunnerClass)
        val fileProvider = DefaultCoverageFileProvider(coverageDataPath)
        val suite = JavaCoverageEngine.getInstance().createCoverageSuite(runner, "Simple", fileProvider, null, -1, null, false, false, false, myProject)
        val bundle = CoverageSuitesBundle(suite)
        CoverageDataManager.getInstance(myProject).chooseSuitesBundle(bundle)
        assertNotNull(CoverageDataManager.getInstance(myProject).currentSuitesBundle)
        return bundle
    }

    private fun annotatePackage(bundle: CoverageSuitesBundle): PackageAnnotationConsumer {
        val psiPackage = JavaPsiFacade.getInstance(myProject).findPackage("demo")
        val annotator = PackageAnnotator(psiPackage)
        val consumer = PackageAnnotationConsumer()
        annotator.annotate(bundle, consumer)
        return consumer
    }

    private class PackageAnnotationConsumer : PackageAnnotator.Annotator {
        private val myDirectoryCoverage = HashMap<VirtualFile, PackageAnnotator.PackageCoverageInfo>()
        private val myPackageCoverage = HashMap<String, PackageAnnotator.PackageCoverageInfo>()
        private val myFlatPackageCoverage = HashMap<String, PackageAnnotator.PackageCoverageInfo>()
        val classCoverageInfoMap = HashMap<String, PackageAnnotator.ClassCoverageInfo>()

        override fun annotateSourceDirectory(virtualFile: VirtualFile?, packageCoverageInfo: PackageAnnotator.PackageCoverageInfo, module: Module) {
            myDirectoryCoverage.put(virtualFile, packageCoverageInfo)
        }

        override fun annotateTestDirectory(virtualFile: VirtualFile, packageCoverageInfo: PackageAnnotator.PackageCoverageInfo, module: Module) {
            myDirectoryCoverage.put(virtualFile, packageCoverageInfo)
        }

        override fun annotatePackage(packageQualifiedName: String, packageCoverageInfo: PackageAnnotator.PackageCoverageInfo) {
            myPackageCoverage.put(packageQualifiedName, packageCoverageInfo)
        }

        override fun annotatePackage(packageQualifiedName: String, packageCoverageInfo: PackageAnnotator.PackageCoverageInfo, flatten: Boolean) {
            (if (flatten) myFlatPackageCoverage else myPackageCoverage).put(packageQualifiedName, packageCoverageInfo)
        }

        override fun annotateClass(classQualifiedName: String, classCoverageInfo: PackageAnnotator.ClassCoverageInfo) {
            classCoverageInfoMap[classQualifiedName] = classCoverageInfo
        }
    }
}
