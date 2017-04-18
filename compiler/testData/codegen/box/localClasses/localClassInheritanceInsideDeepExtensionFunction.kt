open class FirstOuter {
    open inner class Inner {}
}

open class SecondOuter  {
    open inner class Inner {}
}

fun FirstOuter.foo(): String {
    fun SecondOuter.bar(): String {
        class Local : SecondOuter.Inner() {
            fun testOK() = "OK"
        }

        return Local().testOK()
    }

    return SecondOuter().bar()
}

fun box(): String {
    return FirstOuter().foo()
}