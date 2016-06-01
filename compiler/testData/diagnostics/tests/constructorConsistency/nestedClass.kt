class Outer {
    fun Nested.foo(): String = x

    class Nested(outer: Outer) {
        // Does not work now...
        val x: String = with(outer) { foo() } // OOPS! x also holds null
    }
}