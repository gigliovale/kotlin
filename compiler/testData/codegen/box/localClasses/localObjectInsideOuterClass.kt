open class Outer {
    open inner class Inner
}

class Baz {
    fun Outer.foo(): String {
        val p = object : Outer.Inner() {
            fun testOk() = "OK"
        }

        return p.testOk()
    }

    fun test(): String {
        return Outer().foo()
    }
}

fun box(): String {
    return Baz().test()
}