fun f(a: Array<out List<Any>>) {
    if (a is <!CANNOT_CHECK_FOR_ERASED!>Array<List<String>><!>) {}
    <!UNCHECKED_CAST!>a as Array<List<String>><!>
}