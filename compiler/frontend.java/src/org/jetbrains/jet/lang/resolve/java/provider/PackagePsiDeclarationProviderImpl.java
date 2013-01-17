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

import com.google.common.base.Function;
import com.google.common.collect.Collections2;
import com.google.common.collect.Lists;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jet.lang.resolve.java.JetJavaMirrorMarker;
import org.jetbrains.jet.lang.resolve.java.JvmAbi;
import org.jetbrains.jet.lang.resolve.name.Name;

import java.util.Collection;
import java.util.List;

import static org.jetbrains.jet.lang.resolve.java.provider.DeclarationOrigin.JAVA;
import static org.jetbrains.jet.lang.resolve.java.provider.DeclarationOrigin.KOTLIN;

public final class PackagePsiDeclarationProviderImpl extends PsiDeclarationProviderBase implements PackagePsiDeclarationProvider {

    @NotNull
    private final PsiPackage psiPackage;
    @NotNull
    private final GlobalSearchScope searchScope;


    /*package private*/ PackagePsiDeclarationProviderImpl(
            @NotNull PsiPackage psiPackage,
            @NotNull GlobalSearchScope searchScope
    ) {
        this.psiPackage = psiPackage;
        this.searchScope = searchScope;
    }

    @NotNull
    public PsiPackage getPsiPackage() {
        return psiPackage;
    }

    @NotNull
    @Override
    public Collection<Name> getDeclaredClasses() {
        return Collections2.transform(getPsiClasses(), new Function<PsiClass, Name>() {
            @Override
            public Name apply(PsiClass input) {
                return Name.identifier(input.getName());
            }
        });
    }

    @NotNull
    @Override
    public Collection<Name> getDeclaredPackages() {
        List<Name> result = Lists.newArrayList();
        for (PsiPackage psiSubPackage : getPsiPackage().getSubPackages()) {
            String subPackageName = psiSubPackage.getName();
            assert subPackageName != null : "All packages except root package must have names";
            result.add(Name.identifier(subPackageName));
        }
        return result;
    }

    //NOTE: may be better to cache the result
    @NotNull
    public Collection<PsiClass> getPsiClasses() {
        List<PsiClass> result = Lists.newArrayList();
        for (PsiClass psiClass : getPsiPackage().getClasses(searchScope)) {
            if (getDeclarationOrigin() == KOTLIN && JvmAbi.PACKAGE_CLASS.equals(psiClass.getName())) {
                continue;
            }

            if (psiClass instanceof JetJavaMirrorMarker) {
                continue;
            }

            // TODO: Temp hack for collection function descriptors from java
            if (JvmAbi.PACKAGE_CLASS.equals(psiClass.getName())) {
                continue;
            }

            if (!psiClass.hasModifierProperty(PsiModifier.PUBLIC)) {
                continue;
            }

            result.add(psiClass);
        }
        return result;
    }

    @NotNull
    @Override
    protected MembersCache buildMembersCache() {
        return MembersCache.buildMembersByNameCache(new MembersCache(), null, getPsiClasses(), true, getDeclarationOrigin() == KOTLIN);
    }

    @NotNull
    @Override
    public DeclarationOrigin getDeclarationOrigin() {
        return JAVA;
    }
}
