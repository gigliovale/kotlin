import test.*

fun test1(): String {
    val o = "O"

    val result = doWork ({o}, {"K"})

    return result.getO() + result.getK()
}

fun test2() : String {
    //same names as in object
    val o1 = "O"
    val k1 = "K"

    val result = doWorkInConstructor ({o1}, {k1})

    return result.getO() + result.getK()
}

fun box() : String {
    val result1 = test1();
    if (test1() != "OK") return "fail1 $result1"

    val result2 = test2();
    if (test2() != "OK") return "fail2 $result2"

    return "OK"
}

