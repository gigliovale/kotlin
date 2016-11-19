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

package kotlin.serialization.internal

import java.util.*
import kotlin.serialization.KSerialClassDesc
import kotlin.serialization.KSerialClassKind

open class SerialClassDescImpl(override val name: String) : KSerialClassDesc {
    override val kind: KSerialClassKind get() = KSerialClassKind.CLASS

    private val names: MutableList<String> = ArrayList()
    private var _indices: Map<String, Int>? = null
    private val indices: Map<String, Int> get() = _indices ?: buildIndices()

    fun addElement(name: String) {
        names.add(name)
    }

    override fun getElementName(index: Int): String = names[index]
    override fun getElementIndex(name: String): Int = indices[name] ?: throw IllegalArgumentException("Unknown name '$name'")

    private fun buildIndices(): Map<String, Int> {
        val indices = HashMap<String, Int>()
        for (i in 0..names.size - 1)
            indices.put(names[i], i)
        _indices = indices
        return indices
    }

    override fun toString() = "$name$names"
}