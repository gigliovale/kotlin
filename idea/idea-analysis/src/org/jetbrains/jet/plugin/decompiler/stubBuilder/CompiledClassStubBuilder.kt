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

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.stubs.StubElement
import org.jetbrains.jet.descriptors.serialization.ClassData
import org.jetbrains.jet.descriptors.serialization.Flags
import org.jetbrains.jet.descriptors.serialization.ProtoBuf
import org.jetbrains.jet.lang.psi.stubs.KotlinStubWithFqName
import org.jetbrains.jet.lang.psi.stubs.elements.JetClassElementType
import org.jetbrains.jet.lang.psi.stubs.impl.KotlinClassStubImpl
import org.jetbrains.jet.lang.psi.stubs.impl.KotlinObjectStubImpl
import org.jetbrains.jet.lang.resolve.name.FqName
import org.jetbrains.jet.lang.resolve.name.Name
import com.intellij.psi.PsiElement
import org.jetbrains.jet.lang.psi.JetClassBody
import org.jetbrains.jet.lang.psi.stubs.impl.KotlinPlaceHolderStubImpl
import org.jetbrains.jet.lang.psi.stubs.elements.JetStubElementTypes
import com.intellij.util.io.StringRef
import org.jetbrains.jet.lang.resolve.kotlin.KotlinBinaryClassCache
import org.jetbrains.jet.descriptors.serialization.JavaProtoBufUtil
import org.jetbrains.jet.lexer.JetModifierKeywordToken
import org.jetbrains.jet.descriptors.serialization.ProtoBuf.Visibility
import org.jetbrains.jet.lexer.JetTokens
import org.jetbrains.jet.descriptors.serialization.ProtoBuf.Modality
import com.intellij.psi.PsiNamedElement
import org.jetbrains.jet.lang.psi.stubs.impl.KotlinModifierListStubImpl
import org.jetbrains.jet.lang.psi.stubs.impl.ModifierMaskUtils
import org.jetbrains.jet.lang.psi.JetParameterList
import kotlin.properties.Delegates

public class CompiledClassStubBuilder(
        classData: ClassData,
        private val classFqName: FqName,
        packageFqName: FqName,
        private val parent: StubElement<out PsiElement>,
        private val file: VirtualFile
) : CompiledStubBuilderBase(classData.getNameResolver(), packageFqName) {
    private val classProto = classData.getClassProto()
    private var rootStub: KotlinStubWithFqName<out PsiNamedElement> by Delegates.notNull()

    override fun getInternalFqName(name: String) = null

    public fun createStub() {
        createRootStub()
        createModifierListStub()
        createConstructorStub()
        val classBody = KotlinPlaceHolderStubImpl<JetClassBody>(rootStub, JetStubElementTypes.CLASS_BODY)

        for (nestedNameIndex in classProto.getNestedClassNameList()) {
            val nestedName = nameResolver.getName(nestedNameIndex)
            val nestedFile = findNestedClassFile(file, nestedName)
            val kotlinBinaryClass = KotlinBinaryClassCache.getKotlinBinaryClass(nestedFile)
            val classFqName = kotlinBinaryClass.getClassId().asSingleFqName().toSafe()
            val classData = JavaProtoBufUtil.readClassDataFrom(kotlinBinaryClass.getClassHeader().annotationData)
            // TODO package name is not needed
            CompiledClassStubBuilder(classData, classFqName, classFqName, classBody, nestedFile).createStub()
        }

        //        if (classProto.hasClassObject() && kind != ProtoBuf.Class.Kind.ENUM_CLASS) {
        //            // TODO enum
        //            val nestedFile = findNestedClassFile(file, Name.identifier(JvmAbi.CLASS_OBJECT_CLASS_NAME))
        //            val kotlinBinaryClass = KotlinBinaryClassCache.getKotlinBinaryClass(nestedFile)
        //            val classFqName = kotlinBinaryClass.getClassId().asSingleFqName().toSafe()
        //            val classData = JavaProtoBufUtil.readClassDataFrom(kotlinBinaryClass.getClassHeader().annotationData)
        //            // TODO package name is not needed
        //            CompiledClassStubBuilder(classData, classFqName, classFqName.parent(), classBody, nestedFile).createStub()
        //        }

        createMemberStubs(classBody)
    }

    private fun createMemberStubs(classBody: KotlinPlaceHolderStubImpl<JetClassBody>) {
        for (callableProto in classProto.getMemberList()) {
            createCallableStub(classBody, callableProto)
        }
    }

    private fun createRootStub() {
        val kind = Flags.CLASS_KIND.get(classProto.getFlags())
        val isEnumEntry = kind == ProtoBuf.Class.Kind.ENUM_ENTRY
        //TODO: inner classes
        val shortName = classFqName.shortName().asString().ref()
        if (kind == ProtoBuf.Class.Kind.OBJECT) {
            rootStub = KotlinObjectStubImpl(
                    parent, shortName, classFqName, getSuperTypeRefs(),
                    isTopLevel = true,
                    isClassObject = false,
                    isLocal = false,
                    isObjectLiteral = false
            )
        }
        else {
            rootStub = KotlinClassStubImpl(
                    JetClassElementType.getStubType(isEnumEntry), parent, classFqName.asString().ref(), shortName,
                    getSuperTypeRefs(),
                    isTrait = kind == ProtoBuf.Class.Kind.TRAIT,
                    isEnumEntry = kind == ProtoBuf.Class.Kind.ENUM_ENTRY,
                    isLocal = false,
                    isTopLevel = true
            )
        }
    }

    private fun getSuperTypeRefs(): Array<StringRef> {
        val superTypeStrings = classProto.getSupertypeList().map {
            type ->
            assert(type.getConstructor().getKind() == ProtoBuf.Type.Constructor.Kind.CLASS)
            val superFqName = nameResolver.getFqName(`type`.getConstructor().getId())
            superFqName.asString()
        }
        return superTypeStrings.filter { it != "kotlin.Any" }.map { it.ref() }.copyToArray()
    }

    private fun findNestedClassFile(file: VirtualFile, innerName: Name): VirtualFile {
        val baseName = file.getNameWithoutExtension()
        val dir = file.getParent()
        assert(dir != null)
        return dir!!.findChild(baseName + "$" + innerName.asString() + ".class")
    }

    fun createModifierListStub() {
        val flags = classProto.getFlags()
        val modifiersArray = array(
                modalityToModifier(Flags.MODALITY.get(flags)),
                visibilityToModifier(Flags.VISIBILITY.get(flags))
        )
        KotlinModifierListStubImpl(
                rootStub,
                ModifierMaskUtils.computeMask { it in modifiersArray },
                JetStubElementTypes.MODIFIER_LIST
        )
    }

    fun modalityToModifier(modality: Modality): JetModifierKeywordToken {
        return when (modality) {
            ProtoBuf.Modality.ABSTRACT -> JetTokens.ABSTRACT_KEYWORD
            ProtoBuf.Modality.FINAL -> JetTokens.FINAL_KEYWORD
            ProtoBuf.Modality.OPEN -> JetTokens.OPEN_KEYWORD
            else -> throw IllegalStateException("Unexpected modality: $modality")
        }
    }

    fun visibilityToModifier(visibility: Visibility): JetModifierKeywordToken {
        return when (visibility) {
            ProtoBuf.Visibility.PRIVATE -> JetTokens.PRIVATE_KEYWORD
            ProtoBuf.Visibility.INTERNAL -> JetTokens.INTERNAL_KEYWORD
            ProtoBuf.Visibility.PROTECTED -> JetTokens.PROTECTED_KEYWORD
            ProtoBuf.Visibility.PRIVATE -> JetTokens.PRIVATE_KEYWORD
            //TODO: support extra visibility
            else -> throw IllegalStateException("Unexpected visibility: $visibility")
        }
    }

    fun createConstructorStub() {
        KotlinPlaceHolderStubImpl<JetParameterList>(rootStub, JetStubElementTypes.VALUE_PARAMETER_LIST)
    }
}

fun String.ref() = StringRef.fromString(this)
