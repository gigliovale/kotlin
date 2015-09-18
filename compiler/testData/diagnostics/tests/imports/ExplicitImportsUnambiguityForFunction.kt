// FILE: a.kt
package a

fun X(<!UNUSED_PARAMETER!>p<!>: Int) {}

// FILE: b.kt
package b

fun X(): Int = 1

// FILE: c.kt
package c

import b.X
import a.<!NAME_ALREADY_IMPORTED!>X<!>

fun foo() {
    val <!UNUSED_VARIABLE!>v<!>: Int = X()
}