/*
 * Copyright 2010-2016 JetBrains s.r.o.
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

package org.jetbrains.kotlin.resolve.lazy

import org.jetbrains.kotlin.builtins.KotlinBuiltIns
import org.jetbrains.kotlin.config.LanguageVersionSettings
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.descriptors.TypeAliasDescriptor
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.isChildOf
import org.jetbrains.kotlin.name.isSubpackageOf
import org.jetbrains.kotlin.platform.PlatformToKotlinClassMap
import org.jetbrains.kotlin.psi.KtImportsFactory
import org.jetbrains.kotlin.resolve.*
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameSafe
import org.jetbrains.kotlin.resolve.scopes.DescriptorKindFilter
import org.jetbrains.kotlin.storage.StorageManager
import org.jetbrains.kotlin.storage.getValue

class DefaultImportScopeProvider(
        private val storageManager: StorageManager,
        private val moduleDescriptor: ModuleDescriptor,
        private val qualifiedExpressionResolver: QualifiedExpressionResolver,
        private val bindingTrace: BindingTrace,
        private val ktImportsFactory: KtImportsFactory,
        private val platformToKotlinClassMap: PlatformToKotlinClassMap,
        private val defaultImportProvider: DefaultImportProvider,
        private val languageVersionSettings: LanguageVersionSettings
) {
    private val defaultImports by storageManager.createLazyValue {
        ktImportsFactory.createImportDirectives(defaultImportProvider.defaultImports)
    }

    private val tempTrace  by storageManager.createLazyValue {
        TemporaryBindingTrace.create(bindingTrace, "Transient trace for default imports lazy resolve", false)
    }

    private val defaultExplicitImportResolver by storageManager.createLazyValue {
        createImportResolver(ExplicitImportsIndexed(defaultImports), tempTrace)
    }

    private val defaultAllUnderImportResolver by storageManager.createLazyValue {
        createImportResolver(AllUnderImportsIndexed(defaultImports), tempTrace, defaultImportProvider.excludedImports)
    }

    val defaultAllUnderImportInvisibleScope by storageManager.createLazyValue {
        LazyImportScope(null, defaultAllUnderImportResolver, LazyImportScope.FilteringKind.INVISIBLE_CLASSES,
                        "Default all under imports (invisible classes only)")
    }

    val defaultAllUnderImportVisibleScope by storageManager.createLazyValue {
        LazyImportScope(null, defaultAllUnderImportResolver, LazyImportScope.FilteringKind.VISIBLE_CLASSES,
                        "Default all under imports (visible classes)")
    }

    val defaultExplicitImportScope by storageManager.createLazyValue {
        LazyImportScope(null, defaultExplicitImportResolver, LazyImportScope.FilteringKind.ALL,
                        "Default explicit imports")
    }

    private fun createImportResolver(indexedImports: IndexedImports, trace: BindingTrace, excludedImports: List<FqName> = emptyList()) =
            LazyImportResolver(
                    storageManager,
                    qualifiedExpressionResolver,
                    moduleDescriptor,
                    platformToKotlinClassMap,
                    languageVersionSettings,
                    indexedImports,
                    excludedImports,
                    trace,
                    null
            )
}

class DefaultImportProvider(
        storageManager: StorageManager,
        moduleDescriptor: ModuleDescriptor,
        private val targetPlatform: TargetPlatform,
        private val languageVersionSettings: LanguageVersionSettings
) {
    val defaultImports: List<ImportPath>
            by storageManager.createLazyValue { targetPlatform.getDefaultImports(languageVersionSettings) }

    val excludedImports: List<FqName> by storageManager.createLazyValue {
        val packagesWithAliases = listOf(KotlinBuiltIns.BUILT_INS_PACKAGE_FQ_NAME, KotlinBuiltIns.TEXT_PACKAGE_FQ_NAME)
        val builtinTypeAliases = moduleDescriptor.allDependencyModules
                .flatMap { dependencyModule ->
                    packagesWithAliases.map(dependencyModule::getPackage).flatMap {
                        it.memberScope.getContributedDescriptors(DescriptorKindFilter.TYPE_ALIASES).filterIsInstance<TypeAliasDescriptor>()
                    }
                }
                .filter { it.checkSinceKotlinVersionAccessibility(languageVersionSettings) }


        val nonKotlinDefaultImportedPackages =
                defaultImports
                        .filter { it.isAllUnder }
                        .mapNotNull {
                            it.fqName.takeUnless { it.isSubpackageOf(KotlinBuiltIns.BUILT_INS_PACKAGE_FQ_NAME) }
                        }
        val nonKotlinAliasedTypeFqNames =
                builtinTypeAliases
                        .mapNotNull { it.expandedType.constructor.declarationDescriptor?.fqNameSafe }
                        .filter { nonKotlinDefaultImportedPackages.any(it::isChildOf) }

        nonKotlinAliasedTypeFqNames + targetPlatform.excludedImports
    }
}
