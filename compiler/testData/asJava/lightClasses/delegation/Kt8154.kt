// Kt8154

interface A<T> {
    fun foo()
}

interface B<T> : A<T> {
    fun bar()
}

class Kt8154<T>(a: A<T>) : B<T>, A<T> by a {
    override fun bar() { throw UnsupportedOperationException() }
}