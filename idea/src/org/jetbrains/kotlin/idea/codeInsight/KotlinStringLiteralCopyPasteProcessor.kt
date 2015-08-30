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
package org.jetbrains.kotlin.idea.codeInsight

import com.intellij.codeInsight.editorActions.CopyPastePreProcessor
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.RawText
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.LineTokenizer
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.util.text.StringUtil.unescapeStringCharacters
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.codeStyle.CodeStyleSettingsManager
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.annotations.NonNls
import org.jetbrains.kotlin.lexer.JetTokens

public class KotlinStringLiteralCopyPasteProcessor : CopyPastePreProcessor {

    override fun preprocessOnCopy(file: PsiFile, startOffsets: IntArray, endOffsets: IntArray, text: String): String? {
        val buffer = StringBuilder()
        var givenTextOffset = 0
        var textWasChanged = false
        val deducedBlockSelectionWidth = deduceBlockSelectionWidth(startOffsets, endOffsets, text)
        var i = 0
        while (i < startOffsets.size() && givenTextOffset < text.length()) {
            if (i > 0) {
                buffer.append('\n') // LF is added for block selection
            }
            // Calculate offsets offsets of the selection interval being processed now.
            val fileStartOffset = startOffsets[i]
            val fileEndOffset = endOffsets[i]
            var givenTextStartOffset = Math.min(givenTextOffset, text.length())
            val givenTextEndOffset = Math.min(givenTextOffset + (fileEndOffset - fileStartOffset), text.length())
            givenTextOffset = givenTextEndOffset
            var element = file.findElementAt(fileStartOffset)
            while (givenTextStartOffset < givenTextEndOffset) {
                if (element == null) {
                    buffer.append(text.substring(givenTextStartOffset, givenTextEndOffset))
                    break
                }
                val elementRange = element.textRange
                val escapedStartOffset: Int
                val escapedEndOffset: Int
                if ((isStringLiteral(element) || isCharLiteral(element)) && (elementRange.startOffset < fileStartOffset || elementRange.endOffset > fileEndOffset)// We don't want to un-escape if complete literal is copied.
                ) {
                    escapedStartOffset = elementRange.startOffset + 1 /* String/char literal quote */
                    escapedEndOffset = elementRange.endOffset - 1 /* String/char literal quote */
                }
                else {
                    escapedEndOffset = elementRange.startOffset
                    escapedStartOffset = elementRange.startOffset
                }

                // Process text to the left of the escaped fragment (if any).
                var numberOfSymbolsToCopy = escapedStartOffset - Math.max(fileStartOffset, elementRange.startOffset)
                if (numberOfSymbolsToCopy > 0) {
                    buffer.append(text.substring(givenTextStartOffset, givenTextStartOffset + numberOfSymbolsToCopy))
                    givenTextStartOffset += numberOfSymbolsToCopy
                }

                // Process escaped text (un-escape it).
                numberOfSymbolsToCopy = Math.min(escapedEndOffset, fileEndOffset) - Math.max(fileStartOffset, escapedStartOffset)
                if (numberOfSymbolsToCopy > 0) {
                    textWasChanged = true
                    buffer.append(unescape(text.substring(givenTextStartOffset, givenTextStartOffset + numberOfSymbolsToCopy), element))
                    givenTextStartOffset += numberOfSymbolsToCopy
                }

                // Process text to the right of the escaped fragment (if any).
                numberOfSymbolsToCopy = Math.min(fileEndOffset, elementRange.endOffset) - Math.max(fileStartOffset, escapedEndOffset)
                if (numberOfSymbolsToCopy > 0) {
                    buffer.append(text.substring(givenTextStartOffset, givenTextStartOffset + numberOfSymbolsToCopy))
                    givenTextStartOffset += numberOfSymbolsToCopy
                }
                element = PsiTreeUtil.nextLeaf(element)
            }
            val blockSelectionPadding = deducedBlockSelectionWidth - (fileEndOffset - fileStartOffset)
            for (j in 0..blockSelectionPadding - 1) {
                buffer.append(' ')
                givenTextOffset++
            }
            i++
            givenTextOffset++
        }
        return if (textWasChanged) buffer.toString() else null
    }

    private fun deduceBlockSelectionWidth(startOffsets: IntArray, endOffsets: IntArray, text: String): Int {
        val fragmentCount = startOffsets.size()
        assert(fragmentCount > 0)
        var totalLength = fragmentCount - 1 // number of line breaks inserted between fragments
        for (i in 0..fragmentCount - 1) {
            totalLength += endOffsets[i] - startOffsets[i]
        }
        if (totalLength < text.length() && (text.length() + 1) % fragmentCount == 0) {
            return (text.length() + 1) / fragmentCount - 1
        }
        else {
            return -1
        }
    }

    protected fun unescape(text: String, token: PsiElement): String {
        return unescapeStringCharacters(text)
    }

    override fun preprocessOnPaste(project: Project, file: PsiFile, editor: Editor, text: String, rawText: RawText?): String {
        var text = text
        val document = editor.document
        PsiDocumentManager.getInstance(project).commitDocument(document)
        val selectionModel = editor.selectionModel

        // pastes in block selection mode (column mode) are not handled by a CopyPasteProcessor
        val selectionStart = selectionModel.selectionStart
        val selectionEnd = selectionModel.selectionEnd
        val token = findLiteralTokenType(file, selectionStart, selectionEnd) ?: return text

        if (isStringLiteral(token)) {
            val buffer = StringBuilder(text.length())
            @NonNls val breaker = getLineBreaker(token)
            val lines = LineTokenizer.tokenize(text.toCharArray(), false, true)
            for (i in lines.indices) {
                buffer.append(escapeCharCharacters(lines[i], token))
                if (i != lines.size() - 1) {
                    buffer.append(breaker)
                }
                else if (text.endsWith("\n")) {
                    buffer.append("\\n")
                }
            }
            text = buffer.toString()
        }
        else if (isCharLiteral(token)) {
            return escapeCharCharacters(text, token)
        }
        return text
    }

    protected fun getLineBreaker(token: PsiElement): String {
        val codeStyleSettings = CodeStyleSettingsManager.getSettings(token.project).getCommonSettings(token.language)
        return if (codeStyleSettings.BINARY_OPERATION_SIGN_ON_NEXT_LINE) "\\n\"\n+ \"" else "\\n\" +\n\""
    }

    protected fun findLiteralTokenType(file: PsiFile, selectionStart: Int, selectionEnd: Int): PsiElement? {
        val elementAtSelectionStart = file.findElementAt(selectionStart) ?: return null
        if (!isStringLiteral(elementAtSelectionStart) && !isCharLiteral(elementAtSelectionStart)) {
            return null
        }

        if (elementAtSelectionStart.textRange.endOffset < selectionEnd) {
            val elementAtSelectionEnd = file.findElementAt(selectionEnd) ?: return null
            if (elementAtSelectionEnd.node.elementType === elementAtSelectionStart.node.elementType && elementAtSelectionEnd.textRange.startOffset < selectionEnd) {
                return elementAtSelectionStart
            }
        }

        val textRange = elementAtSelectionStart.textRange
        if (selectionStart <= textRange.startOffset || selectionEnd >= textRange.endOffset) {
            return null
        }
        return elementAtSelectionStart
    }

    protected fun isCharLiteral(token: PsiElement): Boolean {
        val node = token.node
        return node != null && node.elementType === JetTokens.CHARACTER_LITERAL
    }

    protected fun isStringLiteral(token: PsiElement): Boolean {
        val node = token.node ?: return false
        if (node.elementType === JetTokens.REGULAR_STRING_PART) {
            return true
        }
        if (node.elementType == JetTokens.CLOSING_QUOTE || node.elementType == JetTokens.OPEN_QUOTE) {
            return true
        }
        return false
    }

    protected fun escapeCharCharacters(s: String, token: PsiElement): String {
        val buffer = StringBuilder()
        StringUtil.escapeStringCharacters(s.length(), s, if (isStringLiteral(token)) "\"" else "\'", buffer)
        return buffer.toString()
    }
}
