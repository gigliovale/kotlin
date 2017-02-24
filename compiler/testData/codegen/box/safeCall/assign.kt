class A(var z: Boolean)

fun test(a: A?): A? {
    a?.z = true
    return a
}

fun box(): String {
    if (!test(A(false))!!.z) return "fail"
    return "OK"
}



fun test() {

}