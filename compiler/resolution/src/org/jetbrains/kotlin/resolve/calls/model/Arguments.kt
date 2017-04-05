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

package org.jetbrains.kotlin.resolve.calls.model

import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.resolve.calls.BaseResolvedCall
import org.jetbrains.kotlin.resolve.calls.inference.model.ConstraintStorage
import org.jetbrains.kotlin.resolve.calls.tower.CandidateWithBoundDispatchReceiver
import org.jetbrains.kotlin.resolve.scopes.receivers.DetailedReceiver
import org.jetbrains.kotlin.resolve.scopes.receivers.QualifierReceiver
import org.jetbrains.kotlin.resolve.scopes.receivers.ReceiverValue
import org.jetbrains.kotlin.resolve.scopes.receivers.ReceiverValueWithSmartCastInfo
import org.jetbrains.kotlin.types.UnwrappedType


interface ReceiverCallArgument {
    val receiver: DetailedReceiver
}

class QualifierReceiverCallArgument(override val receiver: QualifierReceiver) : ReceiverCallArgument {
    override fun toString() = "$receiver"
}

interface CallArgument {
    val isSpread: Boolean
    val argumentName: Name?
}

interface SimpleCallArgument : CallArgument, ReceiverCallArgument {
    override val receiver: ReceiverValueWithSmartCastInfo

    val isSafeCall: Boolean
}

interface ExpressionArgument : SimpleCallArgument


interface SubCallArgument : SimpleCallArgument {
    val resolvedCall: BaseResolvedCall.OnlyResolvedCall
}

interface LambdaArgument : CallArgument {
    override val isSpread: Boolean
        get() = false

    /**
     * parametersTypes == null means, that there is no declared arguments
     * null inside array means that this type is not declared explicitly
     */
    val parametersTypes: Array<UnwrappedType?>?
}

interface FunctionExpression : LambdaArgument {
    override val parametersTypes: Array<UnwrappedType?>

    // null means that there function can not have receiver
    val receiverType: UnwrappedType?

    // null means that return type is not declared, for fun(){ ... } returnType == Unit
    val returnType: UnwrappedType?
}

interface CallableReferenceArgument : CallArgument {
    override val isSpread: Boolean
        get() = false

    // Foo::bar lhsType = Foo. For a::bar where a is expression, this type is null
    val lhsType: UnwrappedType?

    val constraintStorage: ConstraintStorage
}

interface ChosenCallableReferenceDescriptor : CallableReferenceArgument {
    val candidate: CandidateWithBoundDispatchReceiver

    val extensionReceiver: ReceiverValue?
}


interface TypeArgument

// todo allow '_' in frontend
object TypeArgumentPlaceholder : TypeArgument

interface SimpleTypeArgument: TypeArgument {
    val type: UnwrappedType
}
