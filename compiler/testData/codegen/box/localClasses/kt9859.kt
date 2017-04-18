open class A(val s: String) {
    open inner class InnerA (val s: String)
}

class B : A("B") {
    fun test () = object : A.InnerA("OK") {
        fun test() = s
    }.test()
}

fun box(): String {
    return B().test()
}