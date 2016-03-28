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

package org.jetbrains.kotlin.idea.caches.resolve

import com.intellij.psi.*

object MapPsiToAsmDesc {
    fun typeDesc(type: PsiType): String = when (type) {
        PsiType.VOID -> "V"

        PsiType.BOOLEAN -> "Z"

        PsiType.CHAR -> "C"
        PsiType.INT -> "I"
        PsiType.BYTE -> "B"
        PsiType.SHORT -> "S"
        PsiType.LONG -> "J"

        PsiType.FLOAT -> "F"
        PsiType.DOUBLE -> "D"

        is PsiArrayType -> "[" + typeDesc(type.componentType)

        is PsiClassType -> {
            val resolved = type.resolve()
            when (resolved) {
                is PsiTypeParameter -> resolved.superTypes.firstOrNull()?.let { typeDesc(it) } ?: "Ljava/lang/Object;"
                is PsiClass -> classDesc(resolved)
                else -> error("TODO") //TODO_R:
            }

        }
        else -> error("TODO") // TODO_R:
    }

    private fun classDesc(psiClass: PsiClass) = buildString {
        append("L")
        val classes = generateSequence(psiClass) { it.containingClass }.toList().reversed()
        append(classes.first().qualifiedName!!.replace(".", "/"))
        classes.drop(1).forEach {
            append("$")
            append(it.name!!)
        }
        append(";")
    }

    fun methodDesc(psiMethod: PsiMethod): String = buildString {
        append("(")
        psiMethod.parameterList.parameters.forEach {
            append(typeDesc(it.type))
        }
        append(")")
        append(typeDesc(psiMethod.returnType ?: return ""))
    }
}