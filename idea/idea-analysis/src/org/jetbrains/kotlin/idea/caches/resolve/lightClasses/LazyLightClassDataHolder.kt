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
import com.intellij.psi.PsiClassType
import org.jetbrains.kotlin.asJava.builder.*
import org.jetbrains.kotlin.asJava.classes.KtLightClass
import org.jetbrains.kotlin.asJava.elements.KtLightField
import org.jetbrains.kotlin.asJava.elements.KtLightFieldImpl
import org.jetbrains.kotlin.asJava.elements.KtLightMethod
import org.jetbrains.kotlin.asJava.elements.KtLightMethodImpl
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtClassOrObject

class LazyLightClassDataHolder(
        build: (LightClassConstructionContext) -> LightClassBuilderResult,
        getRealContext: () -> LightClassConstructionContext,
        getDummyContext: () -> LightClassConstructionContext
) : LightClassDataHolder {

    private val exactResult by lazy(LazyThreadSafetyMode.PUBLICATION) { build(getRealContext()) }

    private val inexactResult by lazy(LazyThreadSafetyMode.PUBLICATION) { build(getDummyContext()) }

    override val javaFileStub get() = exactResult.stub
    override val extraDiagnostics get() = exactResult.diagnostics

    override fun findData(classOrObject: KtClassOrObject): LightClassData =
            LazyLightClassData(relyOnDummySupertypes = classOrObject.getSuperTypeList() == null) {
                it.stub.findDelegate(classOrObject)
            }

    override fun findData(classFqName: FqName): LightClassData =
            LazyLightClassData(relyOnDummySupertypes = true) { it.stub.findDelegate(classFqName) }


    private inner class LazyLightClassData(
            private val relyOnDummySupertypes: Boolean,
            findDelegate: (LightClassBuilderResult) -> PsiClass
    ) : LightClassData {
        override val clsDelegate: PsiClass by lazy(LazyThreadSafetyMode.PUBLICATION) { findDelegate(exactResult) }

        private val dummyDelegate: PsiClass by lazy(LazyThreadSafetyMode.PUBLICATION) { findDelegate(inexactResult) }

        override fun getOwnFields(containingClass: KtLightClass): List<KtLightField> {
            return dummyDelegate.fields.map { dummyDelegate ->
                val memberOrigin = ClsWrapperStubPsiFactory.getMemberOrigin(dummyDelegate)!!
                val fieldName = dummyDelegate.name!!
                KtLightFieldImpl.lazy(fieldName, memberOrigin, containingClass) {
                    clsDelegate.findFieldByName(fieldName, false)!!.apply {
                        assert(this.codegenMarker!! == dummyDelegate.codegenMarker!!)
                    }
                }
            }
        }

        override fun getOwnMethods(containingClass: KtLightClass): List<KtLightMethod> {
            return dummyDelegate.methods.map { dummyDelegate ->
                // TODO_R: correct origin
                val methodName = dummyDelegate.name
                val origin = ClsWrapperStubPsiFactory.getMemberOrigin(dummyDelegate)
                KtLightMethodImpl.lazy(methodName, containingClass, origin) {
                    val dummyMarker = dummyDelegate.codegenMarker!!
                    clsDelegate.findMethodsByName(methodName, false).filter {
                        delegateCandidate -> delegateCandidate.codegenMarker == dummyMarker
                    }.single().apply {
                        assert(this.parameterList.parametersCount == dummyDelegate.parameterList.parametersCount)
                    }
                }
            }
        }

        override val supertypes: Array<PsiClassType>
            get() = if (relyOnDummySupertypes) dummyDelegate.superTypes else clsDelegate.superTypes

    }
}
