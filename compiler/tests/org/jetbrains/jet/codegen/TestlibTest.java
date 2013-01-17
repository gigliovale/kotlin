/*
 * Copyright 2010-2012 JetBrains s.r.o.
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

package org.jetbrains.jet.codegen;

import com.google.common.base.Predicates;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import gnu.trove.THashSet;
import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jet.ConfigurationKind;
import org.jetbrains.jet.JetTestUtils;
import org.jetbrains.jet.TestJdkKind;
import org.jetbrains.jet.analyzer.AnalyzeExhaust;
import org.jetbrains.jet.cli.common.CLIConfigurationKeys;
import org.jetbrains.jet.cli.common.messages.MessageCollectorPlainTextToStream;
import org.jetbrains.jet.cli.jvm.JVMConfigurationKeys;
import org.jetbrains.jet.cli.jvm.compiler.JetCoreEnvironment;
import org.jetbrains.jet.codegen.forTestCompile.ForTestCompileRuntime;
import org.jetbrains.jet.codegen.state.GenerationState;
import org.jetbrains.jet.codegen.state.JetTypeMapper;
import org.jetbrains.jet.codegen.state.JetTypeMapperMode;
import org.jetbrains.jet.config.CommonConfigurationKeys;
import org.jetbrains.jet.config.CompilerConfiguration;
import org.jetbrains.jet.lang.descriptors.ClassDescriptor;
import org.jetbrains.jet.lang.psi.JetClass;
import org.jetbrains.jet.lang.psi.JetDeclaration;
import org.jetbrains.jet.lang.psi.JetFile;
import org.jetbrains.jet.lang.resolve.AnalyzerScriptParameter;
import org.jetbrains.jet.lang.resolve.BindingContext;
import org.jetbrains.jet.lang.resolve.DescriptorUtils;
import org.jetbrains.jet.lang.resolve.java.AnalyzerFacadeForJVM;
import org.jetbrains.jet.lang.types.JetType;
import org.jetbrains.jet.parsing.JetParsingTest;
import org.jetbrains.jet.utils.ExceptionUtils;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.jetbrains.jet.lang.resolve.ModuleDescriptorProviderFactory.createModuleDescriptorProviderForOneModule;

public class TestlibTest extends CodegenTestCase {

    private File junitJar;

    public static Test suite() {
        return new TestlibTest().buildSuite();
    }

    protected TestSuite buildSuite() {
        try {
            setUp();
            return doBuildSuite();
        }
        catch (Exception e) {
            throw ExceptionUtils.rethrow(e);
        }
        finally {
            try {
                tearDown();
            }
            catch (Exception e) {
                throw ExceptionUtils.rethrow(e);
            }
        }
    }

    private TestSuite doBuildSuite() {
        try {
            GenerationState generationState = compileAndGetGenerationState();
            ClassFileFactory classFileFactory = generationState.getFactory();
            final GeneratedClassLoader loader = new GeneratedClassLoader(
                    classFileFactory,
                    new URLClassLoader(new URL[] {ForTestCompileRuntime.runtimeJarForTests().toURI().toURL(), junitJar.toURI().toURL()},
                                       TestCase.class.getClassLoader()));

            JetTypeMapper typeMapper = generationState.getTypeMapper();
            try {
                return buildTestSuite(generationState, loader, typeMapper);
            }
            finally {
                typeMapper = null;
            }
        }
        catch (Exception e) {
            throw ExceptionUtils.rethrow(e);
        }
    }

    @NotNull
    private GenerationState compileAndGetGenerationState() {
        Project project = myEnvironment.getProject();
        List<JetFile> files = myEnvironment.getSourceFiles();
        // NOTE: this is a hack
        // we need to compile our test code as if it is in the same module with our library code
        // but compiled library is in runtime jar
        // so we hack ModuleDescriptorProvider so it always returns the same module
        final AnalyzeExhaust analyzeExhaust = AnalyzerFacadeForJVM
                .analyzeFilesWithJavaIntegration(project, files, Collections.<AnalyzerScriptParameter>emptyList(),
                                                 Predicates.<PsiFile>alwaysTrue(),
                                                 false,
                                                 createModuleDescriptorProviderForOneModule(project, "module")
                );
        return GenerationUtils.compileFilesGetGenerationState(project, analyzeExhaust, files);
    }

    private TestSuite buildTestSuite(GenerationState generationState, GeneratedClassLoader loader, JetTypeMapper typeMapper)
            throws ClassNotFoundException {
        TestSuite suite = new TestSuite("stdlib_test");
        for (JetFile jetFile : myEnvironment.getSourceFiles()) {
            for (JetDeclaration decl : jetFile.getDeclarations()) {
                if (decl instanceof JetClass) {
                    ClassDescriptor classDescriptor =
                            (ClassDescriptor) generationState.getBindingContext().get(BindingContext.DECLARATION_TO_DESCRIPTOR, decl);
                    mayBeAddTestCaseToTheSuite(loader, typeMapper, suite, classDescriptor);
                }
            }
        }
        return suite;
    }

    private static void mayBeAddTestCaseToTheSuite(
            GeneratedClassLoader loader,
            JetTypeMapper typeMapper,
            TestSuite suite,
            ClassDescriptor classDescriptor
    ) throws ClassNotFoundException {

        Set<JetType> allSuperTypes = new THashSet<JetType>();
        DescriptorUtils.addSuperTypes(classDescriptor.getDefaultType(), allSuperTypes);

        for (JetType type : allSuperTypes) {
            String internalName = typeMapper.mapType(type, JetTypeMapperMode.IMPL).getInternalName();
            if (internalName.equals("junit/framework/Test")) {
                String name = typeMapper.mapType(classDescriptor.getDefaultType(), JetTypeMapperMode.IMPL).getInternalName();
                System.out.println(name);
                Class<TestCase> aClass = (Class<TestCase>) loader.loadClass(name.replace('/', '.'));
                if ((aClass.getModifiers() & Modifier.ABSTRACT) == 0
                    && (aClass.getModifiers() & Modifier.PUBLIC) != 0) {
                    try {
                        Constructor<TestCase> constructor = aClass.getConstructor();
                        if (constructor != null && (constructor.getModifiers() & Modifier.PUBLIC) != 0) {
                            suite.addTestSuite(aClass);
                        }
                    }
                    catch (NoSuchMethodException e) {
                    }
                }
                break;
            }
        }
    }

    @Override
    public void setUp() throws Exception {
        super.setUp();
        CompilerConfiguration configuration = JetTestUtils.compilerConfigurationForTests(ConfigurationKind.ALL,
                                                                                         TestJdkKind.FULL_JDK);
        configuration.add(JVMConfigurationKeys.CLASSPATH_KEY, JetTestUtils.getAnnotationsJar());

        junitJar = new File("libraries/lib/junit-4.9.jar");
        assertTrue(junitJar.exists());
        configuration.add(JVMConfigurationKeys.CLASSPATH_KEY, junitJar);

        configuration.add(CommonConfigurationKeys.SOURCE_ROOTS_KEY, JetParsingTest.getTestDataDir() + "/../../libraries/stdlib/test");
        configuration.add(CommonConfigurationKeys.SOURCE_ROOTS_KEY, JetParsingTest.getTestDataDir() + "/../../libraries/kunit/src");
        configuration.put(CLIConfigurationKeys.MESSAGE_COLLECTOR_KEY, MessageCollectorPlainTextToStream.PLAIN_TEXT_TO_SYSTEM_ERR);

        myEnvironment = new JetCoreEnvironment(getTestRootDisposable(), configuration);
    }
}
