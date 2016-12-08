// !DIAGNOSTICS: -UNUSED_EXPRESSION -UNUSED_PARAMETER -UNUSED_VARIABLE

class GenericController<T, R> {
    operator fun handleResult(r: R, c: Continuation<Nothing>) {}
}

suspend fun <S> GenericController<S, *>.yieldAll(s: Collection<S>): String = ""
suspend fun <S> GenericController<S, *>.yieldAll(s: Set<S>): Int = 4

fun <T, R> generate(coroutine g: GenericController<T, R>.() -> Continuation<Unit>): Pair<T, R> = TODO()

val test1 = generate {
    yieldAll(setOf(4))
}

val test2 = generate {
    yieldAll(listOf(4))
}

// Util function
fun <X> setOf(vararg x: X): Set<X> = TODO()
fun <X> listOf(vararg x: X): List<X> = TODO()
class Pair<T, S>
