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
    private ModuleDescriptorProviderFactory() {
    }

    @NotNull
    public static final ModuleDescriptor JAVA_MODULE = new ModuleDescriptor(Name.special("<java_root>"));

    // puts every java file read under dummy JAVA_MODULE
    @NotNull
    public static ModuleDescriptorProvider createDefaultModuleDescriptorProvider(@NotNull final Project project) {

        return new ModuleDescriptorProvider() {
            @NotNull
            private final GlobalSearchScope javaSearchScope = new DelegatingGlobalSearchScope(GlobalSearchScope.allScope(project)) {
                @Override
                public boolean contains(VirtualFile file) {
                    return myBaseScope.contains(file) && file.getFileType() != JetFileType.INSTANCE;
                }
            };

            @NotNull
            @Override
            public ModuleDescriptor getModule(@NotNull VirtualFile file) {
                return JAVA_MODULE;
            }

            @NotNull
            @Override
            public Collection<ModuleDescriptor> getAllModules() {
                return Collections.singletonList(JAVA_MODULE);
            }

            @NotNull
            @Override
            public GlobalSearchScope getSearchScopeForModule(@NotNull ModuleDescriptor descriptor) {
                if (descriptor == JAVA_MODULE) {
                    return javaSearchScope;
                }
                throw new IllegalStateException();
            }
        };
    }

    // puts everything under specified module
    @NotNull
    public static ModuleDescriptorProvider createModuleDescriptorProviderForOneModule(
            @NotNull final Project project,
            @NotNull final ModuleDescriptor kotlinModule
    ) {
        return new ModuleDescriptorProvider() {
            @NotNull
            @Override
            public ModuleDescriptor getModule(@NotNull VirtualFile file) {
                return kotlinModule;
            }

            @NotNull
            @Override
            public Collection<ModuleDescriptor> getAllModules() {
                return Collections.singletonList(kotlinModule);
            }

            @NotNull
            @Override
            public GlobalSearchScope getSearchScopeForModule(@NotNull ModuleDescriptor descriptor) {
                assert kotlinModule == descriptor;
                return GlobalSearchScope.allScope(project);
            }
        };
    }
}
