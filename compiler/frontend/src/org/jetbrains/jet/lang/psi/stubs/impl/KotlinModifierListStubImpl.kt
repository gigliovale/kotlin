/*
 * Copyright 2010-2014 JetBrains s.r.o.
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

package org.jetbrains.jet.lang.psi.stubs.impl

import com.intellij.psi.stubs.StubElement
import com.intellij.util.ArrayUtil
import org.jetbrains.jet.lang.psi.JetModifierList
import org.jetbrains.jet.lang.psi.stubs.KotlinModifierListStub
import org.jetbrains.jet.lang.psi.stubs.elements.JetModifierListElementType
import org.jetbrains.jet.lexer.JetModifierKeywordToken

import org.jetbrains.jet.lexer.JetTokens.MODIFIER_KEYWORDS_ARRAY

public class KotlinModifierListStubImpl(
        parent: StubElement<PsiElement>,
        public val mask: Int,
        elementType: JetModifierListElementType<*>
) : KotlinStubBaseImpl<JetModifierList>(parent, elementType), KotlinModifierListStub {

    override fun hasModifier(modifierToken: JetModifierKeywordToken): Boolean {
        val index = ArrayUtil.indexOf(MODIFIER_KEYWORDS_ARRAY, modifierToken)
        assert(index >= 0, "All JetModifierKeywordTokens should be present in MODIFIER_KEYWORDS_ARRAY")
        return (mask and (1 shl index)) != 0
    }

    override fun toString(): String {
        val sb = StringBuilder()
        sb.append(super.toString())
        sb.append("[")
        var first = true
        for (modifierKeyword in MODIFIER_KEYWORDS_ARRAY) {
            if (hasModifier(modifierKeyword)) {
                if (!first) {
                    sb.append(" ")
                }
                sb.append(modifierKeyword.getValue())
                first = false
            }
        }
        sb.append("]")
        return sb.toString()
    }

    class object {

        {
            assert(MODIFIER_KEYWORDS_ARRAY.size <= 32, "Current implementation depends on the ability to represent modifier list as bit mask")
        }

        public fun computeMaskFromPsi(modifierList: JetModifierList): Int {
            var mask = 0
            val orderedKeywords = MODIFIER_KEYWORDS_ARRAY
            for (i in orderedKeywords.indices) {
                val modifierKeywordToken = orderedKeywords[i]
                if (modifierList.hasModifier(modifierKeywordToken)) {
                    mask = mask or (1 shl i)
                }
            }
            return mask
        }
    }
}
