/*
 * Copyright 2010-2015 JetBrains s.r.o.
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

package org.jetbrains.kotlin.android.tests;

import com.google.common.io.Files;
import com.intellij.openapi.util.io.FileUtil;
import junit.framework.TestCase;
import junit.framework.TestSuite;
import kotlin.io.IoPackage;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.List;

public class AndroidRunner extends TestSuite {

    private static PathManager pathManager;

    @NotNull
    public static PathManager getPathManager() {
        if (pathManager == null) {
            File tmpFolder = Files.createTempDir();
            System.out.println("Created temporary folder for running android tests: " + tmpFolder.getAbsolutePath());
            File rootFolder = new File("");
            pathManager = new PathManager(rootFolder.getAbsolutePath(), tmpFolder.getAbsolutePath());
        }
        return pathManager;
    }

    public static TestSuite suite() throws Throwable {
        PathManager pathManager = getPathManager();

        FileUtil.copyDir(new File(pathManager.getAndroidModuleRoot()), new File(pathManager.getTmpFolder()));

        CodegenTestsOnAndroidGenerator generator = new CodegenTestsOnAndroidGenerator(pathManager);
        CodegenTestsOnAndroidRunner runner = new CodegenTestsOnAndroidRunner(AndroidRunner.pathManager);

        generator.prepareAndroidModule();
        runner.downloadDependencies();

        TestSuite suite = new TestSuite("Android tests");

        runner.startEmulator();

        try {
            runTestsOnAndroidDevice(generator, runner, suite, "compiler/testData/codegen/box");

            IoPackage.deleteRecursively(new File(pathManager.getOutputForCompiledFiles()));

            runTestsOnAndroidDevice(generator, runner, suite, "compiler/testData/codegen/boxWithStdlib");
        }
        finally {
            runner.stopEmulator();
        }

        suite.addTest(new AndroidJpsBuildTestCase());
        return suite;
    }

    private static void runTestsOnAndroidDevice(
            CodegenTestsOnAndroidGenerator generator,
            CodegenTestsOnAndroidRunner runner,
            TestSuite suite,
            String folder
    ) throws Throwable {
        generator.generateAndSave(new File(folder));

        System.out.println("Run tests on android...");
        List<TestCase> boxTests = runner.getTests(folder);
        for (TestCase test : boxTests) {
            suite.addTest(test);
        }
    }

    public void tearDown() throws Exception {
        // Clear tmp folder where we run android tests
        FileUtil.delete(new File(pathManager.getTmpFolder()));
    }
}
