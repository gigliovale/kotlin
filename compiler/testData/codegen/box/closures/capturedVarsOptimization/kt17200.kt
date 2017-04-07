inline fun inlineCall(action: () -> Unit) {
    action()
}

fun test() {
    var width = 1
    inlineCall {
        width += width
    }
}

fun box(): String {
    test()
    return "OK"
}