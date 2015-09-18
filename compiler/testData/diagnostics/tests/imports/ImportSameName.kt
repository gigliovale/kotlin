// FILE: 1.kt
package a
fun foo() {}
class bar

// FILE: 2.kt
package b
val foo = 2
fun bar() {}

// FILE: 3.kt
package c
val foo = 2

class some
class A

// FILE: 4.kt
import a.foo
import b.<!NAME_ALREADY_IMPORTED!>foo<!>
import c.<!NAME_ALREADY_IMPORTED!>foo<!>


import a.bar
import b.<!NAME_ALREADY_IMPORTED!>bar<!>
import a.foo as <!NAME_ALREADY_IMPORTED!>bar<!>


import a.<!UNRESOLVED_REFERENCE!>some<!>
import c.some
import c.<!NAME_ALREADY_IMPORTED!>some<!>
import c.<!NAME_ALREADY_IMPORTED!>some<!>


import a.foo as A
import c.<!NAME_ALREADY_IMPORTED!>A<!>

import a.bar as bas
import a.bar as <!NAME_ALREADY_IMPORTED!>bas<!>

import a.foo as fas
import b.foo as <!NAME_ALREADY_IMPORTED!>fas<!>

import <!UNRESOLVED_REFERENCE!>x<!>.<!DEBUG_INFO_MISSING_UNRESOLVED!>foo<!> as xex
import b.foo as xex