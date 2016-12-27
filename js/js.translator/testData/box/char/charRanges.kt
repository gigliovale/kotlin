package foo

class A {

    override fun toString(): String {
        return "A";
    }
}

fun box(): String {

    var a = A()
    var q = ""
    q += a

    assertEquals(true, 'B' in 'A'..'D')
    assertEquals(true, 'E' !in 'A'..'D')

    var s = ""
    for(char in 'A'..'D') {
        s += char
    }
    assertEquals("ABCD", s)

    return "OK"
}