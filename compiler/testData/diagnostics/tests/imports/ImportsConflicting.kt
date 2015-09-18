//FILE:a.kt
package a

import b.foo
import c.<!NAME_ALREADY_IMPORTED!>foo<!>

//FILE:b.kt
package b

fun foo() = 2

//FILE:c.kt
package c

fun foo() = 1