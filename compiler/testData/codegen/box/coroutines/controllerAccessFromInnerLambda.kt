// WITH_RUNTIME
// WITH_COROUTINES
import kotlin.coroutines.*

class Controller {
    var result = false
    suspend fun suspendHere(): String = suspendWithCurrentContinuation { x ->
        x.resume("OK")
        SUSPENDED
    }

    fun foo() {
        result = true
    }
}

fun builder(c: suspend Controller.() -> Unit) {
    val controller = Controller()
    c.startCoroutine(controller, EmptyContinuation)
    if (!controller.result) throw RuntimeException("fail")
}

fun noinlineRun(block: () -> Unit) {
    block()
}

fun box(): String {
    builder {
        noinlineRun {
            foo()
        }
    }

    return "OK"
}
