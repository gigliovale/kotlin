class Test {
    private val z = object {
        fun bar() = <!DEBUG_INFO_LEAKING_THIS!>this@Test<!>
    }

    val x: String = z.bar().x // OOPS! x holds null
}