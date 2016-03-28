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

import com.intellij.psi.PsiField
import com.intellij.psi.PsiMember
import com.intellij.psi.PsiMethod
import org.jetbrains.kotlin.asJava.LightMemberOrigin
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.idea.decompiler.classFile.KtClsFile
import org.jetbrains.kotlin.idea.decompiler.textBuilder.DecompiledTextIndexer
import org.jetbrains.kotlin.load.java.JvmAbi
import org.jetbrains.kotlin.load.kotlin.MemberSignature
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.resolve.DescriptorUtils
import org.jetbrains.kotlin.resolve.descriptorUtil.classId
import org.jetbrains.kotlin.resolve.jvm.diagnostics.JvmDeclarationOriginKind
import org.jetbrains.kotlin.serialization.deserialization.descriptors.DeserializedPropertyDescriptor
import org.jetbrains.kotlin.serialization.deserialization.descriptors.DeserializedSimpleFunctionDescriptor
import org.jetbrains.kotlin.serialization.jvm.JvmProtoBuf
import org.jetbrains.kotlin.serialization.jvm.JvmProtoBufUtil

interface LightMemberOriginForCompiledElement : LightMemberOrigin {
    override val originKind: JvmDeclarationOriginKind
        get() = JvmDeclarationOriginKind.OTHER
}


data class LightMemberOriginForCompiledField(val field: PsiField, val file: KtClsFile) : LightMemberOriginForCompiledElement {
    override fun copy(): LightMemberOrigin {
        return LightMemberOriginForCompiledField(field.copy() as PsiField, file)
    }

    override val originalElement: KtDeclaration?
        get() {
            val desc = MapPsiToAsmDesc.typeDesc(this.field.type)
            val signature = MemberSignature.fromFieldNameAndDesc(this.field.name!!, desc)
            return file.getDeclaration(ByJvmSignatureIndexer, ClassNameAndSignature(this.field.relativeClassName(), signature))
        }
}

data class LightMemberOriginForCompiledMethod(val method: PsiMethod, val file: KtClsFile) : LightMemberOriginForCompiledElement {
    override val originalElement: KtDeclaration?
        get() {
            val desc = MapPsiToAsmDesc.methodDesc(method)
            val signature = MemberSignature.fromMethodNameAndDesc(method.name, desc)
            return file.getDeclaration(ByJvmSignatureIndexer, ClassNameAndSignature(method.relativeClassName(), signature))
        }

    override fun copy(): LightMemberOrigin {
        return LightMemberOriginForCompiledMethod(method.copy() as PsiMethod, file)
    }
}

private data class ClassNameAndSignature(val relativeClassName: List<Name>, val memberSignature: MemberSignature)

private fun PsiMember.relativeClassName(): List<Name> {
    return generateSequence(this.containingClass) { it.containingClass }.toList().dropLast(1).reversed().map { Name.identifier(it.name!!) }
}

private fun ClassDescriptor.relativeClassName(): List<Name> {
    return classId.relativeClassName.pathSegments().drop(1).toList().orEmpty()
}

private fun ClassDescriptor.desc(): String = "L" + this.classId.packageFqName.asString().replace(".", "/") + "/" + this.classId.relativeClassName.asString().replace(".", "$") + ";"

private object ByJvmSignatureIndexer : DecompiledTextIndexer<ClassNameAndSignature> {
    override fun indexDescriptor(descriptor: DeclarationDescriptor): Collection<ClassNameAndSignature> {
        val signatures = arrayListOf<ClassNameAndSignature>()

        fun save(signature: ClassNameAndSignature) {
            signatures.add(signature)
        }

        fun save(id: List<Name>, signature: MemberSignature) {
            signatures.add(ClassNameAndSignature(id, signature))
        }

        if (descriptor is ClassDescriptor) {
            when (descriptor.kind) {
                ClassKind.ENUM_ENTRY -> {
                    val enumClass = descriptor.containingDeclaration as ClassDescriptor
                    val signature = MemberSignature.fromFieldNameAndDesc(descriptor.name.asString(), enumClass.desc())
                    save(enumClass.relativeClassName(), signature)
                }
                ClassKind.OBJECT -> {
                    val instanceFieldSignature = MemberSignature.fromFieldNameAndDesc(JvmAbi.INSTANCE_FIELD, descriptor.desc())
                    save(descriptor.relativeClassName(), instanceFieldSignature)
                    if (descriptor.isCompanionObject) {
                        val signature = MemberSignature.fromFieldNameAndDesc(descriptor.name.asString(), descriptor.desc())
                        save((descriptor.containingDeclaration as? ClassDescriptor)?.relativeClassName().orEmpty(), signature)
                    }
                }
            }
        }

        if (descriptor is DeserializedSimpleFunctionDescriptor) {
            JvmProtoBufUtil.getJvmMethodSignature(descriptor.proto, descriptor.nameResolver, descriptor.typeTable)?.let {
                val signature = MemberSignature.fromMethodNameAndDesc(it)
                val id = (descriptor.containingDeclaration as? ClassDescriptor)?.relativeClassName().orEmpty()
                save(id, signature)
            }
        }
        if (descriptor is DeserializedPropertyDescriptor) {
            val proto = descriptor.proto
            val className = (descriptor.containingDeclaration as? ClassDescriptor)?.relativeClassName().orEmpty()
            if (proto.hasExtension(JvmProtoBuf.propertySignature)) {
                val signature = proto.getExtension(JvmProtoBuf.propertySignature)
                if (signature.hasField()) {
                    val field = signature.field
                    save(className, MemberSignature.fromFieldNameAndDesc(descriptor.name.asString(), descriptor.nameResolver.getString(field.desc))) //TODO_R: test this line
                }
                if (signature.hasGetter()) {
                    save(className, MemberSignature.fromMethod(descriptor.nameResolver, signature.getter))
                }
                if (signature.hasSetter()) {
                    save(className, MemberSignature.fromMethod(descriptor.nameResolver, signature.setter))
                }
                if (signature.hasSyntheticMethod()) {
                    save(className, MemberSignature.fromMethod(descriptor.nameResolver, signature.syntheticMethod))
                }
            }
            if (DescriptorUtils.isAnnotationClass(descriptor.containingDeclaration)) {
//                saveSignature(MemberSignature.fromMethodNameAndDesc(descriptor.name.asString(), "()"))
            }
        }
        return signatures
    }
}

// expose with different type
val BySignatureIndexer: DecompiledTextIndexer<*> = ByJvmSignatureIndexer
