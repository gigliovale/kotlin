class Outer {
    fun Nested.foo(): String = x

    class Nested(outer: Outer) {
        // Does not work now...
        val x: String = with(outer) { <!DEBUG_INFO_LEAKING_THIS!>foo<!>() } // OOPS! x also holds null
    }
}