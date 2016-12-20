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

package org.jetbrains.kotlin.resolve.lazy

import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.context.GlobalContext
import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.incremental.KotlinLookupLocation
import org.jetbrains.kotlin.incremental.components.LookupLocation
import org.jetbrains.kotlin.incremental.components.NoLookupLocation
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getElementTextWithContext
import org.jetbrains.kotlin.renderer.DescriptorRenderer
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.BindingTrace
import org.jetbrains.kotlin.resolve.scopes.MemberScope
import org.jetbrains.kotlin.storage.LockBasedLazyResolveStorageManager
import javax.inject.Inject

open class LazyDeclarationResolver
@Deprecated("")
constructor(
        globalContext: GlobalContext,
        delegationTrace: BindingTrace,
        private val topLevelDescriptorProvider: TopLevelDescriptorProvider,
        private val absentDescriptorHandler: AbsentDescriptorHandler
) {
    private val trace: BindingTrace

    protected lateinit var scopeProvider: DeclarationScopeProvider

    // component dependency cycle
    @Inject
    fun setDeclarationScopeProvider(scopeProvider: DeclarationScopeProviderImpl) {
        this.scopeProvider = scopeProvider
    }

    init {
        val lockBasedLazyResolveStorageManager = LockBasedLazyResolveStorageManager(globalContext.storageManager)

        this.trace = lockBasedLazyResolveStorageManager.createSafeTrace(delegationTrace)
    }

    open fun getClassDescriptor(classOrObject: KtClassOrObject, location: LookupLocation) = findClassDescriptor(classOrObject, location)

    open protected fun getClassDescriptorIfAny(classOrObject: KtClassOrObject, location: LookupLocation) =
            findClassDescriptorIfAny(classOrObject, location)

    fun getScriptDescriptor(script: KtScript, location: LookupLocation) = findClassDescriptor(script, location) as ScriptDescriptor

    private fun getScriptDescriptorIfAny(script: KtScript, location: LookupLocation) =
            findClassDescriptorIfAny(script, location) as? ScriptDescriptor

    private fun KtNamedDeclaration.findScopeDescriptor(location: LookupLocation): ClassifierDescriptor? {
        val scope = getMemberScopeDeclaredIn(this, location)

        // Why not use the result here. Because it may be that there is a redeclaration:
        //     class A {} class A { fun foo(): A<completion here>}
        // and if we find the class by name only, we may b-not get the right one.
        // This call is only needed to make sure the classes are written to trace
        return scope?.getContributedClassifier(nameAsSafeName, location)
    }

    private fun findClassDescriptorIfAny(
            classObjectOrScript: KtNamedDeclaration,
            location: LookupLocation
    ): ClassDescriptor? {
        classObjectOrScript.findScopeDescriptor(location)
        return bindingContext.get(BindingContext.DECLARATION_TO_DESCRIPTOR, classObjectOrScript) as? ClassDescriptor
    }

    private fun findClassDescriptor(
            classObjectOrScript: KtNamedDeclaration,
            location: LookupLocation
    ): ClassDescriptor {
        return findClassDescriptorIfAny(classObjectOrScript, location) ?: run {
            val scopeDescriptor = classObjectOrScript.findScopeDescriptor(location)
            throw IllegalArgumentException(
                    String.format("Could not find a classifier for %s.\n" + "Found descriptor: %s (%s).\n",
                                  classObjectOrScript.getElementTextWithContext(),
                                  scopeDescriptor?.let { DescriptorRenderer.DEBUG_TEXT.render(it) } ?: "null",
                                  scopeDescriptor?.containingDeclaration?.javaClass))
        }
    }

    private val bindingContext: BindingContext
        get() = trace.bindingContext

    fun resolveToDescriptor(declaration: KtDeclaration): DeclarationDescriptor {
        return resolveToDescriptor(declaration, track = true) ?: absentDescriptorHandler.diagnoseDescriptorNotFound(declaration)
    }

    fun resolveToDescriptorIfAny(declaration: KtDeclaration) = resolveToDescriptor(declaration, track = true)

    private fun resolveToDescriptor(declaration: KtDeclaration, track: Boolean): DeclarationDescriptor? {
        val result = declaration.accept(object : KtVisitor<DeclarationDescriptor?, Nothing?>() {
            private fun lookupLocationFor(declaration: KtDeclaration, isTopLevel: Boolean): LookupLocation {
                return if (isTopLevel && track) KotlinLookupLocation(declaration) else NoLookupLocation.WHEN_RESOLVE_DECLARATION
            }

            override fun visitClass(klass: KtClass, data: Nothing?): DeclarationDescriptor? {
                return getClassDescriptorIfAny(klass, lookupLocationFor(klass, klass.isTopLevel()))
            }

            override fun visitObjectDeclaration(declaration: KtObjectDeclaration, data: Nothing?): DeclarationDescriptor? {
                return getClassDescriptorIfAny(declaration, lookupLocationFor(declaration, declaration.isTopLevel()))
            }

            override fun visitTypeParameter(parameter: KtTypeParameter, data: Nothing?): DeclarationDescriptor {
                val ownerElement = PsiTreeUtil.getParentOfType(parameter, KtTypeParameterListOwner::class.java) ?: error("Owner not found for type parameter: " + parameter.text)
                val ownerDescriptor = resolveToDescriptor(ownerElement, /*track =*/false)

                val typeParameters: List<TypeParameterDescriptor>
                when (ownerDescriptor) {
                    is CallableDescriptor -> typeParameters = ownerDescriptor.typeParameters
                    is ClassifierDescriptorWithTypeParameters -> typeParameters = ownerDescriptor.typeConstructor.parameters
                    else -> throw IllegalStateException("Unknown owner kind for a type parameter: " + ownerDescriptor)
                }

                val name = parameter.nameAsSafeName
                for (typeParameterDescriptor in typeParameters) {
                    if (typeParameterDescriptor.name == name) {
                        return typeParameterDescriptor
                    }
                }

                throw IllegalStateException("Type parameter $name not found for $ownerDescriptor")
            }

            override fun visitNamedFunction(function: KtNamedFunction, data: Nothing?): DeclarationDescriptor? {
                val location = lookupLocationFor(function, function.isTopLevel)
                val scopeForDeclaration = getMemberScopeDeclaredIn(function, location)
                scopeForDeclaration?.getContributedFunctions(function.nameAsSafeName, location)
                return bindingContext.get(BindingContext.DECLARATION_TO_DESCRIPTOR, function)
            }

            override fun visitParameter(parameter: KtParameter, data: Nothing?): DeclarationDescriptor? {
                val grandFather = parameter.parent.parent
                when (grandFather) {
                    is KtPrimaryConstructor -> {
                        val jetClass = grandFather.getContainingClassOrObject()
                        // This is a primary constructor parameter
                        val classDescriptor = getClassDescriptorIfAny(jetClass, lookupLocationFor(jetClass, false)) ?: return null
                        if (parameter.hasValOrVar()) {
                            classDescriptor.defaultType.memberScope.getContributedVariables(parameter.nameAsSafeName, lookupLocationFor(parameter, false))
                            return bindingContext.get(BindingContext.PRIMARY_CONSTRUCTOR_PARAMETER, parameter)
                        }
                        else {
                            val constructor = classDescriptor.unsubstitutedPrimaryConstructor
                                              ?: error("There are constructor parameters found, so a constructor should also exist")
                            constructor.valueParameters
                            return bindingContext.get(BindingContext.VALUE_PARAMETER, parameter)
                        }
                    }
                    is KtNamedFunction -> {
                        val function = visitNamedFunction(grandFather, data) as FunctionDescriptor
                        function.valueParameters
                        return bindingContext.get(BindingContext.VALUE_PARAMETER, parameter)
                    }
                    is KtSecondaryConstructor -> {
                        val constructorDescriptor = visitSecondaryConstructor(
                                grandFather, data
                        ) as ConstructorDescriptor
                        constructorDescriptor.valueParameters
                        return bindingContext.get(BindingContext.VALUE_PARAMETER, parameter)
                    }
                    else -> //TODO: support parameters in accessors and other places(?)
                        return super.visitParameter(parameter, data)
                }
            }

            override fun visitSecondaryConstructor(constructor: KtSecondaryConstructor, data: Nothing?): DeclarationDescriptor? {
                getClassDescriptorIfAny(
                        constructor.parent.parent as KtClassOrObject, lookupLocationFor(constructor, false)
                )?.constructors
                return bindingContext.get(BindingContext.CONSTRUCTOR, constructor)
            }

            override fun visitPrimaryConstructor(constructor: KtPrimaryConstructor, data: Nothing?): DeclarationDescriptor? {
                getClassDescriptorIfAny(
                        constructor.getContainingClassOrObject(), lookupLocationFor(constructor, false)
                )?.constructors
                return bindingContext.get(BindingContext.CONSTRUCTOR, constructor)
            }

            override fun visitProperty(property: KtProperty, data: Nothing?): DeclarationDescriptor? {
                val location = lookupLocationFor(property, property.isTopLevel)
                val scopeForDeclaration = getMemberScopeDeclaredIn(property, location)
                scopeForDeclaration?.getContributedVariables(property.nameAsSafeName, location)
                return bindingContext.get(BindingContext.DECLARATION_TO_DESCRIPTOR, property)
            }

            override fun visitTypeAlias(typeAlias: KtTypeAlias, data: Nothing?): DeclarationDescriptor? {
                val location = lookupLocationFor(typeAlias, typeAlias.isTopLevel())
                val scopeForDeclaration = getMemberScopeDeclaredIn(typeAlias, location)
                scopeForDeclaration?.getContributedClassifier(typeAlias.nameAsSafeName, location)
                return bindingContext.get(BindingContext.DECLARATION_TO_DESCRIPTOR, typeAlias)
            }

            override fun visitScript(script: KtScript, data: Nothing?): DeclarationDescriptor? {
                return getScriptDescriptorIfAny(script, lookupLocationFor(script, true))
            }

            override fun visitKtElement(element: KtElement, data: Nothing?): DeclarationDescriptor {
                throw IllegalArgumentException("Unsupported declaration type: " + element + " " +
                                               element.getElementTextWithContext())
            }
        }, null)
        return result
    }

    internal fun getMemberScopeDeclaredIn(declaration: KtDeclaration, location: LookupLocation): MemberScope? {
        val parentDeclaration = KtStubbedPsiUtil.getContainingDeclaration(declaration)
        val isTopLevel = parentDeclaration == null
        if (isTopLevel) { // for top level declarations we search directly in package because of possible conflicts with imports
            val ktFile = declaration.containingFile as KtFile
            val fqName = ktFile.packageFqName
            val packageDescriptor = topLevelDescriptorProvider.getPackageFragment(fqName)
            if (packageDescriptor == null) {
                if (topLevelDescriptorProvider is LazyClassContext) {
                    topLevelDescriptorProvider.declarationProviderFactory.diagnoseMissingPackageFragment(ktFile)
                }
                else {
                    throw IllegalStateException("Cannot find package fragment for file " + ktFile.name + " with package " + fqName)
                }
            }
            return packageDescriptor!!.getMemberScope()
        }
        else {
            when (parentDeclaration) {
                is KtClassOrObject -> return getClassDescriptorIfAny(parentDeclaration, location)?.unsubstitutedMemberScope
                is KtScript -> return getScriptDescriptorIfAny(parentDeclaration, location)?.unsubstitutedMemberScope
                else -> throw IllegalStateException("Don't call this method for local declarations: " + declaration + "\n" +
                                                    declaration.getElementTextWithContext())
            }
        }
    }
}
