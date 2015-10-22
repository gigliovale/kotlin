//!DIAGNOSTICS: -UNUSED_PARAMETER

@Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
fun <@kotlin.internal.OnlyInputTypes T> assertEquals1(t1: T, t2: T) {}

fun test(src: Array<Any>) {
    //todo
    assertEquals1(arrayListOf(3.0), src.filterIsInstance<Double>())
}
