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

import com.intellij.psi.compiled.ClsStubBuilder
import com.intellij.util.cls.ClsFormatException
import com.intellij.util.indexing.FileContent
import com.intellij.psi.stubs.PsiFileStub
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.impl.compiled.ClassFileStubBuilder
import org.jetbrains.jet.plugin.decompiler
import org.jetbrains.jet.lang.resolve.kotlin.KotlinBinaryClassCache
import org.jetbrains.jet.lang.resolve.kotlin.header.KotlinClassHeader
import org.jetbrains.jet.descriptors.serialization.JavaProtoBufUtil
import org.jetbrains.jet.lang.psi.JetFile

public class KotlinClsStubBuilder : ClsStubBuilder() {
    override fun getStubVersion() = ClassFileStubBuilder.STUB_VERSION + 1

    override fun buildFileStub(content: FileContent): PsiFileStub<*>? {
        val file = content.getFile()

        if (decompiler.isKotlinInternalCompiledFile(file)) {
            return null
        }

        return doBuildFileStub(file)
    }

    throws(javaClass<ClsFormatException>())
    fun doBuildFileStub(file: VirtualFile): PsiFileStub<JetFile>? {
        val kotlinBinaryClass = KotlinBinaryClassCache.getKotlinBinaryClass(file)
        val classFqName = kotlinBinaryClass.getClassId().asSingleFqName().toSafe()
        val header = kotlinBinaryClass.getClassHeader()
        val packageFqName = classFqName.parent()
        return when (header.kind) {
            KotlinClassHeader.Kind.PACKAGE_FACADE -> CompiledPackageClassStubBuilder(
                    JavaProtoBufUtil.readPackageDataFrom(header.annotationData), packageFqName
            ).createStub()

            KotlinClassHeader.Kind.CLASS -> {
                val fileStub = createFileStub(packageFqName)
                CompiledClassStubBuilder(
                        JavaProtoBufUtil.readClassDataFrom(header.annotationData),
                        classFqName, packageFqName, fileStub, file
                ).createStub()
                fileStub
            }
            else -> throw IllegalStateException("Should have processed " + file.getPath() + " with ${header.kind}")
        }
    }
}