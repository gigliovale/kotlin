// Tests for KT-13597 (val assignment inside local object in constructor)

class Test {
    val a: String

    init {
        val t = object {
            fun some() {
                // See KT-13597
                <!CAPTURED_VAL_INITIALIZATION!>a<!> = "12"
            }
        }

        a = "2"
        t.some()
    }
}

class Test2 {
    init {
        val t = object {
            fun some() {
                <!VAL_REASSIGNMENT!>a<!> = "12"
            }
        }

        <!INITIALIZATION_BEFORE_DECLARATION!>a<!> = "2"
        t.some()
    }

    val a: String
}

// Tests for KT-14381 (val assignment inside lambda in constructor)

class Test3 {
    val a: String

    init {
        run {
            <!VAL_REASSIGNMENT!>a<!> = "12"
        }
        a = "34"
    }
}

fun <T> exec(f: () -> T): T = f()

class Test4 {
    val a: String

    init {
        exec {
            // See KT-14381
            <!CAPTURED_VAL_INITIALIZATION!>a<!> = "12"
        }
        a = "34"
    }
}
