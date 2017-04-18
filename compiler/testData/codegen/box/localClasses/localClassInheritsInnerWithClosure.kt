class Outer {
    open inner class Inner
}

fun Outer.foo(b: Boolean): String {
    var closure = "ok?"
    class Local : Outer.Inner() {
        fun test() {
            if (b) {
                closure = "OK"
            }
        }
    }

    Local().test()

    return closure
}

fun box(): String {
    return Outer().foo(true)
}