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

package org.jetbrains.kotlin.js.dce

import org.jetbrains.kotlin.js.backend.ast.JsNode

interface Value {
    val jsNode: JsNode?

    val path: String

    fun getMember(name: String): Node

    fun getDynamicMember(): Node

    fun getParameter(index: Int): Node

    fun getReturnValue(): Node

    fun addHandler(handler: ValueEventHandler)

    val isUsed: Boolean

    fun use()

    val stringConstant: String?
}
