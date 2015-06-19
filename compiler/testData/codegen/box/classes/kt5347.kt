fun test1(str: String): String {
    @data class A(val x: Int) {
        fun foo() = str
    }
    return A(0).copy().foo()
}

fun String.test2(): String {
    @data class A(val x: Int) {
        fun foo() = this@test2
    }
    return A(0).copy().foo()
}

class TestClass(val x: String) {
    fun foo(): String {
        @data class A(val x: Int) {
            fun foo() = this@TestClass.x
        }
        return A(0).copy().foo()
    }
}

fun test3(str: String): String = TestClass(str).foo()


fun box(): String {
    return when {
        test1("test1") != "test1" -> "Failed #1"
        "test2".test2() != "test2" -> "Failed #2"
        test3("test3") != "test3" -> "Failed #3"
        else -> "OK"
    }
}