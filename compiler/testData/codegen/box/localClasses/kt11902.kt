class Outer {
    inner abstract class Inner {
        abstract fun foo(): String
    }
}

fun Outer.test() =
        object : Outer.Inner() {
            override fun foo(): String {
                return "OK"
            }
        }

fun box(): String {
    return Outer().test().foo()
}