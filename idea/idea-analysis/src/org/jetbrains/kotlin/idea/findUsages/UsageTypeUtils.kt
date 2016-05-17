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

package org.jetbrains.kotlin.idea.findUsages

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiPackage
import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.findUsages.UsageTypeEnum.*
import org.jetbrains.kotlin.idea.references.*
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getNonStrictParentOfType
import org.jetbrains.kotlin.psi.psiUtil.getParentOfTypeAndBranch
import org.jetbrains.kotlin.psi.psiUtil.isAncestor
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.DescriptorUtils

object UsageTypeUtils {
    fun getUsageType(element: PsiElement?): UsageTypeEnum? {
        when (element) {
            is KtForExpression -> return IMPLICIT_ITERATION
            is KtDestructuringDeclaration -> return READ
            is KtPropertyDelegate -> return PROPERTY_DELEGATION
            is KtStringTemplateExpression -> return USAGE_IN_STRING_LITERAL
        }

        val refElement = element?.getNonStrictParentOfType<KtReferenceElement>() ?: return null

        val context = refElement.analyze()

        fun getCommonUsageType(): UsageTypeEnum? {
            return when {
                refElement.getNonStrictParentOfType<KtImportDirective>() != null ->
                    CLASS_IMPORT
                refElement.getParentOfTypeAndBranch<KtCallableReferenceExpression>(){ callableReference } != null ->
                    CALLABLE_REFERENCE
                else -> null
            }
        }

        fun getClassUsageType(): UsageTypeEnum? {
            if (refElement.getNonStrictParentOfType<KtTypeProjection>() != null) return TYPE_PARAMETER

            val property = refElement.getNonStrictParentOfType<KtProperty>()
            if (property != null) {
                when {
                    property.typeReference.isAncestor(refElement) ->
                        return if (property.isLocal) CLASS_LOCAL_VAR_DECLARATION else NON_LOCAL_PROPERTY_TYPE

                    property.receiverTypeReference.isAncestor(refElement) ->
                        return EXTENSION_RECEIVER_TYPE
                }
            }

            val function = refElement.getNonStrictParentOfType<KtFunction>()
            if (function != null) {
                when {
                    function.typeReference.isAncestor(refElement) ->
                        return FUNCTION_RETURN_TYPE
                    function.receiverTypeReference.isAncestor(refElement) ->
                        return EXTENSION_RECEIVER_TYPE
                }
            }

            return when {
                refElement.getParentOfTypeAndBranch<KtTypeParameter>(){ extendsBound } != null
                || refElement.getParentOfTypeAndBranch<KtTypeConstraint>(){ boundTypeReference } != null ->
                    TYPE_CONSTRAINT

                refElement is KtSuperTypeListEntry
                || refElement.getParentOfTypeAndBranch<KtSuperTypeListEntry>(){ typeReference } != null ->
                    SUPER_TYPE

                refElement.getParentOfTypeAndBranch<KtParameter>(){ typeReference } != null ->
                    VALUE_PARAMETER_TYPE

                refElement.getParentOfTypeAndBranch<KtIsExpression>(){ typeReference } != null
                || refElement.getParentOfTypeAndBranch<KtWhenConditionIsPattern>(){ typeReference } != null ->
                    IS

                with(refElement.getParentOfTypeAndBranch<KtBinaryExpressionWithTypeRHS>(){ right }) {
                    val opType = this?.operationReference?.getReferencedNameElementType()
                    opType == KtTokens.AS_KEYWORD || opType == KtTokens.AS_SAFE
                } ->
                    CLASS_CAST_TO

                with(refElement.getNonStrictParentOfType<KtDotQualifiedExpression>()) {
                    if (this == null) false
                    else if (receiverExpression == refElement) true
                    else
                        selectorExpression == refElement
                        && getParentOfTypeAndBranch<KtDotQualifiedExpression>(strict = true) { receiverExpression } != null
                } ->
                    CLASS_OBJECT_ACCESS

                refElement.getParentOfTypeAndBranch<KtSuperExpression>(){ superTypeQualifier } != null ->
                    SUPER_TYPE_QUALIFIER

                else -> null
            }
        }

        fun getVariableUsageType(): UsageTypeEnum? {
            if (refElement.getParentOfTypeAndBranch<KtDelegatedSuperTypeEntry>(){ delegateExpression } != null) {
                return DELEGATE
            }

            if (refElement.parent is KtValueArgumentName) return NAMED_ARGUMENT

            val dotQualifiedExpression = refElement.getNonStrictParentOfType<KtDotQualifiedExpression>()

            if (dotQualifiedExpression != null) {
                val parent = dotQualifiedExpression.parent
                when {
                    dotQualifiedExpression.receiverExpression.isAncestor(refElement) ->
                        return RECEIVER

                    parent is KtDotQualifiedExpression && parent.receiverExpression.isAncestor(refElement) ->
                        return RECEIVER
                }
            }

            if (refElement !is KtReferenceExpression) return null
            return when (refElement.readWriteAccess(useResolveForReadWrite = true)) {
                ReferenceAccess.READ -> READ
                ReferenceAccess.WRITE, ReferenceAccess.READ_WRITE -> WRITE
            }
        }

        fun getFunctionUsageType(descriptor: FunctionDescriptor): UsageTypeEnum? {
            when (refElement.mainReference) {
                is KtArrayAccessReference -> {
                    return when {
                        context[BindingContext.INDEXED_LVALUE_GET, refElement as? KtReferenceExpression] != null -> IMPLICIT_GET
                        context[BindingContext.INDEXED_LVALUE_SET, refElement as? KtReferenceExpression] != null -> IMPLICIT_SET
                        else -> null
                    }
                }
                is KtInvokeFunctionReference -> return IMPLICIT_INVOKE
            }

            return when {
                refElement.getParentOfTypeAndBranch<KtSuperTypeListEntry>(){ typeReference } != null ->
                    SUPER_TYPE

                descriptor is ConstructorDescriptor
                && refElement.getParentOfTypeAndBranch<KtAnnotationEntry>(){ typeReference } != null ->
                    ANNOTATION

                with(refElement.getParentOfTypeAndBranch<KtCallExpression>(){ calleeExpression }) {
                    this?.calleeExpression is KtSimpleNameExpression
                } ->
                    if (descriptor is ConstructorDescriptor) CLASS_NEW_OPERATOR else FUNCTION_CALL

                refElement.getParentOfTypeAndBranch<KtBinaryExpression>(){ operationReference } != null ||
                refElement.getParentOfTypeAndBranch<KtUnaryExpression>(){ operationReference } != null ||
                refElement.getParentOfTypeAndBranch<KtWhenConditionInRange>(){ operationReference } != null ->
                    FUNCTION_CALL

                else -> null
            }
        }

        fun getPackageUsageType(): UsageTypeEnum? {
            return when {
                refElement.getNonStrictParentOfType<KtPackageDirective>() != null -> PACKAGE_DIRECTIVE
                refElement.getNonStrictParentOfType<KtQualifiedExpression>() != null -> PACKAGE_MEMBER_ACCESS
                else -> getClassUsageType()
            }
        }

        val usageType = getCommonUsageType()
        if (usageType != null) return usageType

        val descriptor = context[BindingContext.REFERENCE_TARGET, refElement]

        return when (descriptor) {
            is ClassifierDescriptor -> when {
            // Treat object accesses as variables to simulate the old behaviour (when variables were created for objects)
                DescriptorUtils.isNonCompanionObject(descriptor) || DescriptorUtils.isEnumEntry(descriptor) -> getVariableUsageType()
                DescriptorUtils.isCompanionObject(descriptor) -> COMPANION_OBJECT_ACCESS
                else -> getClassUsageType()
            }
            is PackageViewDescriptor -> {
                if (refElement.mainReference.resolve() is PsiPackage) getPackageUsageType() else getClassUsageType()
            }
            is VariableDescriptor -> getVariableUsageType()
            is FunctionDescriptor -> getFunctionUsageType(descriptor)
            else -> null
        }
    }
}

enum class UsageTypeEnum {
    TYPE_CONSTRAINT,
    VALUE_PARAMETER_TYPE,
    NON_LOCAL_PROPERTY_TYPE,
    FUNCTION_RETURN_TYPE,
    SUPER_TYPE,
    IS,
    CLASS_OBJECT_ACCESS,
    COMPANION_OBJECT_ACCESS,
    EXTENSION_RECEIVER_TYPE,
    SUPER_TYPE_QUALIFIER,

    FUNCTION_CALL,
    IMPLICIT_GET,
    IMPLICIT_SET,
    IMPLICIT_INVOKE,
    IMPLICIT_ITERATION,
    PROPERTY_DELEGATION,

    RECEIVER,
    DELEGATE,

    PACKAGE_DIRECTIVE,
    PACKAGE_MEMBER_ACCESS,

    CALLABLE_REFERENCE,

    READ,
    WRITE,
    CLASS_IMPORT,
    CLASS_LOCAL_VAR_DECLARATION,
    TYPE_PARAMETER,
    CLASS_CAST_TO,
    ANNOTATION,
    CLASS_NEW_OPERATOR,
    NAMED_ARGUMENT,

    USAGE_IN_STRING_LITERAL
}
