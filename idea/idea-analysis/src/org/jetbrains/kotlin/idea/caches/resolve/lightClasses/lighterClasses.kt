/*
 * Copyright 2010-2017 JetBrains s.r.o.
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

package org.jetbrains.kotlin.idea.caches.resolve.lightClasses

import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.kotlin.analyzer.common.DefaultAnalyzerFacade
import org.jetbrains.kotlin.asJava.builder.LightClassConstructionContext
import org.jetbrains.kotlin.config.ApiVersion
import org.jetbrains.kotlin.config.LanguageVersion
import org.jetbrains.kotlin.config.LanguageVersionSettingsImpl
import org.jetbrains.kotlin.container.get
import org.jetbrains.kotlin.container.useImpl
import org.jetbrains.kotlin.container.useInstance
import org.jetbrains.kotlin.context.LazyResolveToken
import org.jetbrains.kotlin.context.ModuleContext
import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.descriptors.annotations.Annotations
import org.jetbrains.kotlin.descriptors.impl.CompositePackageFragmentProvider
import org.jetbrains.kotlin.descriptors.impl.ModuleDescriptorImpl
import org.jetbrains.kotlin.frontend.di.configureModule
import org.jetbrains.kotlin.idea.caches.resolve.getResolutionFacade
import org.jetbrains.kotlin.idea.project.IdeaEnvironment
import org.jetbrains.kotlin.incremental.components.LookupTracker
import org.jetbrains.kotlin.incremental.components.NoLookupLocation
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.resolve.*
import org.jetbrains.kotlin.resolve.calls.CallResolver
import org.jetbrains.kotlin.resolve.calls.model.ResolvedCall
import org.jetbrains.kotlin.resolve.calls.model.ResolvedValueArgument
import org.jetbrains.kotlin.resolve.calls.results.OverloadResolutionResults
import org.jetbrains.kotlin.resolve.calls.results.ResolutionStatus
import org.jetbrains.kotlin.resolve.constants.ConstantValue
import org.jetbrains.kotlin.resolve.constants.StringValue
import org.jetbrains.kotlin.resolve.constants.evaluate.ConstantExpressionEvaluator
import org.jetbrains.kotlin.resolve.descriptorUtil.module
import org.jetbrains.kotlin.resolve.jvm.platform.JvmPlatform
import org.jetbrains.kotlin.resolve.lazy.FileScopeProviderImpl
import org.jetbrains.kotlin.resolve.lazy.ForceResolveUtil
import org.jetbrains.kotlin.resolve.lazy.ResolveSession
import org.jetbrains.kotlin.resolve.lazy.declarations.FileBasedDeclarationProviderFactory
import org.jetbrains.kotlin.resolve.scopes.LexicalScope
import org.jetbrains.kotlin.serialization.deserialization.KotlinMetadataFinder
import org.jetbrains.kotlin.serialization.deserialization.MetadataPackageFragmentProvider
import org.jetbrains.kotlin.storage.LockBasedStorageManager
import org.jetbrains.kotlin.storage.StorageManager
import org.jetbrains.kotlin.types.ErrorUtils
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.WrappedTypeFactory

fun contextForBuildingLighterClasses(classOrObject: KtClassOrObject): LightClassConstructionContext {
    val resolveSession = setupAdHocResolve(classOrObject.project, classOrObject.getResolutionFacade().moduleDescriptor, listOf(classOrObject.containingKtFile))

    val descriptor = resolveSession.resolveToDescriptor(classOrObject)
    ForceResolveUtil.forceResolveAllContents(descriptor)

    return LightClassConstructionContext(resolveSession.bindingContext, resolveSession.moduleDescriptor)
}

fun contextForBuildingLighterClasses(files: List<KtFile>): LightClassConstructionContext {
    val representativeFile = files.first()
    val resolveSession = setupAdHocResolve(representativeFile.project, representativeFile.getResolutionFacade().moduleDescriptor, files)

    return LightClassConstructionContext(resolveSession.bindingContext, resolveSession.moduleDescriptor)
}

private fun setupAdHocResolve(project: Project, realWorldModule: ModuleDescriptor, files: List<KtFile>): ResolveSession {
    val trace = BindingTraceContext()
    val sm = LockBasedStorageManager.NO_LOCKS
    // TODO_R: test internals
    val moduleDescriptor = ModuleDescriptorImpl(realWorldModule.name, sm, realWorldModule.builtIns)
    val jvmFieldClass = realWorldModule.getPackage(FqName("kotlin.jvm")).memberScope
            .getContributedClassifier(Name.identifier("JvmField"), NoLookupLocation.FROM_IDE)

    if (jvmFieldClass != null) {
        moduleDescriptor.setDependencies(moduleDescriptor, jvmFieldClass.module as ModuleDescriptorImpl, moduleDescriptor.builtIns.builtInsModule)
    }
    else {
        moduleDescriptor.setDependencies(moduleDescriptor, moduleDescriptor.builtIns.builtInsModule)
    }

    val container = createContainer("LightClassStub", DefaultAnalyzerFacade.targetPlatform) {
        configureModule(ModuleContext(moduleDescriptor, project), JvmPlatform, trace)

        useInstance(GlobalSearchScope.EMPTY_SCOPE)
        useInstance(LookupTracker.DO_NOTHING)
        useImpl<ResolveSession>()
        useImpl<LazyTopDownAnalyzer>()
        useImpl<FileScopeProviderImpl>()
        useImpl<AdHocAnnotationResolver>()

        useInstance(LanguageVersion.LATEST)
        useImpl<CompilerDeserializationConfiguration>()
        useImpl<MetadataPackageFragmentProvider>()
        val languageVersionSettings = LanguageVersionSettingsImpl(
                LanguageVersion.LATEST, ApiVersion.LATEST
        )
        useInstance(object : WrappedTypeFactory(sm) {
            override fun createLazyWrappedType(computation: () -> KotlinType): KotlinType = ErrorUtils.createErrorType("^_^")

            override fun createDeferredType(trace: BindingTrace, computation: () -> KotlinType) = ErrorUtils.createErrorType("^_^")

            override fun createRecursionIntolerantDeferredType(trace: BindingTrace, computation: () -> KotlinType) = ErrorUtils.createErrorType("^_^")
        })
        useInstance(languageVersionSettings)
        useInstance(FileBasedDeclarationProviderFactory(sm, files))
        useInstance(PackagePartProvider.Empty)
        useInstance(KotlinMetadataFinder.Empty)

        IdeaEnvironment.configure(this)
        useImpl<LazyResolveToken>()
    }


    val resolveSession = container.get<ResolveSession>()
    moduleDescriptor.initialize(CompositePackageFragmentProvider(listOf(resolveSession.packageFragmentProvider)))
    return resolveSession
}

private val annotationsThatAffectCodegen = listOf("JvmField", "JvmOverloads", "JvmName", "JvmStatic").map { FqName("kotlin.jvm").child(Name.identifier(it)) }

class AdHocAnnotationResolver(
        private val moduleDescriptor: ModuleDescriptor,
        callResolver: CallResolver,
        constantExpressionEvaluator: ConstantExpressionEvaluator,
        storageManager: StorageManager
) : AnnotationResolverImpl(callResolver, constantExpressionEvaluator, storageManager) {

    override fun resolveAnnotationEntries(scope: LexicalScope, annotationEntries: List<KtAnnotationEntry>, trace: BindingTrace, shouldResolveArguments: Boolean): Annotations {
        return super.resolveAnnotationEntries(scope, annotationEntries, trace, shouldResolveArguments)
    }

    override fun resolveAnnotationType(scope: LexicalScope, entryElement: KtAnnotationEntry, trace: BindingTrace): KotlinType {
        return annotationClassByEntry(entryElement)?.defaultType ?: super.resolveAnnotationType(scope, entryElement, trace)
    }

    private fun annotationClassByEntry(entryElement: KtAnnotationEntry): ClassDescriptor? {
        val annotationTypeReferencePsi = (entryElement.typeReference?.typeElement as? KtUserType)?.referenceExpression ?: return null
        val referencedName = annotationTypeReferencePsi.getReferencedName()
        for (annotationFqName in annotationsThatAffectCodegen) {
            if (referencedName == annotationFqName.shortName().asString()) {
                moduleDescriptor.getPackage(annotationFqName.parent()).memberScope
                        .getContributedClassifier(annotationFqName.shortName(), NoLookupLocation.FROM_IDE)?.let { return it as? ClassDescriptor }

            }
        }
        return null
    }

    override fun resolveAnnotationCall(annotationEntry: KtAnnotationEntry, scope: LexicalScope, trace: BindingTrace): OverloadResolutionResults<FunctionDescriptor> {
        val annotationConstructor = annotationClassByEntry(annotationEntry)?.constructors?.singleOrNull() ?: return super.resolveAnnotationCall(annotationEntry, scope, trace)
        val valueArgumentText = ((annotationEntry.valueArguments.singleOrNull()?.getArgumentExpression() as? KtStringTemplateExpression)?.entries?.singleOrNull() as? KtLiteralStringTemplateEntry)?.text ?: return super.resolveAnnotationCall(annotationEntry, scope, trace)
        val fakeResolvedCall = object : ResolvedCall<FunctionDescriptor> {
            override fun getStatus() = ResolutionStatus.SUCCESS
            override fun getCall() = error("TODO")

            override fun getCandidateDescriptor() = annotationConstructor
            override fun getResultingDescriptor() = annotationConstructor

            override fun getExtensionReceiver() = error("TODO")
            override fun getDispatchReceiver() = error("TODO")
            override fun getExplicitReceiverKind() = error("TODO")
            override fun getValueArguments() =
                    annotationConstructor.valueParameters.singleOrNull()?.let { mapOf(it to FakeResolvedValueArgument(valueArgumentText)) }.orEmpty()
            override fun getValueArgumentsByIndex() = error("TODO")
            override fun getArgumentMapping(valueArgument: ValueArgument) = error("TODO")
            override fun getTypeArguments() = error("TODO")
            override fun getDataFlowInfoForArguments() = error("TODO")
            override fun getSmartCastDispatchReceiverType() = error("TODO")
        }

        return object : OverloadResolutionResults<FunctionDescriptor> {
            override fun isSingleResult() = true
            override fun getResultingCall(): ResolvedCall<FunctionDescriptor> = fakeResolvedCall
            override fun getResultingDescriptor() = annotationConstructor
            override fun getAllCandidates() = error("TODO")
            override fun getResultingCalls() = error("TODO")
            override fun getResultCode() = error("TODO")
            override fun isSuccess() = error("TODO")
            override fun isNothing() = error("TODO")
            override fun isAmbiguity() = error("TODO")
            override fun isIncomplete() = error("TODO")
        }
    }

    override fun getAnnotationArgumentValue(trace: BindingTrace, valueParameter: ValueParameterDescriptor, resolvedArgument: ResolvedValueArgument): ConstantValue<*>? {
        if (resolvedArgument is FakeResolvedValueArgument) return StringValue(resolvedArgument.argumentText, moduleDescriptor.builtIns)

        return super.getAnnotationArgumentValue(trace, valueParameter, resolvedArgument)
    }

    private class FakeResolvedValueArgument(val argumentText: String): ResolvedValueArgument {
        override fun getArguments() = error("TODO")
    }
}