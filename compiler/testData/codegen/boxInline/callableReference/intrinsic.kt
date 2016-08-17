// FILE: 1.kt

package test

inline fun call(p: String, s: String.() -> String): Int {
    return p.s().length
}

// FILE: 2.kt

import test.*

fun box() : String {
    return if (call("123", String::toString) == 3) "OK" else "fail"
}
