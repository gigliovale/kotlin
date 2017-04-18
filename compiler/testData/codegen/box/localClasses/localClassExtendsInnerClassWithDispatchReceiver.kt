class Outer {
    open inner class Inner
}

fun Outer.foo(): String {
    class Local : Outer.Inner() {
        fun res() = "OK"
    }

    return Local().res()
}

fun box(): String {
    val o = Outer()
    return o.foo()
}