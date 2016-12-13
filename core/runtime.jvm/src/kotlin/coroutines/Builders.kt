/*
 * Copyright 2010-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package kotlin.coroutines

import kotlin.jvm.internal.CoroutineImpl
import kotlin.jvm.internal.RestrictedCoroutineImpl

/**
 * A strategy to intercept suspension points inside coroutine. A suspension point is defined as an invocation
 * of [runWithCurrentContinuation], [suspendWithCurrentContinuation] or [maySuspendWithCurrentContinuation]
 * functions. Even when the [maySuspendWithCurrentContinuation] function does not suspend exectuion (but returns result immediately)
 * it is still considered to be a suspension point from the standpoint of interceptor.
 *
 * Interceptor may execute some code before coroutine execution is suspended via
 * [interceptSuspend] function to save some additional information about the current execution context.
 * The information that might need saving typically includes various thread-local variables used
 * for authentication, resources allocation, etc.
 *
 * The suspend function may resume execution in some other
 * execution context. Interceptor may execute some additional code on resume in one of two ways. It may
 * either wrap [Continuation] object and return a wrapper from [interceptSuspend], in which case the
 * returned contination will be used to resume execution, and/or it may override
 * [interceptResume] and/or [interceptResumeWithException] functions to intercept resumption without
 * the need to wrap continuation. Note, that if both ways are used, then [interceptResume] and
 * [interceptResumeWithException] functions receive the continuation returned
 * by [interceptResume] function as their second parameter.
 *
 * Interceptor may shift coroutine resumption into another execution frame by scheduling asynchronous execution
 * in this or another thread.
 */
@SinceKotlin("1.1")
public interface SuspendInterceptor {
    /**
     * Intercepts suspension points inside coroutine.
     * This implementation always returns original [continuation].
     */
    public fun <P> interceptSuspend(continuation: Continuation<P>): Continuation<P> = continuation

    /**
     * Intercepts [Continuation.resume] invocation when coroutine execution is resumed.
     * This function must either return `false` or return `true` and invoke `continuation.resume(data)` asynchronously.
     * This implementation always returns `false`.
     */
    public fun <P> interceptResume(data: P, continuation: Continuation<P>): Boolean = false

    /**
     * Intercepts [Continuation.resumeWithException] invocation when coroutine execution is resumed.
     * This function must either return `false` or return `true` and invoke `continuation.resumeWithException(exception)` asynchronously.
     * This implementation always returns `false`.
     */
    public fun interceptResumeWithException(exception: Throwable, continuation: Continuation<*>): Boolean = false
}

/**
 * Creates coroutine with receiver type [R] and result type [T].
 * This function creates a new, fresh instance of suspendable computation every time it is invoked.
 * To start executing the created coroutine, invoke `resume(Unit)` on the returned [Continuation] instance.
 * The result of the coroutine's execution is provided via invocation of [resultHandler].
 */
@SinceKotlin("1.1")
@Suppress("UNCHECKED_CAST")
public fun <R, T> (/*suspend*/ R.() -> T).createCoroutine(
        receiver: R,
        resultHandler: Continuation<T>
): Continuation<Unit> {
    // check if the RestrictedCoroutineImpl was passed and do efficient creation
    if (this is RestrictedCoroutineImpl)
        return doCreateInternal(receiver, resultHandler)
    // otherwise, it is just a callable reference to some suspend function
    return object : Continuation<Unit> {
        override fun resume(data: Unit) {
            startCoroutine(receiver, resultHandler)
        }

        override fun resumeWithException(exception: Throwable) {
            resultHandler.resumeWithException(exception)
        }
    }
}

/**
 * Starts coroutine with receiver type [R] and result type [T].
 * This function creates and start a new, fresh instance of suspendable computation every time it is invoked.
 * The result of the coroutine's execution is provided via invocation of [resultHandler].
 */
@SinceKotlin("1.1")
@Suppress("UNCHECKED_CAST")
public fun <R, T> (/*suspend*/ R.() -> T).startCoroutine(
        receiver: R,
        resultHandler: Continuation<T>
) {
    try {
        val result = (this as Function2<R, Continuation<T>, Any?>).invoke(receiver, resultHandler)
        if (result == SUSPENDED) return
        resultHandler.resume(result as T)
    } catch (e: Throwable) {
        resultHandler.resumeWithException(e)
    }
}

/**
 * Creates coroutine without receiver and with result type [T].
 * This function creates a new, fresh instance of suspendable computation every time it is invoked.
 * To start executing the created coroutine, invoke `resume(Unit)` on the returned [Continuation] instance.
 * The result of the coroutine's execution is provided via invocation of [resultHandler].
 * An optional [interceptor] may be specified to intercept suspension points inside the coroutine.
 */
@SinceKotlin("1.1")
@Suppress("UNCHECKED_CAST")
public fun <T> (/*suspend*/ () -> T).createCoroutine(
        resultHandler: Continuation<T>,
        interceptor: SuspendInterceptor? = null
): Continuation<Unit> {
    // check if the CoroutineImpl was passed and do efficient creation
    if (this is CoroutineImpl)
        return doCreateInternal(null, withInterceptor(resultHandler, interceptor))
    // otherwise, it is just a callable reference to some suspend function
    return object : Continuation<Unit> {
        override fun resume(data: Unit) {
            startCoroutine(resultHandler, interceptor)
        }

        override fun resumeWithException(exception: Throwable) {
            resultHandler.resumeWithException(exception)
        }
    }
}

/**
 * Starts coroutine without receiver and with result type [T].
 * This function creates and start a new, fresh instance of suspendable computation every time it is invoked.
 * The result of the coroutine's execution is provided via invocation of [resultHandler].
 * An optional [interceptor] may be specified to intercept suspension points inside the coroutine.
 */
@SinceKotlin("1.1")
@Suppress("UNCHECKED_CAST")
public fun <T> (/*suspend*/ () -> T).startCoroutine(
        resultHandler: Continuation<T>,
        interceptor: SuspendInterceptor? = null
) {
    try {
        val result = (this as Function1<Continuation<T>, Any?>).invoke(withInterceptor(resultHandler, interceptor))
        if (result == SUSPENDED) return
        resultHandler.resume(result as T)
    } catch (e: Throwable) {
        resultHandler.resumeWithException(e)
    }
}

// ------- internal stuff -------

internal interface InterceptableContinuation<P> : Continuation<P> {
    val interceptor: SuspendInterceptor?
}

private fun <T> withInterceptor(resultHandler: Continuation<T>, interceptor: SuspendInterceptor?): Continuation<T> {
    return if (interceptor == null) resultHandler else
        object : InterceptableContinuation<T>, Continuation<T> by resultHandler {
            override val interceptor: SuspendInterceptor? = interceptor
        }
}
