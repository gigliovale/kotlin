import kotlin.test.*

fun <T> eq(expected: T, actual: T): Boolean {
    return when (expected) {
        is BooleanArray -> actual is BooleanArray && actual.size == expected.size && actual.foldIndexed(true) { i, r, v -> r && expected[i] == v }
        is ByteArray -> actual is ByteArray && actual.size == expected.size && actual.foldIndexed(true) { i, r, v -> r && expected[i] == v }
        is ShortArray -> actual is ShortArray && actual.size == expected.size && actual.foldIndexed(true) { i, r, v -> r && expected[i] == v }
        is CharArray -> actual is CharArray && actual.size == expected.size && actual.foldIndexed(true) { i, r, v -> r && expected[i] == v }
        is IntArray -> actual is IntArray && actual.size == expected.size && actual.foldIndexed(true) { i, r, v -> r && expected[i] == v }
        is LongArray -> actual is LongArray && actual.size == expected.size && actual.foldIndexed(true) { i, r, v -> r && expected[i] == v }
        is FloatArray -> actual is FloatArray && actual.size == expected.size && actual.foldIndexed(true) { i, r, v -> r && expected[i] == v }
        is DoubleArray -> actual is DoubleArray && actual.size == expected.size && actual.foldIndexed(true) { i, r, v -> r && expected[i] == v }
        is Array<*> -> actual is Array<*> && actual.size == expected.size && actual.foldIndexed(true) { i, r, v -> r && expected[i] == v }
        else -> false
    }
}

fun customBooleanArrayOf(vararg arr: Boolean): BooleanArray = arr
fun customByteArrayOf(vararg arr: Byte): ByteArray = arr
fun customShortArrayOf(vararg arr: Short): ShortArray = arr
fun customCharArrayOf(vararg arr: Char): CharArray = arr
fun customIntArrayOf(vararg arr: Int): IntArray = arr
fun customFloatArrayOf(vararg arr: Float): FloatArray = arr
fun customDoubleArrayOf(vararg arr: Double): DoubleArray = arr
fun customLongArrayOf(vararg arr: Long): LongArray = arr

fun box(): String {

    assertTrue(booleanArrayOf(false) is BooleanArray)
    assertTrue(eq(booleanArrayOf(false), BooleanArray(1)))
    assertTrue(eq(booleanArrayOf(false, true, false), BooleanArray(3) { it % 2 != 0 }))
    assertTrue(eq(booleanArrayOf(true), booleanArrayOf(true).copyOf()))
    assertTrue(eq(booleanArrayOf(true, false), booleanArrayOf(true).copyOf(2)))
    assertTrue(eq(booleanArrayOf(true), booleanArrayOf(true, true).copyOf(1)))
    assertTrue(eq(booleanArrayOf(false, true), booleanArrayOf(false) + true))
    assertTrue(eq(booleanArrayOf(false, true, false), booleanArrayOf(false) + listOf(true, false)))
    assertTrue(eq(booleanArrayOf(true, false), booleanArrayOf(false, true, false, true).copyOfRange(1, 3)))
    assertTrue(eq(booleanArrayOf(false, true, false, true), customBooleanArrayOf(false, *booleanArrayOf(true, false), true)))
    assertTrue(booleanArrayOf(true).iterator() is BooleanIterator)
    assertEquals(true, booleanArrayOf(true).iterator().nextBoolean())
    assertEquals(true, booleanArrayOf(true).iterator().next())
    assertFalse(booleanArrayOf().iterator().hasNext())
    assertTrue(assertFails { booleanArrayOf().iterator().next() } is IndexOutOfBoundsException)

    assertTrue(byteArrayOf(0) is ByteArray)
    assertTrue(eq(byteArrayOf(0), ByteArray(1)))
    assertTrue(eq(byteArrayOf(1, 2, 3), ByteArray(3) { (it + 1).toByte() }))
    assertTrue(eq(byteArrayOf(1), byteArrayOf(1).copyOf()))
    assertTrue(eq(byteArrayOf(1, 0), byteArrayOf(1).copyOf(2)))
    assertTrue(eq(byteArrayOf(1), byteArrayOf(1, 2).copyOf(1)))
    assertTrue(eq(byteArrayOf(1, 2), byteArrayOf(1) + 2))
    assertTrue(eq(byteArrayOf(1, 2, 3), byteArrayOf(1) + listOf(2.toByte(), 3.toByte())))
    assertTrue(eq(byteArrayOf(2, 3), byteArrayOf(1, 2, 3, 4).copyOfRange(1, 3)))
    assertTrue(eq(byteArrayOf(1, 2, 3, 4), customByteArrayOf(1.toByte(), *byteArrayOf(2, 3), 4.toByte())))
    assertTrue(byteArrayOf(1).iterator() is ByteIterator)
    assertEquals(1, byteArrayOf(1).iterator().nextByte())
    assertEquals(1, byteArrayOf(1).iterator().next())
    assertFalse(byteArrayOf().iterator().hasNext())
    assertTrue(assertFails { byteArrayOf().iterator().next() } is IndexOutOfBoundsException)

    assertTrue(shortArrayOf(0) is ShortArray)
    assertTrue(eq(shortArrayOf(0), ShortArray(1)))
    assertTrue(eq(shortArrayOf(1, 2, 3), ShortArray(3) { (it + 1).toShort() }))
    assertTrue(eq(shortArrayOf(1), shortArrayOf(1).copyOf()))
    assertTrue(eq(shortArrayOf(1, 0), shortArrayOf(1).copyOf(2)))
    assertTrue(eq(shortArrayOf(1), shortArrayOf(1, 2).copyOf(1)))
    assertTrue(eq(shortArrayOf(1, 2), shortArrayOf(1) + 2))
    assertTrue(eq(shortArrayOf(1, 2, 3), shortArrayOf(1) + listOf(2.toShort(), 3.toShort())))
    assertTrue(eq(shortArrayOf(2, 3), shortArrayOf(1, 2, 3, 4).copyOfRange(1, 3)))
    assertTrue(eq(shortArrayOf(1, 2, 3, 4), customShortArrayOf(1.toShort(), *shortArrayOf(2, 3), 4.toShort())))
    assertTrue(shortArrayOf(1).iterator() is ShortIterator)
    assertEquals(1, shortArrayOf(1).iterator().nextShort())
    assertEquals(1, shortArrayOf(1).iterator().next())
    assertFalse(shortArrayOf().iterator().hasNext())
    assertTrue(assertFails { shortArrayOf().iterator().next() } is IndexOutOfBoundsException)

    assertTrue(charArrayOf('a') is CharArray)
    assertTrue(eq(charArrayOf(0.toChar()), CharArray(1)))
    assertTrue(eq(charArrayOf('a', 'b', 'c'), CharArray(3) { 'a' + it }))
    assertTrue(eq(charArrayOf('a'), charArrayOf('a').copyOf()))
    assertTrue(eq(charArrayOf('a', 0.toChar()), charArrayOf('a').copyOf(2)))
    assertTrue(eq(charArrayOf('a'), charArrayOf('a', 'b').copyOf(1)))
    assertTrue(eq(charArrayOf('a', 'b'), charArrayOf('a') + 'b'))
    assertTrue(eq(charArrayOf('a', 'b', 'c'), charArrayOf('a') + listOf('b', 'c')))
    assertTrue(eq(charArrayOf('b', 'c'), charArrayOf('a', 'b', 'c', 'd').copyOfRange(1, 3)))
    assertTrue(eq(charArrayOf('a', 'b', 'c', 'd'), customCharArrayOf('a', *charArrayOf('b', 'c'), 'd')))
    assertTrue(charArrayOf('a').iterator() is CharIterator)
    assertEquals('a', charArrayOf('a').iterator().nextChar())
    assertEquals('a', charArrayOf('a').iterator().next())
    assertFalse(charArrayOf().iterator().hasNext())
    assertTrue(assertFails { charArrayOf().iterator().next() } is IndexOutOfBoundsException)

    assertTrue(intArrayOf(0) is IntArray)
    assertTrue(eq(intArrayOf(0), IntArray(1)))
    assertTrue(eq(intArrayOf(1, 2, 3), IntArray(3) { it + 1 }))
    assertTrue(eq(intArrayOf(1), intArrayOf(1).copyOf()))
    assertTrue(eq(intArrayOf(1, 0), intArrayOf(1).copyOf(2)))
    assertTrue(eq(intArrayOf(1), intArrayOf(1, 2).copyOf(1)))
    assertTrue(eq(intArrayOf(1, 2), intArrayOf(1) + 2))
    assertTrue(eq(intArrayOf(1, 2, 3), intArrayOf(1) + listOf(2, 3)))
    assertTrue(eq(intArrayOf(2, 3), intArrayOf(1, 2, 3, 4).copyOfRange(1, 3)))
    assertTrue(eq(intArrayOf(1, 2, 3, 4), customIntArrayOf(1, *intArrayOf(2, 3), 4)))
    assertTrue(intArrayOf(1).iterator() is IntIterator)
    assertEquals(1, intArrayOf(1).iterator().nextInt())
    assertEquals(1, intArrayOf(1).iterator().next())
    assertFalse(intArrayOf().iterator().hasNext())
    assertTrue(assertFails { intArrayOf().iterator().next() } is IndexOutOfBoundsException)

    assertTrue(floatArrayOf(0f) is FloatArray)
    assertTrue(eq(floatArrayOf(0f), FloatArray(1)))
    assertTrue(eq(floatArrayOf(1f, 2f, 3f), FloatArray(3) { (it + 1).toFloat() }))
    assertTrue(eq(floatArrayOf(1f), floatArrayOf(1f).copyOf()))
    assertTrue(eq(floatArrayOf(1f, 0f), floatArrayOf(1f).copyOf(2)))
    assertTrue(eq(floatArrayOf(1f), floatArrayOf(1f, 2f).copyOf(1)))
    assertTrue(eq(floatArrayOf(1f, 2f), floatArrayOf(1f) + 2f))
    assertTrue(eq(floatArrayOf(1f, 2f, 3f), floatArrayOf(1f) + listOf(2f, 3f)))
    assertTrue(eq(floatArrayOf(2f, 3f), floatArrayOf(1f, 2f, 3f, 4f).copyOfRange(1, 3)))
    assertTrue(eq(floatArrayOf(1f, 2f, 3f, 4f), customFloatArrayOf(1f, *floatArrayOf(2f, 3f), 4f)))
    assertTrue(floatArrayOf(1f).iterator() is FloatIterator)
    assertEquals(1f, floatArrayOf(1f).iterator().nextFloat())
    assertEquals(1f, floatArrayOf(1f).iterator().next())
    assertFalse(floatArrayOf().iterator().hasNext())
    assertTrue(assertFails { floatArrayOf().iterator().next() } is IndexOutOfBoundsException)

    assertTrue(doubleArrayOf(0.0) is DoubleArray)
    assertTrue(eq(doubleArrayOf(0.0), DoubleArray(1)))
    assertTrue(eq(doubleArrayOf(1.0, 2.0, 3.0), DoubleArray(3) { (it + 1).toDouble() }))
    assertTrue(eq(doubleArrayOf(1.0), doubleArrayOf(1.0).copyOf()))
    assertTrue(eq(doubleArrayOf(1.0, 0.0), doubleArrayOf(1.0).copyOf(2)))
    assertTrue(eq(doubleArrayOf(1.0), doubleArrayOf(1.0, 2.0).copyOf(1)))
    assertTrue(eq(doubleArrayOf(1.0, 2.0), doubleArrayOf(1.0) + 2.0))
    assertTrue(eq(doubleArrayOf(1.0, 2.0, 3.0), doubleArrayOf(1.0) + listOf(2.0, 3.0)))
    assertTrue(eq(doubleArrayOf(2.0, 3.0), doubleArrayOf(1.0, 2.0, 3.0, 4.0).copyOfRange(1, 3)))
    assertTrue(eq(doubleArrayOf(1.0, 2.0, 3.0, 4.0), customDoubleArrayOf(1.0, *doubleArrayOf(2.0, 3.0), 4.0)))
    assertTrue(doubleArrayOf(1.0).iterator() is DoubleIterator)
    assertEquals(1.0, doubleArrayOf(1.0).iterator().nextDouble())
    assertEquals(1.0, doubleArrayOf(1.0).iterator().next())
    assertFalse(doubleArrayOf().iterator().hasNext())
    assertTrue(assertFails { doubleArrayOf().iterator().next() } is IndexOutOfBoundsException)

    assertTrue(longArrayOf(0) is LongArray)
    assertTrue(eq(longArrayOf(0), LongArray(1)))
    assertTrue(eq(longArrayOf(1, 2, 3), LongArray(3) { it + 1L }))
    assertTrue(eq(longArrayOf(1), longArrayOf(1).copyOf()))
    assertTrue(eq(longArrayOf(1, 0), longArrayOf(1).copyOf(2)))
    assertTrue(eq(longArrayOf(1), longArrayOf(1, 2).copyOf(1)))
    assertTrue(eq(longArrayOf(1, 2), longArrayOf(1) + 2))
    assertTrue(eq(longArrayOf(1, 2, 3), longArrayOf(1) + listOf(2L, 3L)))
    assertTrue(eq(longArrayOf(2, 3), longArrayOf(1, 2, 3, 4).copyOfRange(1, 3)))
    assertTrue(eq(longArrayOf(1, 2, 3, 4), customLongArrayOf(1L, *longArrayOf(2, 3), 4L)))
    assertTrue(longArrayOf(1).iterator() is LongIterator)
    assertEquals(1L, longArrayOf(1).iterator().nextLong())
    assertEquals(1L, longArrayOf(1).iterator().next())
    assertFalse(longArrayOf().iterator().hasNext())
    assertTrue(assertFails { longArrayOf().iterator().next() } is IndexOutOfBoundsException)

    return "OK"
}