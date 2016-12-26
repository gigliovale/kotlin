@file:kotlin.jvm.JvmName("CoroutinesKt")
@file:kotlin.jvm.JvmVersion
package kotlin.coroutines

import java.util.concurrent.atomic.AtomicReferenceFieldUpdater
import kotlin.coroutines.CoroutineIntrinsics.SUSPENDED

/**
 * Creates coroutine with receiver type [R] and result type [T].
 * This function creates a new, fresh instance of suspendable computation every time it is invoked.
 * To start executing the created coroutine, invoke `resume(Unit)` on the returned [Continuation] instance.
 * The [completion] continuation is invoked when coroutine completes with result of exception.
 * An optional [dispatcher] may be specified to customise dispatch of continuations between suspension points inside the coroutine.
 */
@SinceKotlin("1.1")
@Suppress("UNCHECKED_CAST", "INVISIBLE_MEMBER")
public fun <R, T> (suspend R.() -> T).createCoroutine(
        receiver: R,
        completion: Continuation<T>,
        dispatcher: ContinuationDispatcher? = null
): Continuation<Unit> = (this as kotlin.jvm.internal.SuspendFunction1<R, T>)
        .create(receiver, kotlin.coroutines.internal.withDispatcher(completion, dispatcher))

/**
 * Starts coroutine with receiver type [R] and result type [T].
 * This function creates and start a new, fresh instance of suspendable computation every time it is invoked.
 * The [completion] continuation is invoked when coroutine completes with result of exception.
 * An optional [dispatcher] may be specified to customise dispatch of continuations between suspension points inside the coroutine.
 */
@SinceKotlin("1.1")
@Suppress("UNCHECKED_CAST", "INVISIBLE_MEMBER")
public fun <R, T> (suspend R.() -> T).startCoroutine(
        receiver: R,
        completion: Continuation<T>,
        dispatcher: ContinuationDispatcher? = null
) {
    (this as Function2<R, Continuation<T>, Any?>).invoke(receiver, kotlin.coroutines.internal.withDispatcher(completion, dispatcher))
}

/**
 * Creates coroutine without receiver and with result type [T].
 * This function creates a new, fresh instance of suspendable computation every time it is invoked.
 * To start executing the created coroutine, invoke `resume(Unit)` on the returned [Continuation] instance.
 * The [completion] continuation is invoked when coroutine completes with result of exception.
 * An optional [dispatcher] may be specified to customise dispatch of continuations between suspension points inside the coroutine.
 */
@SinceKotlin("1.1")
@Suppress("UNCHECKED_CAST", "INVISIBLE_MEMBER")
public fun <T> (suspend () -> T).createCoroutine(
        completion: Continuation<T>,
        dispatcher: ContinuationDispatcher? = null
): Continuation<Unit> = (this as kotlin.jvm.internal.SuspendFunction0<T>)
        .create(kotlin.coroutines.internal.withDispatcher(completion, dispatcher))

/**
 * Starts coroutine without receiver and with result type [T].
 * This function creates and start a new, fresh instance of suspendable computation every time it is invoked.
 * The [completion] continuation is invoked when coroutine completes with result of exception.
 * An optional [dispatcher] may be specified to customise dispatch of continuations between suspension points inside the coroutine.
 */
@SinceKotlin("1.1")
@Suppress("UNCHECKED_CAST", "INVISIBLE_MEMBER")
public fun <T> (suspend  () -> T).startCoroutine(
        completion: Continuation<T>,
        dispatcher: ContinuationDispatcher? = null
) {
    (this as Function1<Continuation<T>, Any?>).invoke(kotlin.coroutines.internal.withDispatcher(completion, dispatcher))
}

/**
 * Obtains the current continuation instance inside suspending functions and suspends
 * currently running coroutine.
 *
 * In this function both [Continuation.resume] and [Continuation.resumeWithException] can be used either synchronously in
 * the same stack-frame where suspension function is run or asynchronously later in the same thread or
 * from a different thread of execution. Repeated invocation of any resume function produces [IllegalStateException].
 */
@SinceKotlin("1.1")
public inline suspend fun <T> suspendCoroutine(crossinline block: (Continuation<T>) -> Unit): T =
        CoroutineIntrinsics.suspendCoroutineOrReturn { c: Continuation<T> ->
            val safe = SafeContinuation(c)
            block(safe)
            safe.getResult()
        }

/**
 * Obtains the current continuation instance and dispatcher inside suspending functions and suspends
 * currently running coroutine.
 *
 * See [suspendCoroutine] for all the details. The only difference in this function is that it also
 * provides a reference to the dispatcher of the coroutine that is was invoked from or `null` the coroutine
 * was running without dispatcher.
 */
@SinceKotlin("1.1")
public inline suspend fun <T> suspendDispatchedCoroutine(crossinline block: (Continuation<T>, ContinuationDispatcher?) -> Unit): T =
        CoroutineIntrinsics.suspendDispatchedCoroutineOrReturn { c: Continuation<T>, d: ContinuationDispatcher? ->
            val safe = SafeContinuation(c)
            block(safe, d)
            safe.getResult()
        }

// INTERNAL DECLARATIONS

private val UNDECIDED: Any? = Any()
private val RESUMED: Any? = Any()
private class Fail(val exception: Throwable)

@PublishedApi
internal class SafeContinuation<in T>(private val delegate: Continuation<T>) : Continuation<T> {
    @Volatile
    private var result: Any? = UNDECIDED

    companion object {
        @Suppress("UNCHECKED_CAST")
        @JvmStatic
        private val RESULT_UPDATER = AtomicReferenceFieldUpdater.newUpdater<SafeContinuation<*>, Any?>(
                SafeContinuation::class.java, Any::class.java as Class<Any?>, "result")
    }

    private fun cas(expect: Any?, update: Any?): Boolean =
            RESULT_UPDATER.compareAndSet(this, expect, update)

    override fun resume(value: T) {
        while (true) { // lock-free loop
            val result = this.result // atomic read
            when (result) {
                UNDECIDED -> if (cas(UNDECIDED, value)) return
                SUSPENDED -> if (cas(SUSPENDED, RESUMED)) {
                    delegate.resume(value)
                    return
                }
                else -> throw IllegalStateException("Already resumed")
            }
        }
    }

    override fun resumeWithException(exception: Throwable) {
        while (true) { // lock-free loop
            val result = this.result // atomic read
            when (result) {
                UNDECIDED -> if (cas(UNDECIDED, Fail(exception))) return
                SUSPENDED -> if (cas(SUSPENDED, RESUMED)) {
                    delegate.resumeWithException(exception)
                    return
                }
                else -> throw IllegalStateException("Already resumed")
            }
        }
    }

    @PublishedApi
    internal fun getResult(): Any? {
        var result = this.result // atomic read
        if (result == UNDECIDED) {
            if (cas(UNDECIDED, SUSPENDED)) return SUSPENDED
            result = this.result // reread volatile var
        }
        when (result) {
            RESUMED -> return SUSPENDED // already called continuation, indicate SUSPENDED upstream
            is Fail -> throw result.exception
            else -> return result // either SUSPENDED or data
        }
    }
}
