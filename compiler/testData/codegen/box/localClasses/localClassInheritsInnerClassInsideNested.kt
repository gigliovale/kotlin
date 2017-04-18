open class FirstOuter {
    open class Nested {
        open inner class Inner {
            fun testOK() = "OK"
        }
    }
}

fun FirstOuter.Nested.foo(): String {
    class Local : FirstOuter.Nested.Inner()
    return Local().testOK()
}

fun FirstOuter.Nested.bar(): String {
    val o = object : FirstOuter.Nested.Inner() {}
    return o.testOK()
}

fun box(): String {
    if (FirstOuter.Nested().foo() != "OK") return "fail 1"
    if (FirstOuter.Nested().bar() != "OK") return "fail 2"

    return "OK"
}