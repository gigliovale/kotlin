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

package org.jetbrains.jet.lang.resolve.java.scope;

import com.google.common.collect.Lists;
import com.intellij.openapi.progress.ProgressIndicatorProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jet.lang.descriptors.ClassDescriptor;
import org.jetbrains.jet.lang.descriptors.ClassifierDescriptor;
import org.jetbrains.jet.lang.descriptors.DeclarationDescriptor;
import org.jetbrains.jet.lang.descriptors.NamespaceDescriptor;
import org.jetbrains.jet.lang.resolve.DescriptorUtils;
import org.jetbrains.jet.lang.resolve.java.DescriptorSearchRule;
import org.jetbrains.jet.lang.resolve.java.JavaDescriptorResolver;
import org.jetbrains.jet.lang.resolve.java.provider.PackagePsiDeclarationProvider;
import org.jetbrains.jet.lang.resolve.name.FqName;
import org.jetbrains.jet.lang.resolve.name.Name;

import java.util.Collection;
import java.util.List;

import static org.jetbrains.jet.lang.resolve.java.DescriptorSearchRule.IGNORE_IF_FOUND_IN_KOTLIN;

public abstract class JavaPackageScope extends JavaBaseScope {

    @NotNull
    private final PackagePsiDeclarationProvider declarationProvider;
    @NotNull
    private final FqName packageFQN;

    protected JavaPackageScope(
            @NotNull NamespaceDescriptor descriptor,
            @NotNull PackagePsiDeclarationProvider declarationProvider,
            @NotNull FqName packageFQN,
            @NotNull JavaDescriptorResolver descriptorResolver
    ) {
        super(descriptor, declarationProvider, descriptorResolver);
        this.declarationProvider = declarationProvider;
        this.packageFQN = packageFQN;
    }

    @Override
    public ClassifierDescriptor getClassifier(@NotNull Name name) {
        ClassDescriptor classDescriptor =
                getResolver().resolveClass(packageFQN.child(name), DescriptorSearchRule.IGNORE_IF_FOUND_IN_KOTLIN);
        if (classDescriptor == null || classDescriptor.getKind().isObject()) {
            return null;
        }
        return classDescriptor;
    }

    @Override
    public ClassDescriptor getObjectDescriptor(@NotNull Name name) {
        ClassDescriptor classDescriptor =
                getResolver().resolveClass(packageFQN.child(name), DescriptorSearchRule.IGNORE_IF_FOUND_IN_KOTLIN);
        if (classDescriptor != null && classDescriptor.getKind().isObject()) {
            return classDescriptor;
        }
        return null;
    }

    @Override
    public NamespaceDescriptor getNamespace(@NotNull Name name) {
        return getResolver().resolveNamespace(packageFQN.child(name), DescriptorSearchRule.INCLUDE_KOTLIN);
    }

    @NotNull
    @Override
    protected Collection<DeclarationDescriptor> computeAllDescriptors() {
        List<DeclarationDescriptor> result = Lists.newArrayList();
        result.addAll(super.computeAllDescriptors());
        FqName packageFqName = DescriptorUtils.getFQName(descriptor).toSafe();
        result.addAll(computeClasses(packageFqName));
        result.addAll(computeNamespaces(packageFqName));
        return result;
    }

    @NotNull
    private Collection<NamespaceDescriptor> computeNamespaces(@NotNull FqName packageFqName) {
        List<NamespaceDescriptor> result = Lists.newArrayList();
        for (Name packageName : declarationProvider.getDeclaredPackages()) {
            NamespaceDescriptor namespaceDescriptor = getResolver().resolveNamespace(packageFqName.child(packageName),
                                                                                     IGNORE_IF_FOUND_IN_KOTLIN);
            if (namespaceDescriptor != null) {
                result.add(namespaceDescriptor);
            }
        }
        return result;
    }

    @NotNull
    private Collection<ClassDescriptor> computeClasses(@NotNull FqName packageFqName) {
        List<ClassDescriptor> result = Lists.newArrayList();
        for (Name className : declarationProvider.getDeclaredClasses()) {
            ProgressIndicatorProvider.checkCanceled();
            ClassDescriptor classDescriptor = getResolver().resolveClass(packageFqName.child(className), IGNORE_IF_FOUND_IN_KOTLIN);
            if (classDescriptor != null) {
                result.add(classDescriptor);
            }
        }
        return result;
    }
}
