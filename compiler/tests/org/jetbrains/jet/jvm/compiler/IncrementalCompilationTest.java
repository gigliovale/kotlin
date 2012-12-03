/*
 * Copyright 2010-2013 JetBrains s.r.o.
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

package org.jetbrains.jet.jvm.compiler;

import com.google.common.base.Predicates;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jet.ConfigurationKind;
import org.jetbrains.jet.TestJdkKind;
import org.jetbrains.jet.analyzer.AnalyzeExhaust;
import org.jetbrains.jet.cli.jvm.compiler.CompileEnvironmentUtil;
import org.jetbrains.jet.cli.jvm.compiler.JetCoreEnvironment;
import org.jetbrains.jet.codegen.ClassFileFactory;
import org.jetbrains.jet.codegen.state.GenerationState;
import org.jetbrains.jet.config.CompilerConfiguration;
import org.jetbrains.jet.lang.descriptors.ModuleDescriptor;
import org.jetbrains.jet.lang.descriptors.NamespaceDescriptor;
import org.jetbrains.jet.lang.psi.JetFile;
import org.jetbrains.jet.lang.resolve.*;
import org.jetbrains.jet.lang.resolve.name.Name;
import org.jetbrains.jet.test.TestCaseWithTmpdir;

import java.io.File;
import java.io.IOException;
import java.util.Collections;

import static org.jetbrains.jet.JetTestUtils.compilerConfigurationForTests;
import static org.jetbrains.jet.codegen.GenerationUtils.compileFilesGetGenerationState;
import static org.jetbrains.jet.jvm.compiler.LoadDescriptorUtil.TEST_PACKAGE_FQNAME;
import static org.jetbrains.jet.jvm.compiler.LoadDescriptorUtil.loadTestNamespaceAndBindingContextFromBinaries;
import static org.jetbrains.jet.lang.psi.JetPsiFactory.createFile;
import static org.jetbrains.jet.lang.resolve.java.AnalyzerFacadeForJVM.analyzeFilesWithJavaIntegration;
import static org.jetbrains.jet.test.util.NamespaceComparator.DONT_INCLUDE_METHODS_OF_OBJECT;
import static org.jetbrains.jet.test.util.NamespaceComparator.compareNamespaces;


/*

* Compiles files one by one. Considers that all files are in the same module.
*
* */
public class IncrementalCompilationTest extends TestCaseWithTmpdir {

    @NotNull
    private static final String PATH = "compiler/testData/incrementalCompilation";

    @NotNull
    private AnalyzeExhaust compileFile(@NotNull File file) throws IOException {
        CompilerConfiguration compilerConfiguration =
                compilerConfigurationForTests(ConfigurationKind.JDK_ONLY, TestJdkKind.MOCK_JDK, tmpdir);
        JetCoreEnvironment environment = new JetCoreEnvironment(getTestRootDisposable(), compilerConfiguration);
        Project project = environment.getProject();
        JetFile jetFile = createFile(project, FileUtil.loadFile(file, true));
        ModuleDescriptor kotlinModule = new ModuleDescriptor(Name.special("<module>"));
        ModuleDescriptorProvider moduleDescriptorProvider = ModuleDescriptorProviderFactory
                .createModuleDescriptorProviderForOneModule(project, kotlinModule);
        AnalyzeExhaust exhaust = analyzeFilesWithJavaIntegration(project, Collections.singletonList(jetFile),
                                                                 Collections.<AnalyzerScriptParameter>emptyList(),
                                                                 Predicates.<PsiFile>alwaysTrue(),
                                                                 false,
                                                                 kotlinModule, moduleDescriptorProvider);
        AnalyzingUtils.throwExceptionOnErrors(exhaust.getBindingContext());
        GenerationState state = compileFilesGetGenerationState(project, exhaust, Collections.singletonList(jetFile));
        ClassFileFactory classFileFactory = state.getFactory();
        CompileEnvironmentUtil.writeToOutputDirectory(classFileFactory, tmpdir);
        return exhaust;
    }

    public void testSimple() throws Exception {
        doTest();
    }

    public void testInternalVisibility() throws Exception {
        doTest();
    }

    private void doTest() throws IOException {
        File dir = new File(pathToDir());
        assertTrue(pathToDir() + " should be a directory", dir.isDirectory());
        int filesCompiled = compileAllFilesInDir(pathToDir());
        assertEquals("Must compile all files in directory", countKotlinFilesInDir(dir), filesCompiled);
    }

    private static int countKotlinFilesInDir(@NotNull File dir) {
        File[] files = dir.listFiles();
        assert files != null;
        int count = 0;
        for (File file : files) {
            if (file.getName().endsWith(".kt")) {
                count++;
            }
        }
        return count;
    }

    private String pathToDir() {
        String testName = getTestName(true);
        return PATH + "/" + testName;
    }

    private int compileAllFilesInDir(@NotNull String dirName) throws IOException {
        int fileNum = 1;
        AnalyzeExhaust exhaust = null;
        while (true) {
            String fileName = dirName + "/" + fileNum + ".kt";
            File file = new File(fileName);
            if (file.exists()) {
                exhaust = compileFile(file);
                ++fileNum;
            }
            else {
                assert exhaust != null;
                doCompare(exhaust);
                break;
            }
        }
        return fileNum - 1;
    }

    private void doCompare(@NotNull AnalyzeExhaust exhaust) {
        NamespaceDescriptor namespaceFromBinaries =
                loadTestNamespaceAndBindingContextFromBinaries(tmpdir, getTestRootDisposable(), ConfigurationKind.JDK_ONLY).first;
        NamespaceDescriptor namespaceFromIncrementallyCompiledSource
                = exhaust.getBindingContext().get(BindingContext.FQNAME_TO_NAMESPACE_DESCRIPTOR, TEST_PACKAGE_FQNAME);
        assert namespaceFromIncrementallyCompiledSource != null;
        compareNamespaces(namespaceFromBinaries, namespaceFromIncrementallyCompiledSource, DONT_INCLUDE_METHODS_OF_OBJECT,
                          new File(pathToDir() + "/expected.txt"));
    }
}
