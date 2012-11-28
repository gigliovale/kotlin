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

package org.jetbrains.jet.lang.resolve.java.provider;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jet.lang.resolve.name.Name;

import java.util.Collection;

import static org.jetbrains.jet.lang.resolve.java.provider.DeclarationOrigin.KOTLIN;
import static org.jetbrains.jet.lang.resolve.java.provider.PsiDeclarationProviderFactory.createDeclarationProviderForNamespaceWithoutMembers;

public final class KotlinNamespacePsiDeclarationProvider extends ClassPsiDeclarationProviderImpl implements PackagePsiDeclarationProvider {

    @NotNull
    private final PackagePsiDeclarationProviderImpl packagePsiDeclarationProvider;

    public KotlinNamespacePsiDeclarationProvider(
            @NotNull PsiPackage psiPackage,
            @NotNull PsiClass psiClass,
            @NotNull GlobalSearchScope searchScope
    ) {
        super(psiClass, true);
        this.packagePsiDeclarationProvider = createDeclarationProviderForNamespaceWithoutMembers(psiPackage, searchScope);
    }

    @NotNull
    @Override
    public Collection<Name> getDeclaredClasses() {
        return packagePsiDeclarationProvider.getDeclaredClasses();
    }

    @NotNull
    @Override
    public Collection<Name> getDeclaredPackages() {
        return packagePsiDeclarationProvider.getDeclaredPackages();
    }

    @NotNull
    @Override
    protected MembersCache buildMembersCache() {
        MembersCache cacheWithMembers = super.buildMembersCache();
        MembersCache.buildMembersByNameCache(cacheWithMembers, null, packagePsiDeclarationProvider.getPsiPackage(), true,
                                             getDeclarationOrigin() == KOTLIN);
        return cacheWithMembers;
    }

    @NotNull
    @Override
    public DeclarationOrigin getDeclarationOrigin() {
        return KOTLIN;
    }
}
