// !DIAGNOSTICS: -UNUSED_PARAMETER

object T5 {
    interface Foo<T>

    fun <T> delegate(): Foo<T> = TODO()

    operator fun <T> Foo<T>.toDelegateFor(host: T, p: Any?): Foo<T> = TODO()
    operator fun <T> Foo<T>.getValue(receiver: T5, p: Any?): String = TODO()

    val test1: String by <!TYPE_INFERENCE_NO_INFORMATION_FOR_PARAMETER!>delegate<!>() // same story like in T4
    val test2: String by delegate<T5>()
}
