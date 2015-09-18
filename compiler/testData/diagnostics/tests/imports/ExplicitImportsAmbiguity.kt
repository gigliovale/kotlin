// FILE: a.kt
package a

class X

// FILE: b.kt
package b

class X

// FILE: c.kt
package c

import a.X
import b.<!NAME_ALREADY_IMPORTED!>X<!>

class Y : <!UNRESOLVED_REFERENCE!>X<!>