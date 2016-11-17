// MODULE: lib
// FILE: lib.kt

package utils

inline fun <reified T> rrr(f1: Any.()->Unit) {
    4.f1()
}

// MODULE: main(lib)
// FILE: main.kt

import utils.*

fun box(): String {
    var result = "fail"
    rrr<Any> {
        result = "OK"
    }
    return result
}