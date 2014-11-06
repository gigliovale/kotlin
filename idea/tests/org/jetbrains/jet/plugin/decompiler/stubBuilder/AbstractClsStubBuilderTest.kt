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

package org.jetbrains.jet.plugin.decompiler.stubBuilder

import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase
import org.jetbrains.jet.lang.psi.stubs.elements.JetFileStubBuilder
import com.intellij.psi.impl.DebugUtil
import org.jetbrains.jet.lang.resolve.name.SpecialNames
import org.jetbrains.jet.JetTestUtils
import java.io.File
import org.jetbrains.jet.MockLibraryUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.util.indexing.FileContentImpl
import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.StubElement
import java.util.ArrayList
import com.google.common.io.Files
import com.intellij.openapi.vfs.VirtualFile
import org.junit.Assert

//TODO: not clear that code in file and decompiled code differ
public open class AbstractClsStubBuilderTest : LightCodeInsightFixtureTestCase() {
    fun doTest(sourcePath: String) {
        val classFile = getClassFileToDecompile(sourcePath)
        val stubTreeFromCls = KotlinClsStubBuilder().buildFileStub(FileContentImpl.createByFile(classFile))!!
        val psiFile = myFixture.configureByFile(classFile.getPath())
        val stubTreeFromDecompiledText = JetFileStubBuilder().buildStubTree(psiFile)
        Assert.assertEquals(stubTreeFromDecompiledText.serializeToString(), stubTreeFromCls.serializeToString())
        //TODO: add comparison to file
    }

    //TODO: util
    private fun StubElement<out PsiElement>.serializeToString(): String {
        return DebugUtil.stubTreeToString(this).replace(SpecialNames.SAFE_IDENTIFIER_FOR_NO_NAME.asString(), "<no name>")
    }

    private fun getClassFileToDecompile(sourcePath: String): VirtualFile {
        //TODO: Review
        val outDir = JetTestUtils.tmpDir("libForStubTest-" + sourcePath)
        MockLibraryUtil.compileKotlin(sourcePath, outDir)
        //TODO: refactor
        val classFileToDecompile = outDir.findTargetClassFile(Files.getNameWithoutExtension(sourcePath.split("/").last()))
        return LocalFileSystem.getInstance().refreshAndFindFileByIoFile(classFileToDecompile)!!
    }

    private fun File.findTargetClassFile(classFileName: String): File {
        val result = ArrayList<File>()
        recurse {
            if (it.name == "$classFileName.class") {
                result.add(it)
            }
        }
        return result.single()
    }
}