// !DIAGNOSTICS: -UNUSED_PARAMETER -UNUSED_EXPRESSION -UNREACHABLE_CODE -UNUSED_VARIABLE -WRONG_ANNOTATION_TARGET -UNUSED_LAMBDA_EXPRESSION

// FILE: 1.kt

annotation class <!YIELD_IS_RESERVED!>yield<!>

fun bar(p: Int) {
    <!UNSUPPORTED, YIELD_IS_RESERVED!>yield<!>@ p
    `yield`@ p

    @<!UNSUPPORTED!>yield<!>() p
    @`yield`() p

    for (<!YIELD_IS_RESERVED!>yield<!> in 1..5) {

    }
    { <!YIELD_IS_RESERVED!>yield<!>: Int -> }

    val (<!YIELD_IS_RESERVED!>yield<!>) = listOf(4)

}

fun <T> listOf(vararg e: T): List<T> = null!!
operator fun <T> List<T>.component1() = get(0)

// FILE: 2.kt
package p3

enum class <!YIELD_IS_RESERVED!>yield<!> {
    <!YIELD_IS_RESERVED!>yield<!>
}

fun f1(<!YIELD_IS_RESERVED!>yield<!>: Int, foo: Int = <!UNSUPPORTED!>yield<!>) {}

fun f2(foo: <!UNSUPPORTED!>yield<!>) {}

// FILE: 3.kt
package p4

typealias <!YIELD_IS_RESERVED!>yield<!> = Number

fun <<!YIELD_IS_RESERVED!>yield<!>: Number> f1() {}
fun <y: <!UNSUPPORTED!>yield<!>> f2() {}

