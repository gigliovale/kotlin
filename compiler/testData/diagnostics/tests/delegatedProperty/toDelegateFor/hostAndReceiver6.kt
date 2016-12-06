// !DIAGNOSTICS: -UNUSED_PARAMETER

object T6 {
    interface Fas<D, E, R>

    fun <D, E, R> delegate() : Fas<D, E, R> = TODO()

    operator fun <D, E, R> Fas<D, E, R>.toDelegateFor(host: D, p: Any?): Fas<D, E, R> = TODO()
    operator fun <D, E, R> Fas<D, E, R>.getValue(receiver: E, p: Any?): R = TODO()

    val Long.test1: String by <!TYPE_INFERENCE_NO_INFORMATION_FOR_PARAMETER!>delegate<!>() // common test, not working because of T4
    val Long.test2: String by delegate<T6, Long, String>() // should work
}