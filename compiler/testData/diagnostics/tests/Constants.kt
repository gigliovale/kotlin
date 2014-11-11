// !CHECK_TYPE
// !DIAGNOSTICS: -UNUSED_VARIABLE

fun varargByte(vararg v: Byte) = v

fun varargShort(vararg v: Short) = v

fun varargInt(vararg v: Int) = v

fun varargLong(vararg v: Long) = v

fun varargFloat(vararg v: Float) = v

fun varargDouble(vararg v: Double) = v

fun <T> testFun(<!UNUSED_PARAMETER!>p<!>: T) {}

fun test() {
    checkSubtype<Byte>(1)
    checkSubtype<Short>(1)
    checkSubtype<Int>(1)
    checkSubtype<Long>(1)

    checkSubtype<Long>(0x001)
    checkSubtype<Int>(0b001)

    checkSubtype<Double>(0.1)
    checkSubtype<Float>(0.1.toFloat())

    //KT-4749
    val b1: Byte = 0xf0
    val s1: Short = 0xffe
    val i1: Int = 0xffcccccc
    val l1: Long = 0xffffffffffffffff
    val b2: Byte = 0b11100001
    val s2: Short = 0b1110000111100001
    val i2: Int = 0b11100001111000011110000111100001
    val l2: Long = 0b1110000111100001111000011110000111100001111000011110000111100001

    val b3: Byte = <!CONSTANT_EXPECTED_TYPE_MISMATCH!>0x100<!>
    val b4: Byte = <!CONSTANT_EXPECTED_TYPE_MISMATCH!>0xFFF<!>
    val s3: Short = <!CONSTANT_EXPECTED_TYPE_MISMATCH!>0x10000<!>
    val s4: Short = <!CONSTANT_EXPECTED_TYPE_MISMATCH!>0xFFFFF<!>
    val i3: Int = <!CONSTANT_EXPECTED_TYPE_MISMATCH!>0x100000000<!>
    val i4: Int = <!CONSTANT_EXPECTED_TYPE_MISMATCH!>0xFFFFFFFFF<!>
    val l3: Long = <!INT_LITERAL_OUT_OF_RANGE!>0x10000000000000000<!>
    val l4: Long = <!INT_LITERAL_OUT_OF_RANGE!>0xFFFFFFFFFFFFFFFFF<!>

    val b5: Byte = <!CONSTANT_EXPECTED_TYPE_MISMATCH!>0b100000000<!>
    val b6: Byte = <!CONSTANT_EXPECTED_TYPE_MISMATCH!>0b111111111<!>
    val s5: Short = <!CONSTANT_EXPECTED_TYPE_MISMATCH!>0b10000000000000000<!>
    val s6: Short = <!CONSTANT_EXPECTED_TYPE_MISMATCH!>0b100000000000000000000000000000000<!>
    val i5: Int = <!CONSTANT_EXPECTED_TYPE_MISMATCH!>0b111111111111111111111111111111111<!>
    val i6: Int = <!INT_LITERAL_OUT_OF_RANGE!>0b10000000000000000000000000000000000000000000000000000000000000000<!>
    val l5: Long = <!INT_LITERAL_OUT_OF_RANGE!>0b11111111111111111111111111111111111111111111111111111111111111111<!>

    checkSubtype<Double>(1e5)
    checkSubtype<Float>(1e-5.toFloat())

    checkSubtype<Double>(<!CONSTANT_EXPECTED_TYPE_MISMATCH!>1<!>)
    checkSubtype<Float>(<!CONSTANT_EXPECTED_TYPE_MISMATCH!>1<!>)
    
    1 <!CAST_NEVER_SUCCEEDS!>as<!> Byte
    1 <!USELESS_CAST!>as Int<!>
    0xff <!CAST_NEVER_SUCCEEDS!>as<!> Long
    
    1.1 <!CAST_NEVER_SUCCEEDS!>as<!> Int
    checkSubtype<Int>(<!CONSTANT_EXPECTED_TYPE_MISMATCH!>1.1<!>)

    varargByte(0x77, 1, 3, <!CONSTANT_EXPECTED_TYPE_MISMATCH!>200<!>, 0b111)
    varargShort(0x777, 1, 2, 3, <!CONSTANT_EXPECTED_TYPE_MISMATCH!>200000<!>, 0b111)
    varargInt(0x77777777, <!CONSTANT_EXPECTED_TYPE_MISMATCH!>0x7777777777<!>, 1, 2, 3, 2000000000, 0b111)
    varargLong(0x777777777777, 1, 2, 3, 200000, 0b111)
    varargFloat(<!CONSTANT_EXPECTED_TYPE_MISMATCH!>1<!>, <!CONSTANT_EXPECTED_TYPE_MISMATCH!>1.0<!>, <!TYPE_MISMATCH!>-0.1<!>, <!CONSTANT_EXPECTED_TYPE_MISMATCH!>1e4<!>, <!CONSTANT_EXPECTED_TYPE_MISMATCH!>1e-4<!>, <!TYPE_MISMATCH!>-1e4<!>)
    varargDouble(<!CONSTANT_EXPECTED_TYPE_MISMATCH!>1<!>, 1.0, -0.1, 1e4, 1e-4, -1e4)

    testFun(1.0)
    testFun<Float>(<!CONSTANT_EXPECTED_TYPE_MISMATCH!>1.0<!>)
    testFun(1.0.toFloat())
    testFun<Float>(1.0.toFloat())
}