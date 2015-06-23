fun test1(str: String): String {
    var s = ""
    for (i in 1..3) {
        s += if (i<2) str else break;
    }
    return s;
}

fun test2(str: String): String {
    var s = ""
    for (i in 1..3) {
        s += if (i<2) str else continue;
    }
    return s;
}

fun box(): String {
    return when {
        test1("test1") != "test1" -> "Failed #1"
        test2("test2") != "test2" -> "Failed #2"

        else -> "OK"
    }
}