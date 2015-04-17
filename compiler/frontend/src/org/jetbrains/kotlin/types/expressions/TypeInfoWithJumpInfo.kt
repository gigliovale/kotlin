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

package org.jetbrains.kotlin.types.expressions

import org.jetbrains.kotlin.builtins.KotlinBuiltIns
import org.jetbrains.kotlin.psi.JetExpression
import org.jetbrains.kotlin.resolve.calls.context.ResolutionContext
import org.jetbrains.kotlin.resolve.calls.smartcasts.DataFlowInfo
import org.jetbrains.kotlin.types.JetType
import org.jetbrains.kotlin.types.JetTypeInfo

/**
 * A local descendant of JetTypeInfo. Stores simultaneously current type with data flow info
 * and jump point data flow info, together with information about possible jump outside. For example:
 * do {
 * x!!.foo()
 * if (bar()) break;
 * y!!.gav()
 * } while (bis())
 * At the end current data flow info is x != null && y != null, but jump data flow info is x != null only.
 * Both break and continue are counted as possible jump outside of a loop, but return is not.
 */
/*package*/ open class TypeInfoWithJumpInfo(
        type: JetType?,
        dataFlowInfo: DataFlowInfo,
        val jumpOutPossible: Boolean = false,
        val jumpFlowInfo: DataFlowInfo = dataFlowInfo
) : JetTypeInfo(type, dataFlowInfo) {

    fun clearType() = replaceType(null)

    fun checkType(expression: JetExpression, context: ResolutionContext<*>) =
            replaceType(DataFlowUtils.checkType(getType(), expression, context))

    fun checkImplicitCast(expression: JetExpression, context: ResolutionContext<*>, isStatement: Boolean) =
            replaceType(DataFlowUtils.checkImplicitCast(getType(), expression, context, isStatement))

    fun replaceType(type: JetType?) =
            if (type == getType()) {
                this
            }
            else {
                TypeInfoWithJumpInfo(type, getDataFlowInfo(), jumpOutPossible, jumpFlowInfo)
            }

    fun replaceJumpOutPossible(jumpOutPossible: Boolean) =
            if (jumpOutPossible == this.jumpOutPossible) {
                this
            }
            else{
                TypeInfoWithJumpInfo(getType(), getDataFlowInfo(), jumpOutPossible, jumpFlowInfo)
            }

    fun replaceJumpFlowInfo(jumpFlowInfo: DataFlowInfo) =
            if (jumpFlowInfo == this.jumpFlowInfo) {
                this
            }
            else {
                TypeInfoWithJumpInfo(getType(), getDataFlowInfo(), jumpOutPossible, jumpFlowInfo)
            }

    fun replaceDataFlowInfo(dataFlowInfo: DataFlowInfo) =
            if (dataFlowInfo == this.getDataFlowInfo()) {
                this
            }
            else {
                TypeInfoWithJumpInfo(getType(), dataFlowInfo, jumpOutPossible, jumpFlowInfo)
            }
}