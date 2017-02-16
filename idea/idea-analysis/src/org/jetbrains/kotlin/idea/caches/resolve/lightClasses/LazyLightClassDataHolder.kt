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

package org.jetbrains.kotlin.idea.caches.resolve.lightClasses

import com.intellij.psi.PsiClass
import org.jetbrains.kotlin.asJava.builder.*
import org.jetbrains.kotlin.asJava.elements.KtLightField
import org.jetbrains.kotlin.asJava.elements.KtLightMethod
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtClassOrObject

class LazyLightClassDataHolder(
        build: (LightClassConstructionContext) -> LightClassBuilderResult,
        getContext: () -> LightClassConstructionContext
) : LightClassDataHolder {

    private val builderResult by lazy {
        build(getContext())
    }

    override val javaFileStub get() = builderResult.stub
    override val extraDiagnostics get() = builderResult.diagnostics

    override fun findData(classOrObject: KtClassOrObject) = object: LightClassData {
        override val ownFields: Collection<KtLightField>
            get() = TODO("not implemented") //To change initializer of created properties use File | Settings | File Templates.
        override val ownMethods: Collection<KtLightMethod>
            get() = TODO("not implemented") //To change initializer of created properties use File | Settings | File Templates.
        override val clsDelegate: PsiClass by lazy { javaFileStub.findDelegate(classOrObject) }
    }

    override fun findData(classFqName: FqName) = object: LightClassData {
        override val ownFields: Collection<KtLightField>
            get() = TODO("not implemented") //To change initializer of created properties use File | Settings | File Templates.
        override val clsDelegate: PsiClass by lazy { javaFileStub.findDelegate(classFqName) }
        override val ownMethods: Collection<KtLightMethod>
            get() = TODO("not implemented") //To change initializer of created properties use File | Settings | File Templates.
    }
}