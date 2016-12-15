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

/**
 * An interface to customise dispatch of continuations.
 */
@SinceKotlin("1.1")
public interface ContinuationDispatcher {
    /**
     * Dispatches [Continuation.resume] invocation.
     * This function must either return `false` or return `true` and invoke `continuation.resume(value)` asynchronously.
     */
    public fun <T> dispatchResume(value: T, continuation: Continuation<T>): Boolean = false

    /**
     * Dispatches [Continuation.resumeWithException] invocation.
     * This function must either return `false` or return `true` and invoke `continuation.resumeWithException(exception)` asynchronously.
     */
    public fun dispatchResumeWithException(exception: Throwable, continuation: Continuation<*>): Boolean = false
}

/**
 * Creates coroutine with receiver type [R] and result type [T].
 * This function creates a new, fresh instance of suspendable computation every time it is invoked.
 * To start executing the created coroutine, invoke `resume(Unit)` on the returned [Continuation] instance.
 * The [completion] continuation is invoked when coroutine completes with result of exception.
 * An optional [dispatcher] may be specified to customise dispatch of continuations between suspension points inside the coroutine.
 */
@SinceKotlin("1.1")
@Suppress("UNCHECKED_CAST")
public fun <R, T> (suspend R.() -> T).createCoroutine(
        receiver: R,
        completion: Continuation<T>,
        dispatcher: ContinuationDispatcher? = null
): Continuation<Unit> = (this as (R, Continuation<T>) -> Continuation<Unit>).invoke(receiver, withDispatcher(completion, dispatcher))

/**
 * Starts coroutine with receiver type [R] and result type [T].
 * This function creates and start a new, fresh instance of suspendable computation every time it is invoked.
 * The [completion] continuation is invoked when coroutine completes with result of exception.
 * An optional [dispatcher] may be specified to customise dispatch of continuations between suspension points inside the coroutine.
 */
@SinceKotlin("1.1")
@Suppress("UNCHECKED_CAST")
public fun <R, T> (suspend R.() -> T).startCoroutine(
        receiver: R,
        completion: Continuation<T>,
        dispatcher: ContinuationDispatcher? = null
) {
    createCoroutine(receiver, completion, dispatcher).resume(Unit)
}

/**
 * Creates coroutine without receiver and with result type [T].
 * This function creates a new, fresh instance of suspendable computation every time it is invoked.
 * To start executing the created coroutine, invoke `resume(Unit)` on the returned [Continuation] instance.
 * The [completion] continuation is invoked when coroutine completes with result of exception.
 * An optional [dispatcher] may be specified to customise dispatch of continuations between suspension points inside the coroutine.
 */
@SinceKotlin("1.1")
@Suppress("UNCHECKED_CAST")
public fun <T> (suspend () -> T).createCoroutine(
        completion: Continuation<T>,
        dispatcher: ContinuationDispatcher? = null
): Continuation<Unit> = (this as (Continuation<T>) -> Continuation<Unit>).invoke(withDispatcher(completion, dispatcher))

/**
 * Starts coroutine without receiver and with result type [T].
 * This function creates and start a new, fresh instance of suspendable computation every time it is invoked.
 * The [completion] continuation is invoked when coroutine completes with result of exception.
 * An optional [dispatcher] may be specified to customise dispatch of continuations between suspension points inside the coroutine.
 */
@SinceKotlin("1.1")
@Suppress("UNCHECKED_CAST")
public fun <T> (suspend  () -> T).startCoroutine(
        completion: Continuation<T>,
        dispatcher: ContinuationDispatcher? = null
) {
    createCoroutine(completion, dispatcher).resume(Unit)
}

// ------- internal stuff -------

internal interface DispatchedContinuation<T> : Continuation<T> {
    val dispatcher: ContinuationDispatcher?
}

private fun <T> withDispatcher(completion: Continuation<T>, dispatcher: ContinuationDispatcher?): Continuation<T> {
    return if (dispatcher == null) completion else
        object : DispatchedContinuation<T>, Continuation<T> by completion {
            override val dispatcher: ContinuationDispatcher? = dispatcher
        }
}
