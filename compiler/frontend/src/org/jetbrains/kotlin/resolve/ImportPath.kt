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

package org.jetbrains.kotlin.resolve

import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.renderer.render

interface Import {
    val isAllUnder: Boolean
    val fqName: FqName?
    val alias: Name?
}

val Import.hasAlias get() = alias != null
val Import.importedName: Name? get() {
    if (!isAllUnder) {
        return alias ?: fqName?.shortName()
    }

    return null
}

fun Import.getText(): String {
    val fqNameStr = fqName?.toUnsafe()?.render() ?: return ""
    val pathStr = fqNameStr + if (isAllUnder) ".*" else ""
    return pathStr + if (alias != null && !isAllUnder) (" as " + alias?.asString()) else ""
}

data class ImportPath @JvmOverloads constructor(
        override val fqName: FqName,
        override val isAllUnder: Boolean,
        override val alias: Name? = null): Import {

    override fun toString(): String = getText()

    companion object {
        @JvmStatic fun fromString(pathStr: String): ImportPath {
            if (pathStr.endsWith(".*")) {
                return ImportPath(FqName(pathStr.substring(0, pathStr.length - 2)), isAllUnder = true)
            }
            else {
                return ImportPath(FqName(pathStr), isAllUnder = false)

            }
        }
    }
}
