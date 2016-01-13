package test.collections

import kotlin.test.*
import org.junit.Test

class JaggedArraysTest {

    private fun <TArray: Any, V> expectValues(
            rows: TArray.() -> Int, cols: TArray.() -> Int, value: TArray.(Int, Int) -> V,
            vararg testData: Pair<TArray, (Int, Int) -> V>) {
        for ((array, init) in testData) {
            for (row in 0 until array.rows()) {
                for (col in 0 until array.cols()) {
                    assertEquals(init(row, col), array.value(row, col))
                }
            }
        }
    }

    @Test fun jaggedArray() {
        expectValues( { size }, { first().size }, { r, c -> this[r][c] },
                jaggedArray<Int?>(20, 10) { r, c -> (r * 10 + c) } to { r, c -> (r * 10 + c) },
                jaggedArrayOfNulls<Int>(20, 10) to { r, c -> null }
        )
    }

    @Test fun jaggedIntArray() {
        expectValues( { size }, { first().size }, { r, c -> this[r][c] },
                jaggedIntArray(20, 10) { r, c -> r * 10 + c } to { r, c -> r * 10 + c },
                jaggedIntArray(20, 10) to { r, c -> 0 }
        )
    }

    @Test fun jaggedLongArray() {
        expectValues( { size }, { first().size }, { r, c -> this[r][c] },
                jaggedLongArray(20, 10) { r, c -> r * 10L + c } to { r, c -> r * 10L + c },
                jaggedLongArray(20, 10) to { r, c -> 0L }
        )
    }

    @Test fun jaggedShortArray() {
        expectValues( { size }, { first().size }, { r, c -> this[r][c] },
                jaggedShortArray(20, 10) { r, c -> (r * 10 + c).toShort() } to { r, c -> (r * 10 + c).toShort() },
                jaggedShortArray(20, 10) to { r, c -> 0 }
        )
    }

    @Test fun jaggedByteArray() {
        expectValues( { size }, { first().size }, { r, c -> this[r][c] },
                jaggedByteArray(10, 10) { r, c -> (r * 10 + c).toByte() } to { r, c -> (r * 10 + c).toByte() },
                jaggedByteArray(20, 10) to { r, c -> 0 }
        )
    }

    @Test fun jaggedDoubleArray() {
        expectValues( { size }, { first().size }, { r, c -> this[r][c] },
                jaggedDoubleArray(20, 10) { r, c -> r * 10.0 + c } to { r, c ->  r * 10.0 + c },
                jaggedDoubleArray(20, 10) to { r, c -> 0.0 }
        )
    }

    @Test fun jaggedFloatArray() {
        expectValues( { size }, { first().size }, { r, c -> this[r][c] },
                jaggedFloatArray(20, 10) { r, c ->  r * 10.0F + c } to { r, c ->  r * 10.0F + c },
                jaggedFloatArray(20, 10) to { r, c -> 0.0F }
        )
    }
    
    @Test fun jaggedBooleanArray() {
        expectValues( { size }, { first().size }, { r, c -> this[r][c] },
                jaggedBooleanArray(20, 10) { r, c -> (r + c) % 2 == 0 } to { r, c -> (r + c) % 2 == 0 },
                jaggedBooleanArray(20, 10) to { r, c -> false }
        )
    }

}