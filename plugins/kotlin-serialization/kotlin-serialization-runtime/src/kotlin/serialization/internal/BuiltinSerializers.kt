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

package kotlin.serialization.internal

import kotlin.reflect.KClass
import kotlin.serialization.KInput
import kotlin.serialization.KOutput
import kotlin.serialization.KSerializer

object UnitClassDesc : SerialClassDescImpl("kotlin.Unit")

object UnitSerializer : KSerializer<Unit> {
    override val serializableClass: KClass<*> = Unit::class
    override fun save(output: KOutput, obj: Unit) = output.writeUnitValue()
    override fun load(input: KInput): Unit = input.readUnitValue()
}

object BooleanSerializer : KSerializer<Boolean> {
    override val serializableClass: KClass<*> = Boolean::class
    override fun save(output: KOutput, obj: Boolean) = output.writeBooleanValue(obj)
    override fun load(input: KInput): Boolean = input.readBooleanValue()
}

object ByteSerializer : KSerializer<Byte> {
    override val serializableClass: KClass<*> = Byte::class
    override fun save(output: KOutput, obj: Byte) = output.writeByteValue(obj)
    override fun load(input: KInput): Byte = input.readByteValue()
}

object ShortSerializer : KSerializer<Short> {
    override val serializableClass: KClass<*> = Short::class
    override fun save(output: KOutput, obj: Short) = output.writeShortValue(obj)
    override fun load(input: KInput): Short = input.readShortValue()
}

object IntSerializer : KSerializer<Int> {
    override val serializableClass: KClass<*> = Int::class
    override fun save(output: KOutput, obj: Int) = output.writeIntValue(obj)
    override fun load(input: KInput): Int = input.readIntValue()
}

object LongSerializer : KSerializer<Long> {
    override val serializableClass: KClass<*> = Long::class
    override fun save(output: KOutput, obj: Long) = output.writeLongValue(obj)
    override fun load(input: KInput): Long = input.readLongValue()
}

object FloatSerializer : KSerializer<Float> {
    override val serializableClass: KClass<*> = Float::class
    override fun save(output: KOutput, obj: Float) = output.writeFloatValue(obj)
    override fun load(input: KInput): Float = input.readFloatValue()
}

object DoubleSerializer : KSerializer<Double> {
    override val serializableClass: KClass<*> = Double::class
    override fun save(output: KOutput, obj: Double) = output.writeDoubleValue(obj)
    override fun load(input: KInput): Double = input.readDoubleValue()
}

object CharSerializer : KSerializer<Char> {
    override val serializableClass: KClass<*> = Char::class
    override fun save(output: KOutput, obj: Char) = output.writeCharValue(obj)
    override fun load(input: KInput): Char = input.readCharValue()
}

object StringSerializer : KSerializer<String> {
    override val serializableClass: KClass<*> = String::class
    override fun save(output: KOutput, obj: String) = output.writeStringValue(obj)
    override fun load(input: KInput): String = input.readStringValue()
}

// note, that it is instantiated in a special way
class EnumSerializer<T : Enum<T>>(override val serializableClass: KClass<T>) : KSerializer<T> {
    override fun save(output: KOutput, obj: T) = output.writeEnumValue(serializableClass, obj)
    override fun load(input: KInput): T = input.readEnumValue(serializableClass)
}

fun <T : Any> makeNullable(element: KSerializer<T>): KSerializer<T?> = NullableSerializer(element)

class NullableSerializer<T : Any>(private val element: KSerializer<T>) : KSerializer<T?> {
    override val serializableClass: KClass<*> = element.serializableClass

    override fun save(output: KOutput, obj: T?) {
        if (obj != null) {
            output.writeNotNullMark();
            element.save(output, obj)
        } else {
            output.writeNullValue();
        }
    }

    override fun load(input: KInput): T? = if (input.readNotNullMark()) element.load(input) else input.readNullValue()
}

