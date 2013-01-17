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

package org.jetbrains.jet.lang.resolve;

import com.google.common.collect.Lists;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.DelegatingGlobalSearchScope;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jet.lang.descriptors.ModuleDescriptor;
import org.jetbrains.jet.lang.resolve.name.Name;
import org.jetbrains.jet.plugin.JetFileType;

import java.util.Collection;
import java.util.Collections;

public final class ModuleDescriptorProviderFactory {

    public static final String JAVA_MODULE_NAME = "<java_root>";

    private ModuleDescriptorProviderFactory() {
    }

    // puts every java file read under dummy javaModule and every kotlin file under kotlinModule
    @NotNull
    public static ModuleDescriptorProvider createDefaultModuleDescriptorProvider(
            @NotNull final Project project, @NotNull String moduleName
    ) {
        return createDefaultModuleDescriptorProvider(project, new ModuleDescriptor(Name.special("<" + moduleName + ">")));
    }

    // puts every java file read under dummy javaModule and every kotlin file under kotlinModule
    @NotNull
    public static ModuleDescriptorProvider createDefaultModuleDescriptorProvider(
            @NotNull final Project project,
            @NotNull final ModuleDescriptor kotlinModule
    ) {
        return new ModuleDescriptorProvider() {
            @NotNull
            public final ModuleDescriptor javaModule = new ModuleDescriptor(Name.special(JAVA_MODULE_NAME));
            @NotNull
            private final GlobalSearchScope javaSearchScope = new DelegatingGlobalSearchScope(GlobalSearchScope.allScope(project)) {
                @Override
                public boolean contains(VirtualFile file) {
                    return myBaseScope.contains(file) && file.getFileType() != JetFileType.INSTANCE;
                }
            };

            private final GlobalSearchScope kotlinSearchScope = new DelegatingGlobalSearchScope(GlobalSearchScope.allScope(project)) {
                @Override
                public boolean contains(VirtualFile file) {
                    return myBaseScope.contains(file) && file.getFileType() == JetFileType.INSTANCE;
                }
            };

            @NotNull
            @Override
            public ModuleDescriptor getModule(@NotNull VirtualFile file) {
                return javaSearchScope.contains(file) ? javaModule : kotlinModule;
            }

            @NotNull
            @Override
            public Collection<ModuleDescriptor> getAllModules() {
                return Lists.newArrayList(kotlinModule, javaModule);
            }

            @NotNull
            @Override
            public GlobalSearchScope getSearchScopeForModule(@NotNull ModuleDescriptor descriptor) {
                if (descriptor == javaModule) {
                    return javaSearchScope;
                }
                return kotlinSearchScope;
            }

            @NotNull
            @Override
            public ModuleDescriptor getCurrentModule() {
                return kotlinModule;
            }
        };
    }

    // puts everything under specified module
    @NotNull
    public static ModuleDescriptorProvider createModuleDescriptorProviderForOneModule(
            @NotNull Project project, @NotNull String moduleName
    ) {
        return createModuleDescriptorProviderForOneModule(project, new ModuleDescriptor(Name.special("<" + moduleName + ">")));
    }

    // puts everything under specified module
    @NotNull
    public static ModuleDescriptorProvider createModuleDescriptorProviderForOneModule(
            @NotNull final Project project,
            @NotNull final ModuleDescriptor module
    ) {
        return new ModuleDescriptorProvider() {
            @NotNull
            @Override
            public ModuleDescriptor getModule(@NotNull VirtualFile file) {
                return module;
            }

            @NotNull
            @Override
            public Collection<ModuleDescriptor> getAllModules() {
                return Collections.singletonList(module);
            }

            @NotNull
            @Override
            public GlobalSearchScope getSearchScopeForModule(@NotNull ModuleDescriptor descriptor) {
                assert module == descriptor;
                return GlobalSearchScope.allScope(project);
            }

            @NotNull
            @Override
            public ModuleDescriptor getCurrentModule() {
                return module;
            }
        };
    }
}
