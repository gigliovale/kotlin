class C<T> {
    private val blah = object {
        fun foo(): T = TODO()
    }

    fun bar(x: T) {}
}