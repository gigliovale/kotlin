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

package org.jetbrains.jet.plugin.stubindex;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.DelegatingGlobalSearchScope;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jet.plugin.JetFileType;
import org.jetbrains.jet.plugin.JetPluginUtil;

public class JetSourceFilterScope extends DelegatingGlobalSearchScope {

    @NotNull private final Mode mode;

    private enum Mode {
        ONLY_MODULE_CONTENT_SOURCES(false, false),
        MODULE_CONTEXT_SOURCES_AND_JS_LIBS(false, true),
        ALL_KOTLIN_SOURCES(true, true);

        Mode(boolean librarySources, boolean libraryClasses) {
            includeLibrarySources = librarySources;
            includeSourcesUnderLibraryClasses = libraryClasses;
        }

        private final boolean includeSourcesUnderLibraryClasses;
        private final boolean includeLibrarySources;
    }

    //TODO: better utilities naming
    @NotNull
    public static GlobalSearchScope kotlinSourcesAndLibraries(@NotNull GlobalSearchScope delegate, @NotNull Project project) {
        return new JetSourceFilterScope(delegate, Mode.ALL_KOTLIN_SOURCES, project);
    }

    @NotNull
    public static GlobalSearchScope kotlinSources(@NotNull GlobalSearchScope delegate, @NotNull Project project) {
        return new JetSourceFilterScope(delegate, Mode.ONLY_MODULE_CONTENT_SOURCES, project);
    }

    @NotNull
    public static GlobalSearchScope kotlinSourcesAndJsLibraries(@NotNull GlobalSearchScope delegate, @NotNull Project project) {
        return new JetSourceFilterScope(delegate, Mode.MODULE_CONTEXT_SOURCES_AND_JS_LIBS, project);
    }

    private final ProjectFileIndex index;
    private final Project project;

    private JetSourceFilterScope(@NotNull GlobalSearchScope delegate, @NotNull Mode mode, @NotNull Project project) {
        super(delegate);
        this.index = ProjectRootManager.getInstance(project).getFileIndex();
        this.project = project;
        this.mode = mode;
    }

    @Override
    public boolean contains(@NotNull VirtualFile file) {
        if (!super.contains(file)) {
            return false;
        }

        if (!file.getFileType().equals(JetFileType.INSTANCE)) return false;

        if (JetPluginUtil.isKtFileInGradleProjectInWrongFolder(file, project)) {
            return false;
        }

        return index.isInSourceContent(file)
               || index.isInLibraryClasses(file) && mode.includeSourcesUnderLibraryClasses
               || index.isInLibrarySource(file) && mode.includeLibrarySources;
    }
}
