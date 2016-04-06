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

package org.jetbrains.kotlin.idea.debugger.stepping;

import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.engine.SuspendContextImpl;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.impl.DebuggerUtilsEx;
import com.intellij.debugger.jdi.StackFrameProxyImpl;
import com.intellij.debugger.jdi.ThreadReferenceProxyImpl;
import com.intellij.debugger.settings.DebuggerSettings;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.psi.PsiElement;
import com.intellij.ui.classFilter.ClassFilter;
import com.intellij.ui.classFilter.DebuggerClassFilterProvider;
import com.sun.jdi.Location;
import com.sun.jdi.ObjectCollectedException;
import com.sun.jdi.ReferenceType;
import com.sun.jdi.ThreadReference;
import com.sun.jdi.request.EventRequest;
import com.sun.jdi.request.EventRequestManager;
import com.sun.jdi.request.StepRequest;
import kotlin.ranges.IntRange;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.kotlin.psi.KtFile;
import org.jetbrains.kotlin.psi.KtFunction;
import org.jetbrains.kotlin.psi.KtFunctionLiteral;
import org.jetbrains.kotlin.psi.KtNamedFunction;

import java.util.ArrayList;
import java.util.List;

public class DebuggerSteppingHelper {

    public static DebugProcessImpl.ResumeCommand createStepOverCommand(
            final SuspendContextImpl suspendContext,
            final boolean ignoreBreakpoints,
            final KtFile file,
            final IntRange linesRange,
            final List<KtFunction> inlineArguments,
            final List<PsiElement> additionalElementsToSkip
    ) {
        final DebugProcessImpl debugProcess = suspendContext.getDebugProcess();
        return debugProcess.new ResumeCommand(suspendContext) {
            @Override
            public void contextAction() {
                try {
                    StackFrameProxyImpl frameProxy = suspendContext.getFrameProxy();
                    if (frameProxy != null) {
                        Action action = KotlinSteppingCommandProviderKt.getStepOverPosition(
                                debugProcess,
                                frameProxy.location(),
                                file,
                                linesRange,
                                inlineArguments,
                                additionalElementsToSkip
                        );

                        DebugProcessImpl.ResumeCommand command =
                                KotlinSteppingCommandProviderKt.createCommand(debugProcess, suspendContext, ignoreBreakpoints, action);

                        if (command != null) {
                            command.contextAction();
                            return;
                        }
                    }

                    debugProcess.createStepOutCommand(suspendContext).contextAction();
                }
                catch (EvaluateException ignored) {
                }
            }
        };
    }

    public static DebugProcessImpl.ResumeCommand createStepOutCommand(
            final SuspendContextImpl suspendContext,
            final boolean ignoreBreakpoints,
            final List<KtNamedFunction> inlineFunctions,
            final KtFunctionLiteral inlineArgument
    ) {
        final DebugProcessImpl debugProcess = suspendContext.getDebugProcess();
        return debugProcess.new ResumeCommand(suspendContext) {
            @Override
            public void contextAction() {
                try {
                    StackFrameProxyImpl frameProxy = suspendContext.getFrameProxy();
                    if (frameProxy != null) {
                        Action action = KotlinSteppingCommandProviderKt.getStepOutPosition(
                                frameProxy.location(),
                                suspendContext,
                                inlineFunctions,
                                inlineArgument
                        );

                        DebugProcessImpl.ResumeCommand command =
                                KotlinSteppingCommandProviderKt.createCommand(debugProcess, suspendContext, ignoreBreakpoints, action);

                        if (command != null) {
                            command.contextAction();
                            return;
                        }
                    }

                    debugProcess.createStepOverCommand(suspendContext, ignoreBreakpoints).contextAction();
                }
                catch (EvaluateException ignored) {

                }
            }
        };
    }
}
