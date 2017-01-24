// WITH_RUNTIME
// WITH_COROUTINES
import kotlin.coroutines.startCoroutine

class Controller {
    suspend inline fun suspendInline(v: String): String = throw RuntimeException(v)
    suspend fun suspendNoInline(v: String): String = throw RuntimeException(v)
}

fun builder(c: suspend Controller.() -> Unit) {
    c.startCoroutine(Controller(), EmptyContinuation)
}

class OK

fun box(): String {
    var result = ""

    builder {
        result = try { suspendNoInline("OK") } catch (e: RuntimeException) { e.message!! }
    }

    return result
}