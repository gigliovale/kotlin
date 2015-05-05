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

package org.jetbrains.kotlin.resolve;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.kotlin.context.GlobalContext;
import org.jetbrains.kotlin.storage.ExceptionTracker;
import org.jetbrains.kotlin.storage.StorageManager;

/**
 * Various junk that cannot be placed into context (yet).
 */
public class TopDownAnalysisParameters implements GlobalContext {

    @NotNull
    public static TopDownAnalysisParameters create(
            @NotNull StorageManager storageManager,
            @NotNull ExceptionTracker exceptionTracker,
            boolean analyzingBootstrapLibrary,
            boolean declaredLocally
    ) {
        return new TopDownAnalysisParameters(storageManager, exceptionTracker, analyzingBootstrapLibrary, declaredLocally);
    }

    @NotNull
    public static TopDownAnalysisParameters createForLocalDeclarations(
            @NotNull StorageManager storageManager,
            @NotNull ExceptionTracker exceptionTracker
    ) {
        return new TopDownAnalysisParameters(storageManager, exceptionTracker, false, true);
    }

    @NotNull private final StorageManager storageManager;
    @NotNull private final ExceptionTracker exceptionTracker;
    private final boolean analyzingBootstrapLibrary;
    private final boolean declaredLocally;

    private TopDownAnalysisParameters(
            @NotNull StorageManager storageManager,
            @NotNull ExceptionTracker exceptionTracker,
            boolean analyzingBootstrapLibrary,
            boolean declaredLocally
    ) {
        this.storageManager = storageManager;
        this.exceptionTracker = exceptionTracker;
        this.analyzingBootstrapLibrary = analyzingBootstrapLibrary;
        this.declaredLocally = declaredLocally;
    }

    @Override
    @NotNull
    public StorageManager getStorageManager() {
        return storageManager;
    }

    @Override
    @NotNull
    public ExceptionTracker getExceptionTracker() {
        return exceptionTracker;
    }

    public boolean isAnalyzingBootstrapLibrary() {
        return analyzingBootstrapLibrary;
    }

    public boolean isDeclaredLocally() {
        return declaredLocally;
    }
}
