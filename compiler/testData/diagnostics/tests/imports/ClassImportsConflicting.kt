//FILE:a.kt
package a

import b.O
import c.<!NAME_ALREADY_IMPORTED!>O<!>

//FILE:b.kt
package b

object O {}

//FILE:c.kt
package c

object O {}