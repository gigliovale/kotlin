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

import org.jetbrains.kotlin.config.LanguageVersionSettings
import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.descriptors.annotations.Annotations
import org.jetbrains.kotlin.incremental.components.LookupLocation
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.platform.PlatformToKotlinClassMap
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.resolve.*
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameSafe
import org.jetbrains.kotlin.resolve.scopes.DescriptorKindFilter
import org.jetbrains.kotlin.resolve.scopes.ImportingScope
import org.jetbrains.kotlin.resolve.scopes.LexicalScope
import org.jetbrains.kotlin.resolve.scopes.SubpackagesImportingScope
import org.jetbrains.kotlin.resolve.source.KotlinSourceElement
import org.jetbrains.kotlin.script.getScriptExternalDependencies
import org.jetbrains.kotlin.storage.StorageManager
import org.jetbrains.kotlin.storage.getValue
import org.jetbrains.kotlin.types.TypeSubstitutor
import org.jetbrains.kotlin.utils.Printer

data class FileScopes(val lexicalScope: LexicalScope, val importingScope: ImportingScope, val importResolver: ImportResolver)

class FileScopeFactory(
        private val topLevelDescriptorProvider: TopLevelDescriptorProvider,
        private val storageManager: StorageManager,
        private val moduleDescriptor: ModuleDescriptor,
        private val qualifiedExpressionResolver: QualifiedExpressionResolver,
        private val bindingTrace: BindingTrace,
        private val platformToKotlinClassMap: PlatformToKotlinClassMap,
        private val defaultImportScopeProvider: DefaultImportScopeProvider,
        private val languageVersionSettings: LanguageVersionSettings
) {
    fun createScopesForFile(file: KtFile, existingImports: ImportingScope? = null): FileScopes {
        val packageView = moduleDescriptor.getPackage(file.packageFqName)
        val packageFragment = topLevelDescriptorProvider.getPackageFragment(file.packageFqName)
        if (packageFragment == null) {
            // TODO J2K and change return type of diagnoseMissingPackageFragment() to Nothing
            (topLevelDescriptorProvider as? LazyClassContext)?.declarationProviderFactory?.diagnoseMissingPackageFragment(file)
            error("Could not find fragment ${file.packageFqName} for file ${file.name}")
        }

        return FilesScopesBuilder(file, existingImports, packageFragment, packageView).result
    }

    private inner class FilesScopesBuilder(
            private val file: KtFile,
            private val existingImports: ImportingScope?,
            private val packageFragment: PackageFragmentDescriptor,
            private val packageView: PackageViewDescriptor
    ) {
        val imports = file.importDirectives
        val aliasImportFqNamesNames = imports.mapNotNull { if (it.aliasName != null) it.importedFqName else null }.toSet()

        val explicitImportResolver = createImportResolver(ExplicitImportsIndexed(imports), bindingTrace)
        val allUnderImportResolver = createImportResolver(AllUnderImportsIndexed(imports), bindingTrace) // TODO: should we count excludedImports here also?

        val lazyImportingScope = object : ImportingScope by ImportingScope.Empty {
            // avoid constructing the scope before we query it
            override val parent: ImportingScope by storageManager.createLazyValue {
                createImportingScope()
            }
        }

        val lexicalScope = LexicalScope.Empty(lazyImportingScope, topLevelDescriptorProvider.getPackageFragment(file.packageFqName)!!)

        val importResolver = object : ImportResolver {
            override fun forceResolveAllImports() {
                explicitImportResolver.forceResolveAllImports()
                allUnderImportResolver.forceResolveAllImports()
            }

            override fun forceResolveImport(import: Import) {
                if (import.isAllUnder) {
                    allUnderImportResolver.forceResolveImport(import)
                }
                else {
                    explicitImportResolver.forceResolveImport(import)
                }
            }
        }

        val result = FileScopes(lexicalScope, lazyImportingScope, importResolver)

        fun createImportResolver(indexedImports: IndexedImports, trace: BindingTrace, excludedImports: List<FqName>? = null) =
                LazyImportResolver(
                        storageManager, qualifiedExpressionResolver, moduleDescriptor, platformToKotlinClassMap, languageVersionSettings,
                        indexedImports, aliasImportFqNamesNames concat excludedImports, trace, packageFragment
                )


        fun createImportingScope(): LazyImportScope {
            val tempTrace = TemporaryBindingTrace.create(bindingTrace, "Transient trace for default imports lazy resolve", false)

            val extraImportsFromScriptProviders = file.originalFile.virtualFile?.let {  vFile ->
                val scriptExternalDependencies = getScriptExternalDependencies(vFile, file.project)
                scriptExternalDependencies?.imports?.map { ImportPath.fromString(it) }
            } ?: emptyList()
            val extraImportsFiltered = extraImportsFromScriptProviders.filter { it.isAllUnder || it.fqName !in aliasImportFqNamesNames }

//            val allImplicitImports = defaultImports concat extraImportsFromScriptProviders
//
//            val defaultImportsFiltered = if (aliasImportFqNamesNames.isEmpty()) { // optimization
//                allImplicitImports
//            }
//            else {
//                allImplicitImports.filter { it.isAllUnder || it.importedFqName !in aliasImportFqNamesNames }
//            }

//            val defaultExplicitImportResolver = createImportResolver(ExplicitImportsIndexed(defaultImportsFiltered), tempTrace)
//            val defaultAllUnderImportResolver = createImportResolver(AllUnderImportsIndexed(defaultImportsFiltered), tempTrace, defaultImportProvider.excludedImports)

//            val explicitImportResolver = createImportResolver(ExplicitImportsIndexed(imports), bindingTrace)
//            val allUnderImportResolver = createImportResolver(AllUnderImportsIndexed(imports), bindingTrace) // TODO: should we count excludedImports here also?

            val extraExplicitImportResolver = createImportResolver(ExplicitImportsIndexed(extraImportsFiltered), tempTrace)
            val extraAllUnderImportResolver = createImportResolver(AllUnderImportsIndexed(extraImportsFiltered), tempTrace)

            val dummyContainerDescriptor = DummyContainerDescriptor(file, packageFragment)

            var scope: ImportingScope

            val debugName = "LazyFileScope for file " + file.name

            scope = createDelegateImportingScope(existingImports, defaultImportScopeProvider.defaultAllUnderImportInvisibleScope, aliasImportFqNamesNames)

//            scope = LazyImportScope(existingImports, defaultAllUnderImportResolver, LazyImportScope.FilteringKind.INVISIBLE_CLASSES,
//                                    "Default all under imports in $debugName (invisible classes only)")

            if (extraImportsFiltered.isNotEmpty()) {
                scope = LazyImportScope(scope, extraAllUnderImportResolver, LazyImportScope.FilteringKind.INVISIBLE_CLASSES,
                                        "Extra all under imports from script providers in $debugName (invisible classes only)")
            }

            scope = LazyImportScope(scope, allUnderImportResolver, LazyImportScope.FilteringKind.INVISIBLE_CLASSES,
                                    "All under imports in $debugName (invisible classes only)")

            scope = currentPackageScope(packageView, aliasImportFqNamesNames, dummyContainerDescriptor, FilteringKind.INVISIBLE_CLASSES, scope)

            scope = createDelegateImportingScope(
                    scope, defaultImportScopeProvider.defaultAllUnderImportVisibleScope, aliasImportFqNamesNames)

//            scope = LazyImportScope(scope, defaultAllUnderImportResolver, LazyImportScope.FilteringKind.VISIBLE_CLASSES,
//                                    "Default all under imports in $debugName (visible classes)")

            if (extraImportsFiltered.isNotEmpty()) {
                scope = LazyImportScope(scope, extraAllUnderImportResolver, LazyImportScope.FilteringKind.VISIBLE_CLASSES,
                                        "Default all under imports in $debugName (visible classes)")
            }

            scope = LazyImportScope(scope, allUnderImportResolver, LazyImportScope.FilteringKind.VISIBLE_CLASSES,
                                    "All under imports in $debugName (visible classes)")

//            scope = LazyImportScope(scope, defaultExplicitImportResolver, LazyImportScope.FilteringKind.ALL,
//                                    "Default explicit imports in $debugName")

            scope = createDelegateImportingScope(
                    scope, defaultImportScopeProvider.defaultExplicitImportScope, aliasImportFqNamesNames)

            if (extraImportsFiltered.isNotEmpty()) {
                scope = LazyImportScope(scope, extraExplicitImportResolver, LazyImportScope.FilteringKind.ALL,
                                        "Default explicit imports in $debugName")
            }

            scope = SubpackagesImportingScope(scope, moduleDescriptor, FqName.ROOT)

            scope = currentPackageScope(packageView, aliasImportFqNamesNames, dummyContainerDescriptor, FilteringKind.VISIBLE_CLASSES, scope)

            return LazyImportScope(scope, explicitImportResolver, LazyImportScope.FilteringKind.ALL, "Explicit imports in $debugName")
        }

        private infix fun <T> Collection<T>.concat(other: Collection<T>?) =
                if (other == null || other.isEmpty()) this else this + other
    }

    private fun createDelegateImportingScope(
            parent: ImportingScope?,
            delegate: ImportingScope,
            filteredFqNames: Set<FqName>): ImportingScope {
        if (filteredFqNames.isEmpty()) {
            return DelegationImportingScope(parent, delegate)
        }

        return FilteredDelegationImportingScope(parent, delegate, filteredFqNames)
    }

    private class DelegationImportingScope(val _parent: ImportingScope?, delegate: ImportingScope) : ImportingScope by delegate {
        override val parent: ImportingScope? get() = _parent
    }

    private class FilteredDelegationImportingScope(
            override val parent: ImportingScope?,
            val delegate: ImportingScope,
            val filteredFqNames: Set<FqName>) : ImportingScope {
        private fun isFiltered(descriptor: DeclarationDescriptor) = descriptor.fqNameSafe in filteredFqNames

        override fun printStructure(p: Printer) = delegate.printStructure(p)

        override fun getContributedClassifier(name: Name, location: LookupLocation): ClassifierDescriptor? {
            val classifier = delegate.getContributedClassifier(name, location) ?: return null
            if (isFiltered(classifier)) return null
            return classifier
        }

        override fun getContributedVariables(name: Name, location: LookupLocation) =
                delegate.getContributedVariables(name, location).filterNot { isFiltered(it) }

        override fun getContributedFunctions(name: Name, location: LookupLocation) =
                delegate.getContributedFunctions(name, location).filterNot { isFiltered(it) }

        override fun getContributedDescriptors(kindFilter: DescriptorKindFilter, nameFilter: (Name) -> Boolean) =
                delegate.getContributedDescriptors(kindFilter, nameFilter).filterNot { isFiltered(it) }

        override fun getContributedPackage(name: Name) = delegate.getContributedPackage(name)
    }

    private enum class FilteringKind {
        VISIBLE_CLASSES, INVISIBLE_CLASSES
    }

    private fun currentPackageScope(
            packageView: PackageViewDescriptor,
            aliasImportNames: Collection<FqName>,
            fromDescriptor: DummyContainerDescriptor,
            filteringKind: FilteringKind,
            parentScope: ImportingScope
    ): ImportingScope {
        val scope = packageView.memberScope
        val packageName = packageView.fqName
        val excludedNames = aliasImportNames.mapNotNull { if (it.parent() == packageName) it.shortName() else null }

        return object : ImportingScope {
            override val parent: ImportingScope? = parentScope

            override fun getContributedPackage(name: Name) = null

            override fun getContributedClassifier(name: Name, location: LookupLocation): ClassifierDescriptor? {
                if (name in excludedNames) return null
                val classifier = scope.getContributedClassifier(name, location) ?: return null
                val visible = Visibilities.isVisibleIgnoringReceiver(classifier as DeclarationDescriptorWithVisibility, fromDescriptor)
                return classifier.takeIf { filteringKind == if (visible) FilteringKind.VISIBLE_CLASSES else FilteringKind.INVISIBLE_CLASSES }
            }

            override fun getContributedVariables(name: Name, location: LookupLocation): Collection<PropertyDescriptor> {
                if (filteringKind == FilteringKind.INVISIBLE_CLASSES) return listOf()
                if (name in excludedNames) return emptyList()
                return scope.getContributedVariables(name, location)
            }

            override fun getContributedFunctions(name: Name, location: LookupLocation): Collection<FunctionDescriptor> {
                if (filteringKind == FilteringKind.INVISIBLE_CLASSES) return listOf()
                if (name in excludedNames) return emptyList()
                return scope.getContributedFunctions(name, location)
            }

            override fun getContributedDescriptors(kindFilter: DescriptorKindFilter, nameFilter: (Name) -> Boolean): Collection<DeclarationDescriptor> {
                // we do not perform any filtering by visibility here because all descriptors from both visible/invisible filter scopes are to be added anyway
                if (filteringKind == FilteringKind.INVISIBLE_CLASSES) return listOf()
                return scope.getContributedDescriptors(
                        kindFilter.withoutKinds(DescriptorKindFilter.PACKAGES_MASK),
                        { name -> name !in excludedNames && nameFilter(name) }
                ).filter { it !is PackageViewDescriptor } // subpackages of the current package not accessible by the short name
            }

            override fun toString() = "Scope for current package (${filteringKind.name})"

            override fun printStructure(p: Printer) {
                p.println(this.toString())
            }
        }
    }

    // we use this dummy implementation of DeclarationDescriptor to check accessibility of symbols from the current package
    private class DummyContainerDescriptor(file: KtFile, private val packageFragment: PackageFragmentDescriptor) : DeclarationDescriptorNonRoot {
        private val sourceElement = KotlinSourceElement(file)

        override fun getContainingDeclaration() = packageFragment

        override fun getSource() = sourceElement

        override fun getOriginal() = this
        override val annotations: Annotations get() = Annotations.EMPTY
        override fun substitute(substitutor: TypeSubstitutor) = this

        override fun <R : Any?, D : Any?> accept(visitor: DeclarationDescriptorVisitor<R, D>?, data: D): R {
            throw UnsupportedOperationException()
        }

        override fun acceptVoid(visitor: DeclarationDescriptorVisitor<Void, Void>?) {
            throw UnsupportedOperationException()
        }

        override fun getName(): Name {
            throw UnsupportedOperationException()
        }
    }
}
