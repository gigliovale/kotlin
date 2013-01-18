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

package org.jetbrains.jet.plugin.project;

import com.google.common.collect.Lists;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jet.lang.descriptors.ModuleDescriptor;
import org.jetbrains.jet.lang.psi.JetFile;
import org.jetbrains.jet.lang.resolve.ModuleDescriptorProvider;
import org.jetbrains.jet.lang.resolve.ModuleDescriptorProviderFactory;
import org.jetbrains.jet.lang.resolve.name.Name;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public final class ModuleDescriptorProviderForIdeaPlugin implements ModuleDescriptorProvider {

    @NotNull
    public static ModuleDescriptorProvider fromFile(@NotNull JetFile file) {
        VirtualFile virtualFile = file.getOriginalFile().getVirtualFile();
        assert virtualFile != null;
        Module module = ProjectRootManager.getInstance(file.getProject()).getFileIndex().getModuleForFile(virtualFile);
        if (module == null) {
            assert ApplicationManager.getApplication().isUnitTestMode();
            return ModuleDescriptorProviderFactory.createModuleDescriptorProviderForOneModule(file.getProject(), "idea tests");
        }
        return new ModuleDescriptorProviderForIdeaPlugin(file.getProject(), module);
    }

    @NotNull
    private final Map<ModuleDescriptor, GlobalSearchScope> moduleToSearchScope;
    @NotNull
    private final ModuleDescriptor currentModuleDescriptor;


    private ModuleDescriptorProviderForIdeaPlugin(@NotNull Project project, @NotNull Module currentModule) {
        this.moduleToSearchScope = new HashMap<ModuleDescriptor, GlobalSearchScope>();
        ModuleDescriptor currentModuleDescriptor = null;
        for (Module module : ModuleManager.getInstance(project).getModules()) {
            ModuleDescriptor correspondingDescriptor = new ModuleDescriptor(Name.special("<" + module.getName() + ">"));
            if (module == currentModule) {
                currentModuleDescriptor = correspondingDescriptor;
            }
            moduleToSearchScope.put(correspondingDescriptor, module.getModuleScope());
        }
        moduleToSearchScope.put(new ModuleDescriptor(Name.special("<libraries>")),
                                notUnderAnyModuleScope(Lists.newArrayList(moduleToSearchScope.values())));
        assert currentModuleDescriptor != null;
        this.currentModuleDescriptor = currentModuleDescriptor;
    }

    //TODO: this is unoptimal and incorrect way to handle library scopes
    @NotNull
    private static GlobalSearchScope notUnderAnyModuleScope(final Collection<GlobalSearchScope> scopesForAllModules) {
        return new GlobalSearchScope() {
                @Override
                public boolean contains(VirtualFile file) {
                    for (GlobalSearchScope scopeForModule : scopesForAllModules) {
                        if (scopeForModule.contains(file)) {
                            return false;
                        }
                    }
                    return true;
                }

                @Override
                public int compare(VirtualFile file1, VirtualFile file2) {
                    return 0;
                }

                @Override
                public boolean isSearchInModuleContent(@NotNull Module aModule) {
                    return true;
                }

                @Override
                public boolean isSearchInLibraries() {
                    return true;
                }
            };
    }

    @NotNull
    @Override
    public ModuleDescriptor getModule(@NotNull VirtualFile file) {
        for (Map.Entry<ModuleDescriptor, GlobalSearchScope> descriptorAndScope : moduleToSearchScope.entrySet()) {
            GlobalSearchScope scope = descriptorAndScope.getValue();
            ModuleDescriptor descriptor = descriptorAndScope.getKey();
            if (scope.contains(file)) {
                return descriptor;
            }
        }
        throw new IllegalStateException("Could not find module for file: " + file.getCanonicalPath());
    }

    @NotNull
    @Override
    public Collection<ModuleDescriptor> getAllModules() {
        return moduleToSearchScope.keySet();
    }

    @NotNull
    @Override
    public GlobalSearchScope getSearchScopeForModule(@NotNull ModuleDescriptor descriptor) {
        return moduleToSearchScope.get(descriptor);
    }

    @NotNull
    @Override
    public ModuleDescriptor getCurrentModule() {
        return currentModuleDescriptor;
    }
}
