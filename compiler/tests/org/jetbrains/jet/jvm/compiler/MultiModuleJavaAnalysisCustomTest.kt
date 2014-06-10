/*
 * Copyright 2010-2014 JetBrains s.r.o.
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

package org.jetbrains.jet.jvm.compiler

import org.jetbrains.jet.lang.resolve.java.new.JvmAnalyzerFacade
import org.jetbrains.jet.context.GlobalContext
import org.jetbrains.jet.JetTestUtils
import com.intellij.testFramework.UsefulTestCase
import org.jetbrains.jet.lang.resolve.java.new.JvmPlatformParameters
import org.jetbrains.jet.lang.psi.JetFile
import java.io.File
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.jet.lang.resolve.name.Name
import org.jetbrains.jet.lang.resolve.name.FqName
import org.jetbrains.jet.lang.descriptors.ClassDescriptor
import org.jetbrains.jet.lang.descriptors.CallableDescriptor
import org.jetbrains.jet.lang.descriptors.DeclarationDescriptor
import org.junit.Assert
import org.jetbrains.jet.lang.resolve.DescriptorUtils
import org.jetbrains.jet.cli.jvm.compiler.JetCoreEnvironment
import org.jetbrains.jet.config.CompilerConfiguration
import org.jetbrains.jet.cli.jvm.JVMConfigurationKeys
import com.intellij.psi.search.DelegatingGlobalSearchScope
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.jet.lang.types.lang.KotlinBuiltIns
import org.jetbrains.jet.lang.resolve.java.new.JvmAnalyzer
import org.jetbrains.jet.analyzer.new.AnalysisSetup
import org.jetbrains.jet.analyzer.new.ModuleInfo
import java.util.ArrayList

public class MultiModuleJavaAnalysisCustomTest : UsefulTestCase() {

    private class TestModule(val _name: String, val kotlinFiles: List<JetFile>, val javaFilesScope: GlobalSearchScope,
                             val _dependencies: TestModule.() -> List<TestModule>) :
            ModuleInfo<TestModule> {
        override fun dependencies() = _dependencies()
        override val name = Name.identifier(_name)

    }

    fun testJavaEntitiesBelongToCorrectModule() {
        val moduleDirs = File(PATH_TO_TEST_ROOT_DIR).listFiles { it.isDirectory() }!!
        val environment = createEnvironment(moduleDirs)
        val modules = setupModules(environment, moduleDirs)
        val analysis = JvmAnalyzerFacade().setupAnalysis<TestModule>(GlobalContext(), environment.getProject(), modules) {
            module ->
            JvmPlatformParameters(module.kotlinFiles, module.javaFilesScope) {
                javaClass ->
                val moduleName = javaClass.getName().asString().toLowerCase().first().toString()
                modules.first { it._name == moduleName }
            }
        }

        performChecks(analysis, modules)
    }


    private fun createEnvironment(moduleDirs: Array<File>): JetCoreEnvironment {
        val configuration = CompilerConfiguration()
        configuration.addAll(JVMConfigurationKeys.CLASSPATH_KEY, moduleDirs.toList())
        return JetCoreEnvironment.createForTests(getTestRootDisposable()!!, configuration)
    }


    private fun setupModules(environment: JetCoreEnvironment, moduleDirs: Array<File>): List<TestModule> {
        val project = environment.getProject()
        val modules = ArrayList<TestModule>()
        return moduleDirs.mapTo(modules) {
            dir ->
            val name = dir.getName()
            val kotlinFiles = JetTestUtils.loadToJetFiles(environment, dir.listFiles { it.extension == "kt" }?.toList().orEmpty())
            val javaFilesScope = object : DelegatingGlobalSearchScope(GlobalSearchScope.allScope(project)) {
                override fun contains(file: VirtualFile): Boolean {
                    if (file !in myBaseScope!!) return false
                    if (file.isDirectory()) return true
                    return file.getParent()!!.getParent()!!.getName() == name
                }
            }
            TestModule(name, kotlinFiles, javaFilesScope) {
                // module c depends on [c, b, a], b on [b, a], a on [a], order is irrelevant
                modules.filter { other ->
                    this._name.first() <= other._name.first()
                }
            }
        }
    }

    private fun performChecks(analysis: AnalysisSetup<TestModule, JvmAnalyzer>, modules: List<TestModule>) {
        modules.forEach {
            module ->
            val moduleDescriptor = analysis.descriptorByModule[module]!!
            val kotlinPackage = moduleDescriptor.getPackage(FqName.topLevel(Name.identifier("test")))!!
            val kotlinClass
                    = kotlinPackage.getMemberScope().getClassifier(Name.identifier("Kotlin${module._name.toUpperCase()}")) as ClassDescriptor
            checkClass(kotlinClass)

            val javaPackage = moduleDescriptor.getPackage(FqName.topLevel(Name.identifier("custom")))!!
            val classFromJava
                    = javaPackage.getMemberScope().getClassifier(Name.identifier(module._name.toUpperCase() + "Class")) as ClassDescriptor
            checkClass(classFromJava)
        }
    }

    private fun checkClass(classDescriptor: ClassDescriptor) {
        classDescriptor.getDefaultType().getMemberScope().getAllDescriptors().filterIsInstance(javaClass<CallableDescriptor>()).forEach {
            callable ->
            val name = callable.getName().asString()
            when {
                ("return" in name) -> {
                    checkDescriptor(callable.getReturnType()?.getConstructor()?.getDeclarationDescriptor()!!, callable)
                }
                ("parameter" in name) -> {
                    callable.getValueParameters().map {
                        it.getType().getConstructor().getDeclarationDescriptor()!!
                    }.forEach { checkDescriptor(it, callable) }
                }
                ("anno" in name) -> {
                    callable.getAnnotations().map {
                        it.getType().getConstructor().getDeclarationDescriptor()!!
                    }.forEach { checkDescriptor(it, callable) }
                }
            }
            checkSupertypes(classDescriptor)
        }
    }

    private fun checkSupertypes(classDescriptor: ClassDescriptor) {
        classDescriptor.getDefaultType().getConstructor().getSupertypes().filter {
            !KotlinBuiltIns.getInstance().isAnyOrNullableAny(it)
        }.map {
            it.getConstructor().getDeclarationDescriptor()!!
        }.forEach {
            checkDescriptor(it, classDescriptor)
        }
    }

    private fun checkDescriptor(javaClassDescriptor: DeclarationDescriptor, context: DeclarationDescriptor) {
        val descriptorName = javaClassDescriptor.getName().asString()
        val expectedModuleName = descriptorName.toLowerCase().first().toString()
        val moduleName = DescriptorUtils.getContainingModule(javaClassDescriptor).getName().asString()
        Assert.assertEquals(
                "Java class $descriptorName in $context should be in module $expectedModuleName, but instead was in $moduleName",
                expectedModuleName, moduleName
        )
    }

    class object {
        val PATH_TO_TEST_ROOT_DIR = "compiler/testData/multiModule/java/custom"
    }
}