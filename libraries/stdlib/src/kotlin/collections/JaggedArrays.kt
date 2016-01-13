@file:kotlin.jvm.JvmName("JaggedArraysKt")
package kotlin

/**
 * Returns an array of [rows] arrays each having [cols] elements initialized with `null` value.
 */
public inline fun <reified T> jaggedArrayOfNulls(rows: Int, cols: Int): Array<Array<T?>>
        = Array(rows) { arrayOfNulls<T>(cols)}

/**
 * Returns an array of [rows] arrays each having [cols] elements initialized with the result of calling [init] function.
 * The [init] function returns an array element given its indices – row (outer) and col (inner).
 */
public inline fun <reified T> jaggedArray(rows: Int, cols: Int, init: (row: Int, col: Int) -> T): Array<Array<T>>
        = Array<Array<T>>(rows) { row -> Array<T>(cols) { col -> init(row, col) }}

/**
 * Returns an array of [rows] [IntArray] arrays each having [cols] elements initialized with value `0`.
 */
public fun jaggedIntArray(rows: Int, cols: Int): Array<IntArray>
        = Array(rows) { IntArray(cols) }

/**
 * Returns an array of [rows] [IntArray] arrays each having [cols] elements initialized with the result of calling [init] function.
 * The [init] function returns an array element given its indices – row (outer) and col (inner).
 */
public inline fun jaggedIntArray(rows: Int, cols: Int, init: (row: Int, col: Int) -> Int): Array<IntArray>
        = Array(rows) { row -> IntArray(cols) { col -> init(row, col) }}


/**
 * Returns an array of [rows] [LongArray] arrays each having [cols] elements initialized with value `0L`.
 */
public fun jaggedLongArray(rows: Int, cols: Int): Array<LongArray>
        = Array(rows) { LongArray(cols) }

/**
 * Returns an array of [rows] [LongArray] arrays each having [cols] elements initialized with the result of calling [init] function.
 * The [init] function returns an array element given its indices – row (outer) and col (inner).
 */
public inline fun jaggedLongArray(rows: Int, cols: Int, init: (row: Int, col: Int) -> Long): Array<LongArray>
        = Array(rows) { row -> LongArray(cols) { col -> init(row, col) }}


/**
 * Returns an array of [rows] [ShortArray] arrays each having [cols] elements initialized with value `0.toShort()`.
 */
public fun jaggedShortArray(rows: Int, cols: Int): Array<ShortArray>
        = Array(rows) { ShortArray(cols) }

/**
 * Returns an array of [rows] [ShortArray] arrays each having [cols] elements initialized with the result of calling [init] function.
 * The [init] function returns an array element given its indices – row (outer) and col (inner).
 */
public inline fun jaggedShortArray(rows: Int, cols: Int, init: (row: Int, col: Int) -> Short): Array<ShortArray>
        = Array(rows) { row -> ShortArray(cols) { col -> init(row, col) }}

/**
 * Returns an array of [rows] [ByteArray] arrays each having [cols] elements initialized with value `0.toByte()`.
 */
public fun jaggedByteArray(rows: Int, cols: Int): Array<ByteArray>
        = Array(rows) { ByteArray(cols) }

/**
 * Returns an array of [rows] [ByteArray] arrays each having [cols] elements initialized with the result of calling [init] function.
 * The [init] function returns an array element given its indices – row (outer) and col (inner).
 */
public inline fun jaggedByteArray(rows: Int, cols: Int, init: (row: Int, col: Int) -> Byte): Array<ByteArray>
        = Array(rows) { row -> ByteArray(cols) { col -> init(row, col) }}

/**
 * Returns an array of [rows] [FloatArray] arrays each having [cols] elements initialized with value `0.0F`.
 */
public fun jaggedFloatArray(rows: Int, cols: Int): Array<FloatArray>
        = Array(rows) { FloatArray(cols) }

/**
 * Returns an array of [rows] [FloatArray] arrays each having [cols] elements initialized with the result of calling [init] function.
 * The [init] function returns an array element given its indices – row (outer) and col (inner).
 */
public inline fun jaggedFloatArray(rows: Int, cols: Int, init: (row: Int, col: Int) -> Float): Array<FloatArray>
        = Array(rows) { row -> FloatArray(cols) { col -> init(row, col) }}

/**
 * Returns an array of [rows] [DoubleArray] arrays each having [cols] elements initialized with value `0.0`.
 */
public fun jaggedDoubleArray(rows: Int, cols: Int): Array<DoubleArray>
        = Array(rows) { DoubleArray(cols) }

/**
 * Returns an array of [rows] [DoubleArray] arrays each having [cols] elements initialized with the result of calling [init] function.
 * The [init] function returns an array element given its indices – row (outer) and col (inner).
 */
public inline fun jaggedDoubleArray(rows: Int, cols: Int, init: (row: Int, col: Int) -> Double): Array<DoubleArray>
        = Array(rows) { row -> DoubleArray(cols) { col -> init(row, col) }}

/**
 * Returns an array of [rows] [BooleanArray] arrays each having [cols] elements initialized with value `false`.
 */
public fun jaggedBooleanArray(rows: Int, cols: Int): Array<BooleanArray>
        = Array(rows) { BooleanArray(cols) }

/**
 * Returns an array of [rows] [BooleanArray] arrays each having [cols] elements initialized with the result of calling [init] function.
 * The [init] function returns an array element given its indices – row (outer) and col (inner).
 */
public inline fun jaggedBooleanArray(rows: Int, cols: Int, init: (row: Int, col: Int) -> Boolean): Array<BooleanArray>
        = Array(rows) { row -> BooleanArray(cols) { col -> init(row, col) }}
