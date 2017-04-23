open class Outer {
    open inner class Inner
}

class Baz {
    fun Outer.foo(): String {
        class Local : Outer.Inner() {
            fun testOk() = "OK"
        }

        return Local().testOk()
    }

    fun test(): String {
        return Outer().foo()
    }
}

fun box(): String {
    return Baz().test()
}