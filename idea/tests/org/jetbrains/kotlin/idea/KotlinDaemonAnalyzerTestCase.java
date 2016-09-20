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

package org.jetbrains.kotlin.idea;

import com.intellij.codeInsight.daemon.DaemonAnalyzerTestCase;
import com.intellij.concurrency.IdeaForkJoinWorkerThreadFactory;
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess;
import com.intellij.testFramework.ThreadTracker;
import org.jetbrains.kotlin.test.KotlinTestUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.ForkJoinPool;

abstract public class KotlinDaemonAnalyzerTestCase extends DaemonAnalyzerTestCase {
    static {
        System.out.println("Static: " + System.getProperty("java.util.concurrent.ForkJoinPool.common.threadFactory") + "\n");

        String classpath = System.getProperty("java.class.path");
        String[] classpathEntries = classpath.split(File.pathSeparator);
        for (String entry : classpathEntries) {
            System.out.println(entry);
        }

        System.out.println(ClassLoader.getSystemClassLoader() + "\n");
        System.out.println(ClassLoader.getSystemClassLoader().getClass() + "\n");

        System.out.println("---------");

        System.out.println(KotlinDaemonAnalyzerTestCase.class.getClassLoader() + "\n");
        System.out.println(KotlinDaemonAnalyzerTestCase.class.getClassLoader().getClass() + "\n");

        System.out.println("---------");

        ForkJoinPool.ForkJoinWorkerThreadFactory factory = null;
        try {  // ignore exceptions in accessing/parsing properties
            String fp = System.getProperty
                    ("java.util.concurrent.ForkJoinPool.common.threadFactory");
            if (fp != null)
                factory = ((ForkJoinPool.ForkJoinWorkerThreadFactory)ClassLoader.
                        getSystemClassLoader().loadClass(fp).newInstance());
        } catch (Exception ignore) {
            System.out.println(ignore);
        }
    }

    @Override
    protected void setUp() throws Exception {
        System.out.println("Before All: " + System.getProperty("java.util.concurrent.ForkJoinPool.common.threadFactory"));

        IdeaForkJoinWorkerThreadFactory.setupForkJoinCommonPool();
        System.out.println("Before setup: " + System.getProperty("java.util.concurrent.ForkJoinPool.common.threadFactory"));
        System.out.println("Before setup: " + ForkJoinPool.commonPool().getFactory().getClass());
        VfsRootAccess.allowRootAccess(KotlinTestUtils.getHomeDirectory());

        super.setUp();

        System.out.println("After setup: " + ForkJoinPool.commonPool().getFactory().getClass());
        System.out.println("After setup: " + System.getProperty("java.util.concurrent.ForkJoinPool.common.threadFactory"));
        printThreadNames();
    }

    public static void printThreadNames() {
        Collection<Thread> threads = ThreadTracker.getThreads();
        Collection<String> names = new ArrayList<String>();
        for (Thread thread : threads) {
            names.add(thread.getName());
        }

        System.out.println(names);
    }

    @Override
    protected void tearDown() throws Exception {
        try {
            super.tearDown();
        }
        catch (AssertionError ae) {
            System.out.println("BBB!!!");
            System.out.println("After: " + ForkJoinPool.commonPool().getFactory().getClass());
            System.out.println("After: " + System.getProperty("java.util.concurrent.ForkJoinPool.common.threadFactory"));
            printThreadNames();
            throw ae;
        }
        VfsRootAccess.disallowRootAccess(KotlinTestUtils.getHomeDirectory());
    }
}
