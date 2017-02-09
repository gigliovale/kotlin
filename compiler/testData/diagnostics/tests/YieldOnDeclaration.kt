// !DIAGNOSTICS: -UNUSED_PARAMETER -UNUSED_EXPRESSION -UNREACHABLE_CODE -UNUSED_VARIABLE -UNUSED_LAMBDA_EXPRESSION

// FILE: 1.kt
package p1

val <!YIELD_IS_RESERVED!>yield<!> = 3

fun yield(<!YIELD_IS_RESERVED!>yield<!>: Int) {}

// FILE: 2.kt
package p2

class <!YIELD_IS_RESERVED!>yield<!> {
    object <!YIELD_IS_RESERVED!>yield<!> {
        annotation class <!YIELD_IS_RESERVED!>yield<!>
    }
}

// FILE: 3.kt
package p3

enum class <!YIELD_IS_RESERVED!>yield<!> {
    <!YIELD_IS_RESERVED!>yield<!>
}

// FILE: 4.kt

fun test() {
    val <!YIELD_IS_RESERVED!>yield<!> = 4
    { <!YIELD_IS_RESERVED!>yield<!>: Int -> }

}

fun test2() {
    val (<!YIELD_IS_RESERVED!>yield<!>) = 5

    fun yield() {}
}

operator fun Int.component1() = 3