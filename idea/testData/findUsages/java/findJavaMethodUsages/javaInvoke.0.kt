fun f(c: JavaClass) {
    c()
}

fun foo(o: JavaClass.OtherJavaClass) {
    o()
    JavaClass.OtherJavaClass.OJC()
}

fun foo() {
    JavaClass.INSTANCE()
    JavaClass.AnotherOther.INSTANCE()
    JavaClass.JavaOther.INSTANCE()
}
