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

package org.jetbrains.kotlin.resolve.jvm.checkers

import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.config.JVMConfigurationKeys
import org.jetbrains.kotlin.config.JvmTarget
import org.jetbrains.kotlin.descriptors.CallableMemberDescriptor
import org.jetbrains.kotlin.descriptors.ClassOrPackageFragmentDescriptor
import org.jetbrains.kotlin.resolve.calls.checkers.CallChecker
import org.jetbrains.kotlin.resolve.calls.checkers.CallCheckerContext
import org.jetbrains.kotlin.resolve.calls.model.ResolvedCall
import org.jetbrains.kotlin.resolve.inline.InlineUtil
import org.jetbrains.kotlin.resolve.jvm.diagnostics.ErrorsJvm
import org.jetbrains.kotlin.resolve.jvm.getClassVersion
import org.jetbrains.org.objectweb.asm.Opcodes

object InlineFromBiggerJvmTargetChecker : CallChecker {

    override fun check(resolvedCall: ResolvedCall<*>, reportOn: PsiElement, context: CallCheckerContext) {
        val descriptor = resolvedCall.resultingDescriptor as? CallableMemberDescriptor ?: return

        if (!InlineUtil.isInline(descriptor)) return

        val targetPlatform = context.compilerConfiguration.get(JVMConfigurationKeys.JVM_TARGET) ?: JvmTarget.JVM_1_6

        val classOrPackage = descriptor.containingDeclaration as? ClassOrPackageFragmentDescriptor ?: return
        val generatingVersion = targetPlatform.classVersion
        val descriptorVersion = classOrPackage.getClassVersion(generatingVersion, false)
        if (generatingVersion < descriptorVersion) {
            context.trace.report(ErrorsJvm.INLINING_FROM_BIGGER_JVM_TARGET.on(reportOn, descriptor, descriptorVersion, generatingVersion))
        }
    }

    val JvmTarget.classVersion
        get() =
        when (this) {
            JvmTarget.JVM_1_6 -> Opcodes.V1_6
            JvmTarget.JVM_1_8 -> Opcodes.V1_8
        }
}