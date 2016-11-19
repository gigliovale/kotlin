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

package org.jetbrains.kotlin.kserialization.backend.jvm

import org.jetbrains.kotlin.builtins.KotlinBuiltIns
import org.jetbrains.kotlin.codegen.*
import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.kserialization.backend.common.SerializerCodegen
import org.jetbrains.kotlin.kserialization.resolve.SerializableProperty
import org.jetbrains.kotlin.kserialization.resolve.getSerializableClassDescriptorBySerializer
import org.jetbrains.kotlin.kserialization.resolve.toClassDescriptor
import org.jetbrains.kotlin.kserialization.resolve.typeSerializer
import org.jetbrains.kotlin.load.kotlin.TypeMappingMode
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.resolve.descriptorUtil.classId
import org.jetbrains.kotlin.resolve.jvm.AsmTypes
import org.jetbrains.kotlin.resolve.jvm.diagnostics.OtherOrigin
import org.jetbrains.kotlin.resolve.jvm.jvmSignature.JvmMethodSignature
import org.jetbrains.kotlin.serialization.deserialization.findClassAcrossModuleDependencies
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.org.objectweb.asm.Label
import org.jetbrains.org.objectweb.asm.Opcodes.*
import org.jetbrains.org.objectweb.asm.Type
import org.jetbrains.org.objectweb.asm.commons.InstructionAdapter

class SerializerCodegenImpl(
        private val codegen: ImplementationBodyCodegen,
        serializableClass: ClassDescriptor
) : SerializerCodegen(codegen.myClass, codegen.bindingContext) {
    private val internalPackageFqName = FqName("kotlin.serialization.internal")
    private val descType = Type.getObjectType("kotlin/serialization/KSerialClassDesc")
    private val descImplType = Type.getObjectType("kotlin/serialization/internal/SerialClassDescImpl")
    private val kOutputType = Type.getObjectType("kotlin/serialization/KOutput")
    private val kInputType = Type.getObjectType("kotlin/serialization/KInput")
    private val kSerialSaverType = Type.getObjectType("kotlin/serialization/KSerialSaver")
    private val kSerialLoaderType = Type.getObjectType("kotlin/serialization/KSerialLoader")
    private val kSerializerType = Type.getObjectType("kotlin/serialization/KSerializer")
    private val kSerializerArrayType = Type.getObjectType("[Lkotlin/serialization/KSerializer;")

    private val serialDescField = "\$\$serialDesc"

    private val enumSerializerId = ClassId(internalPackageFqName, Name.identifier("EnumSerializer"))
    private val referenceArraySerializerId = ClassId(internalPackageFqName, Name.identifier("ReferenceArraySerializer"))

    private val serializerAsmType = codegen.typeMapper.mapClass(codegen.descriptor)
    private val serializableAsmType = codegen.typeMapper.mapClass(serializableClass)

    companion object {
        fun generateSerializerExtensions(codegen: ImplementationBodyCodegen) {
            val serializableClass = getSerializableClassDescriptorBySerializer(codegen.descriptor) ?: return
            SerializerCodegenImpl(codegen, serializableClass).generate()
        }
    }

    override fun generateSerialDesc() {
        codegen.v.newField(OtherOrigin(codegen.myClass), ACC_PRIVATE or ACC_STATIC or ACC_FINAL or ACC_SYNTHETIC,
                           serialDescField, descType.descriptor, null, null)
        // todo: lazy initialization of $$serialDesc that is performed only when save/load is invoked first time
        with(codegen.createOrGetClInitCodegen().v) {
            val classDescVar = 0
            anew(descImplType)
            dup()
            aconst(serialName)
            invokespecial(descImplType.internalName, "<init>", "(Ljava/lang/String;)V", false)
            store(classDescVar, descImplType)
            for (property in properties) {
                load(classDescVar, descImplType)
                aconst(property.name)
                invokevirtual(descImplType.internalName, "addElement", "(Ljava/lang/String;)V", false)
            }
            load(classDescVar, descImplType)
            putstatic(serializerAsmType.internalName, serialDescField, descType.descriptor)
        }
    }

    private fun InstructionAdapter.serialCLassDescToLocalVar(classDescVar: Int) {
        getstatic(serializerAsmType.internalName, serialDescField, descType.descriptor)
        store(classDescVar, descType)
    }

    // helper
    private fun generateMethod(function: FunctionDescriptor,
                               block: InstructionAdapter.(JvmMethodSignature, ExpressionCodegen) -> Unit) {
        codegen.functionCodegen.generateMethod(OtherOrigin(codegen.myClass, function), function,
               object : FunctionGenerationStrategy.CodegenBased(codegen.state) {
                   override fun doGenerateBody(codegen: ExpressionCodegen, signature: JvmMethodSignature) {
                       codegen.v.block(signature, codegen)
                   }
               })
    }

    override fun generateSerializableClassProperty(property: PropertyDescriptor) {
        // todo: store it into static field?
        generateMethod(property.getter!!) { signature, expressionCodegen ->
            aconst(serializableAsmType)
            AsmUtil.wrapJavaClassIntoKClass(this)
            areturn(AsmTypes.K_CLASS_TYPE)
        }
    }

    override fun generateSave(
            function: FunctionDescriptor
    ) {
        generateMethod(function) { signature, expressionCodegen ->
            // fun save(output: KOutput, obj : T)
            val outputVar = 1
            val objVar = 2
            val descVar = 3
            serialCLassDescToLocalVar(descVar)
            val objType = signature.valueParameters[1].asmType
            // output = output.writeBegin(classDesc, new KSerializer[0])
            load(outputVar, kOutputType)
            load(descVar, descType)
            iconst(0)
            newarray(kSerializerType) // todo: use some predefined empty array
            invokevirtual(kOutputType.internalName, "writeBegin",
                               "(" + descType.descriptor + kSerializerArrayType.descriptor +
                               ")" + kOutputType.descriptor, false)
            store(outputVar, kOutputType)
            // loop for all properties
            for (index in properties.indices) {
                val property = properties[index]
                // output.writeXxxElementValue(classDesc, index, value)
                load(outputVar, kOutputType)
                load(descVar, descType)
                iconst(index)
                val propertyType = codegen.typeMapper.mapType(property.type)
                val sti = getSerialTypeInfo(property, propertyType)
                val useSerializer = stackValueSerializerInstance(sti)
                if (!sti.unit) codegen.genPropertyOnStack(this, expressionCodegen.context, property.descriptor, objType, objVar)
                invokevirtual(kOutputType.internalName,
                                   "write" + sti.nn + (if (useSerializer) "Serializable" else "") + "ElementValue",
                                   "(" + descType.descriptor + "I" +
                                   (if (useSerializer) kSerialSaverType.descriptor else "") +
                                   (if (sti.unit) "" else sti.type.descriptor) + ")V", false)
            }
            // output.writeEnd(classDesc)
            load(outputVar, kOutputType)
            load(descVar, descType)
            invokevirtual(kOutputType.internalName, "writeEnd",
                               "(" + descType.descriptor + ")V", false)
            // return
            areturn(Type.VOID_TYPE)
        }
    }

    override fun generateLoad(
            function: FunctionDescriptor
    ) {
        generateMethod(function) { signature, expressionCodegen ->
            // fun load(input: KInput): T
            val inputVar = 1
            val descVar = 2
            val indexVar = 3
            val readAllVar = 4
            val propsStartVar = 5
            serialCLassDescToLocalVar(descVar)
            // boolean readAll = false
            iconst(0)
            store(readAllVar, Type.BOOLEAN_TYPE)
            // initialize all prop vars
            var propVar = propsStartVar
            for (property in properties) {
                val propertyType = codegen.typeMapper.mapType(property.type)
                stackValueDefault(propertyType)
                store(propVar, propertyType)
                propVar += propertyType.size
            }
            // input = input.readBegin(classDesc, new KSerializer[0])
            load(inputVar, kInputType)
            load(descVar, descType)
            iconst(0)
            newarray(kSerializerType) // todo: use some predefined empty array
            invokevirtual(kInputType.internalName, "readBegin",
                               "(" + descType.descriptor + kSerializerArrayType.descriptor +
                               ")" + kInputType.descriptor, false)
            store(inputVar, kInputType)
            // readElement: int index = input.readElement(classDesc)
            val readElementLabel = Label()
            visitLabel(readElementLabel)
            load(inputVar, kInputType)
            load(descVar, descType)
            invokevirtual(kInputType.internalName, "readElement",
                               "(" + descType.descriptor + ")I", false)
            store(indexVar, Type.INT_TYPE)
            // switch(index)
            val readAllLabel = Label()
            val readEndLabel = Label()
            val labels = arrayOfNulls<Label>(properties.size + 2)
            labels[0] = readAllLabel // READ_ALL
            labels[1] = readEndLabel // READ_DONE
            for (i in properties.indices) {
                labels[i + 2] = Label()
            }
            load(indexVar, Type.INT_TYPE)
            // todo: readEnd is currently default, should probably throw exception instead
            tableswitch(-2, properties.size - 1, readEndLabel, *labels)
            // readAll: readAll := true
            visitLabel(readAllLabel)
            iconst(1)
            store(readAllVar, Type.BOOLEAN_TYPE)
            // loop for all properties
            propVar = propsStartVar
            for (i in properties.indices) {
                val property = properties[i]
                // labelI: propX := input.readXxxValue(value)
                visitLabel(labels[i + 2])
                load(inputVar, kInputType)
                load(descVar, descType)
                iconst(i)
                val propertyType = codegen.typeMapper.mapType(property.type)
                val sti = getSerialTypeInfo(property, propertyType)
                val useSerializer = stackValueSerializerInstance(sti)
                invokevirtual(kInputType.internalName,
                                   "read" + sti.nn + (if (useSerializer) "Serializable" else "") + "ElementValue",
                                   "(" + descType.descriptor + "I" +
                                   (if (useSerializer) kSerialLoaderType.descriptor else "")
                                   + ")" + (if (sti.unit) "V" else sti.type.descriptor), false)
                if (sti.unit) {
                    StackValue.putUnitInstance(this)
                } else {
                    StackValue.coerce(sti.type, propertyType, this)
                }
                store(propVar, propertyType)
                propVar += propertyType.size
                // if (readAll == false) goto readElement
                load(readAllVar, Type.BOOLEAN_TYPE)
                iconst(0)
                ificmpeq(readElementLabel)
            }
            val resultVar = propVar
            // readEnd: input.readEnd(classDesc)
            visitLabel(readEndLabel)
            load(inputVar, kInputType)
            load(descVar, descType)
            invokevirtual(kInputType.internalName, "readEnd",
                               "(" + descType.descriptor + ")V", false)
            // create object with constructor
            anew(serializableAsmType)
            dup()
            val constructorDesc = StringBuilder("(")
            propVar = propsStartVar
            for (property in properties.serializableConstructorProperties) {
                val propertyType = codegen.typeMapper.mapType(property.type)
                constructorDesc.append(propertyType.descriptor)
                load(propVar, propertyType)
                propVar += propertyType.size
            }
            constructorDesc.append(")V")
            invokespecial(serializableAsmType.internalName, "<init>", constructorDesc.toString(), false)
            if (!properties.serializableStandaloneProperties.isEmpty()) {
                // result := ... <created object>
                store(resultVar, serializableAsmType)
                // set other properties
                genSetSerializableStandaloneProperties(expressionCodegen, propVar, resultVar)
                // load result
                load(resultVar, serializableAsmType)
                // will return result
            }
            // return
            areturn(serializableAsmType)
        }
    }

    private fun InstructionAdapter.genSetSerializableStandaloneProperties(
            expressionCodegen: ExpressionCodegen, propVarStart: Int, resultVar: Int) {
        var propVar = propVarStart
        for (property in properties.serializableStandaloneProperties) {
            val propertyType = codegen.typeMapper.mapType(property.type)
            expressionCodegen.intermediateValueForProperty(property.descriptor, false, null,
                                                           StackValue.local(resultVar, serializableAsmType)).
                    store(StackValue.local(propVar, propertyType), this)
            propVar += propertyType.size
        }
    }

    // todo: move to StackValue?
    private fun InstructionAdapter.stackValueDefault(type: Type) {
        when (type.sort) {
            Type.BOOLEAN, Type.BYTE, Type.SHORT, Type.CHAR, Type.INT -> iconst(0)
            Type.LONG -> lconst(0)
            Type.FLOAT -> fconst(0f)
            Type.DOUBLE -> dconst(0.0)
            else -> aconst(null)
        }
    }

    // returns false is property should not use serializer
    private fun InstructionAdapter.stackValueSerializerInstance(sti: SerialTypeInfo): Boolean {
        val serializer = sti.serializer ?: return false
        return stackValueSerializerInstance(sti.property.module, sti.property.type, serializer, this)
    }

    // returns false is cannot not use serializer
    //    use iv == null to check only (do not emit serializer onto stack)
    private fun stackValueSerializerInstance(module: ModuleDescriptor, kType: KotlinType, serializer: ClassDescriptor,
                                             iv: InstructionAdapter?): Boolean {
        if (serializer.kind == ClassKind.OBJECT) {
            // singleton serializer -- just get it
            if (iv != null)
                StackValue.singleton(serializer, codegen.typeMapper).put(kSerializerType, iv)
            return true
        }
        // serializer is not singleton object and shall be instantiated
        val argSerializers = kType.arguments.map { projection ->
            // bail out from stackValueSerializerInstance if any type argument is not serializable
            val argSerializer = findTypeSerializer(module, projection.type, codegen.typeMapper.mapType(projection.type)) ?: return false
            // check if it can be properly serialized with its args recursively
            if (!stackValueSerializerInstance(module, projection.type, argSerializer, null))
                return false
            Pair(projection.type, argSerializer)
        }
        // new serializer if needed
        iv?.apply {
            val serializerType = codegen.typeMapper.mapClass(serializer)
            // todo: support static factory methods for serializers for shorter bytecode
            anew(serializerType)
            dup()
            // instantiate all arg serializers on stack
            val signature = StringBuilder("(")
            when (serializer.classId) {
                enumSerializerId -> {
                    // a special way to instantiate enum -- need a enum KClass reference
                    aconst(codegen.typeMapper.mapType(kType))
                    AsmUtil.wrapJavaClassIntoKClass(this)
                    signature.append(AsmTypes.K_CLASS_TYPE.descriptor)
                }
                referenceArraySerializerId -> {
                    // a special way to instantiate reference array serializer -- need an element java.lang.Class reference
                    aconst(codegen.typeMapper.mapType(kType.arguments[0].type, null, TypeMappingMode.GENERIC_ARGUMENT))
                    signature.append("Ljava/lang/Class;")
                }
            }
            // all serializers get arguments with serializers of their generic types
            argSerializers.forEach { (argType, argSerializer) ->
                assert(stackValueSerializerInstance(module, argType, argSerializer, this))
                // wrap into nullable serializer if argType is nullable
                if (argType.isMarkedNullable) {
                    invokestatic("kotlin/serialization/internal/BuiltinSerializersKt", "makeNullable",
                                 "(" + kSerializerType.descriptor + ")" + kSerializerType.descriptor, false)

                }
                signature.append(kSerializerType.descriptor)
            }
            signature.append(")V")
            // invoke constructor
            invokespecial(serializerType.internalName, "<init>", signature.toString(), false)
        }
        return true
    }

    class SerialTypeInfo(
            val property: SerializableProperty,
            val type: Type,
            val nn: String,
            val serializer: ClassDescriptor? = null,
            val unit: Boolean = false
    )

    fun getSerialTypeInfo(property: SerializableProperty, type: Type): SerialTypeInfo {
        when (type.sort) {
            Type.BOOLEAN, Type.BYTE, Type.SHORT, Type.INT, Type.LONG, Type.FLOAT, Type.DOUBLE, Type.CHAR -> {
                val name = type.className
                return SerialTypeInfo(property, type, Character.toUpperCase(name[0]) + name.substring(1))
            }
            Type.ARRAY -> {
                // check for explicit serialization annotation on this property
                var serializer = property.serializer.toClassDescriptor
                if (serializer == null) {
                    // no explicit serializer for this property. Select strategy by element type
                    when (type.elementType.sort) {
                        Type.OBJECT, Type.ARRAY -> {
                            // reference elements
                            serializer = property.module.findClassAcrossModuleDependencies(referenceArraySerializerId)
                        }
                        // primitive elements are not supported yet
                    }
                }
                return SerialTypeInfo(property, Type.getType("Ljava/lang/Object;"),
                                      if (property.type.isMarkedNullable) "Nullable" else "", serializer)
            }
            Type.OBJECT -> {
                // check for explicit serialization annotation on this property
                var serializer = property.serializer.toClassDescriptor
                if (serializer == null) {
                    // no explicit serializer for this property. Check other built in types
                    if (KotlinBuiltIns.isString(property.type))
                        return SerialTypeInfo(property, Type.getType("Ljava/lang/String;"), "String")
                    if (KotlinBuiltIns.isUnit(property.type))
                        return SerialTypeInfo(property, Type.getType("Lkotlin/Unit;"), "Unit", unit = true)
                    // todo: more efficient enum support here, but only for enums that don't define custom serializer
                    // otherwise, it is a serializer for some other type
                    serializer = findTypeSerializer(property.module, property.type, type)
                }
                return SerialTypeInfo(property, Type.getType("Ljava/lang/Object;"),
                                      if (property.type.isMarkedNullable) "Nullable" else "", serializer)
            }
            else -> throw AssertionError() // should not happen
        }
    }

    fun findTypeSerializer(module: ModuleDescriptor, kType: KotlinType, asmType: Type): ClassDescriptor? {
        return kType.typeSerializer.toClassDescriptor // check for serializer defined on the type
               ?: findStandardAsmTypeSerializer(module, asmType) // otherwise see if there is a standard serializer
               ?: findStandardKotlinTypeSerializer(module, kType)
    }

    fun findStandardKotlinTypeSerializer(module: ModuleDescriptor, kType: KotlinType): ClassDescriptor? {
        val classDescriptor = kType.constructor.declarationDescriptor as? ClassDescriptor ?: return null
        return if (classDescriptor.kind == ClassKind.ENUM_CLASS) module.findClassAcrossModuleDependencies(enumSerializerId) else null
    }

    fun findStandardAsmTypeSerializer(module: ModuleDescriptor, asmType: Type): ClassDescriptor? {
        val name = asmType.standardSerializer ?: return null
        return module.findClassAcrossModuleDependencies(ClassId(internalPackageFqName, Name.identifier(name)))
    }

    private val Type.standardSerializer: String? get() = when (this.descriptor) {
        "Lkotlin/Unit;" -> "UnitSerializer"
        "Z", "Ljava/lang/Boolean;" -> "BooleanSerializer"
        "B", "Ljava/lang/Byte;" -> "ByteSerializer"
        "S", "Ljava/lang/Short;" -> "ShortSerializer"
        "I", "Ljava/lang/Integer;" -> "IntSerializer"
        "J", "Ljava/lang/Long;" -> "LongSerializer"
        "F", "Ljava/lang/Float;" -> "FloatSerializer"
        "D", "Ljava/lang/Double;" -> "DoubleSerializer"
        "C", "Ljava/lang/Character;" -> "CharSerializer"
        "Ljava/lang/String;" -> "StringSerializer"
        "Ljava/util/Collection;", "Ljava/util/List;", "Ljava/util/ArrayList;" -> "ArrayListSerializer"
        "Ljava/util/Set;", "Ljava/util/LinkedHashSet;" -> "LinkedHashSetSerializer"
        "Ljava/util/HashSet;" -> "HashSetSerializer"
        "Ljava/util/Map;", "Ljava/util/LinkedHashMap;" -> "LinkedHashMapSerializer"
        "Ljava/util/HashMap;" -> "HashMapSerializer"
        "Ljava/util/Map\$Entry;" -> "MapEntrySerializer"
        else -> null
    }
}
