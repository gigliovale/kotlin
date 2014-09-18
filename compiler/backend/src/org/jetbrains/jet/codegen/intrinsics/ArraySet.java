/*
 * Copyright 2010-2013 JetBrains s.r.o.
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

package org.jetbrains.jet.codegen.intrinsics;

import com.intellij.psi.PsiElement;
import kotlin.ExtensionFunction1;
import kotlin.Unit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jet.codegen.CallableMethod;
import org.jetbrains.jet.codegen.ExtendedCallable;
import org.jetbrains.org.objectweb.asm.Type;
import org.jetbrains.org.objectweb.asm.commons.InstructionAdapter;
import org.jetbrains.jet.codegen.ExpressionCodegen;
import org.jetbrains.jet.codegen.StackValue;
import org.jetbrains.jet.lang.psi.JetExpression;

import java.util.List;

import static org.jetbrains.jet.codegen.AsmUtil.correctElementType;

public class ArraySet extends IntrinsicMethod {
    @NotNull
    @Override
    public Type generateImpl(
            @NotNull ExpressionCodegen codegen,
            @NotNull InstructionAdapter v,
            @NotNull Type returnType,
            PsiElement element,
            List<JetExpression> arguments,
            StackValue receiver
    ) {
        receiver.put(receiver.type, v);
        Type type = correctElementType(receiver.type);

        codegen.gen(arguments.get(0), Type.INT_TYPE);
        codegen.gen(arguments.get(1), type);

        v.astore(type);
        return Type.VOID_TYPE;
    }

    @Override
    public boolean supportCallable() {
        return true;
    }

    @NotNull
    @Override
    public IntrinsicCallable toCallable(@NotNull CallableMethod method) {
        return new MappedCallable(method, new ExtensionFunction1<MappedCallable, InstructionAdapter, Unit>() {
            @Override
            public Unit invoke(
                    MappedCallable callable,
                    InstructionAdapter adapter
            ) {
                Type type = correctElementType(callable.calcReceiverType());
                adapter.astore(type);
                return Unit.INSTANCE$;
            }
        });
    }
}
