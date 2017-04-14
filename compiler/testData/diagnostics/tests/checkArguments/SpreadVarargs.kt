
interface Base<I>
class A : Base<A>
class B : Base<B>

fun <W> one(vararg w: W): W = TODO()

fun test(a: A, b: B) = one(a, b) 