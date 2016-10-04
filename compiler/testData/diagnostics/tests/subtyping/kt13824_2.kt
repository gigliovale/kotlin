// !DIAGNOSTICS: -UNNECESSARY_SAFE_CALL

// MODULE: m1
// FILE: b.kt
package p

class Foo<T>
fun createFoo2() = Foo<Int>()

// MODULE: m2
// FILE: a.kt
package p

class Foo<X, Y>
fun createFoo1() = Foo<Int, Int>()

// MODULE: m3(m1, m2)
// FILE: c.kt
package q

import p.*

fun test(c: Boolean) = if (c) <!IMPLICIT_CAST_TO_ANY!>createFoo2()<!> else <!IMPLICIT_CAST_TO_ANY!>createFoo1()<!>
fun test2(c: Boolean) = <!TYPE_INFERENCE_FAILED_ON_SPECIAL_CONSTRUCT!>if<!> (c) <!TYPE_MISMATCH!>createFoo1()<!> else createFoo2()
