class Outer {
    open inner class Inner

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
    return Outer().test()
}