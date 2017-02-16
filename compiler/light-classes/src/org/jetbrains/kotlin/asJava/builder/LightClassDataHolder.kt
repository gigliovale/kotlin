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

package org.jetbrains.kotlin.asJava.builder

import com.intellij.psi.PsiClass
import com.intellij.psi.impl.DebugUtil
import com.intellij.psi.impl.java.stubs.PsiJavaFileStub
import com.intellij.psi.stubs.StubElement
import org.jetbrains.kotlin.asJava.LightClassUtil
import org.jetbrains.kotlin.asJava.LightClassUtil.findClass
import org.jetbrains.kotlin.asJava.classes.getOutermostClassOrObject
import org.jetbrains.kotlin.asJava.elements.KtLightField
import org.jetbrains.kotlin.asJava.elements.KtLightMethod
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.resolve.diagnostics.Diagnostics

interface LightClassDataHolder {
    val javaFileStub: PsiJavaFileStub
    val extraDiagnostics: Diagnostics
}

interface LightClassData {
    val clsDelegate: PsiClass
    val ownFields: Collection<KtLightField>
    val ownMethods: Collection<KtLightMethod>
}

class LightClassDataImpl(override val clsDelegate: PsiClass) : LightClassData {
    override val ownFields: Collection<KtLightField>
        get() = TODO("not implemented")
    override val ownMethods: Collection<KtLightMethod>
        get() = TODO("not implemented")
}

object InvalidLightClassDataHolder : LightClassDataHolder {
    override val javaFileStub: PsiJavaFileStub
        get() = shouldNotBeCalled()
    override val extraDiagnostics: Diagnostics
        get() = shouldNotBeCalled()

    private fun shouldNotBeCalled(): Nothing = throw UnsupportedOperationException("Should not be called")
}

class LightClassDataHolderImpl(
        override val javaFileStub: PsiJavaFileStub,
        override val extraDiagnostics: Diagnostics
) : LightClassDataHolder {

    fun findData(classOrObject: KtClassOrObject): LightClassData {
        findClass(javaFileStub) {
            ClsWrapperStubPsiFactory.getOriginalElement(it as StubElement<*>) == classOrObject
        }?.let { return LightClassDataImpl(it) }

        val outermostClassOrObject = getOutermostClassOrObject(classOrObject)
        val ktFileText: String? = try {
            outermostClassOrObject.containingFile.text
        }
        catch (e: Exception) {
            "Can't get text for outermost class"
        }

        val stubFileText = DebugUtil.stubTreeToString(javaFileStub)
        throw IllegalStateException("Couldn't get delegate for $this\nin $ktFileText\nstub: \n$stubFileText")
    }

    fun findData(classFqName: FqName): LightClassData {
        return findClass(javaFileStub) {
            classFqName.asString() == it.qualifiedName
        }?.let(::LightClassDataImpl) ?: throw IllegalStateException("Facade class $classFqName not found; classes in Java file stub: ${collectClassNames(javaFileStub)}")
    }

    private fun collectClassNames(javaFileStub: PsiJavaFileStub): String {
        val names = mutableListOf<String>()
        LightClassUtil.findClass(javaFileStub) { cls ->
            names.add(cls.qualifiedName ?: "<null>")
            false
        }
        return names.joinToString(prefix = "[", postfix = "]")
    }
}