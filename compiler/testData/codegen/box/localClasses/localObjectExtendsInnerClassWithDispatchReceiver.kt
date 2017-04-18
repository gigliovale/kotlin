class Outer {
    open inner class Inner {
        fun testOK() = "OK"
    }
}

fun Outer.foo(): String {
    val p = object : Outer.Inner() {}
    return p.testOK()
}

fun box(): String {
    val o = Outer()
    return o.foo()
}