// !DIAGNOSTICS: -UNUSED_PARAMETER
import kotlin.coroutines.*

fun nonSuspend() {}

suspend fun foo() {
    <!SUSPENSION_CALL_MUST_BE_USED_AS_RETURN_VALUE!>CoroutineIntrinsics.suspendCoroutineOrReturn { x: Continuation<Int> -> }<!>

    nonSuspend()
}

suspend fun unitSuspend() {
    <!SUSPENSION_CALL_MUST_BE_USED_AS_RETURN_VALUE!>CoroutineIntrinsics.suspendCoroutineOrReturn { x: Continuation<Int> -> }<!>
}

suspend fun baz(): Int = run {
    <!SUSPENSION_CALL_MUST_BE_USED_AS_RETURN_VALUE!>CoroutineIntrinsics.suspendCoroutineOrReturn { x: Continuation<Int> -> }<!>
}
