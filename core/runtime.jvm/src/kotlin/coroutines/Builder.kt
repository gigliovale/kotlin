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
 * A strategy to intercept resumption of suspendable computations.
 * Interceptor may shift resumption into another execution frame by scheduling asynchronous execution
 * in this or another thread.
 */
@SinceKotlin("1.1")
public interface ResumeInterceptor {
    /**
     * Intercepts [Continuation.resume] invocation.
     * This function must either return `false` or return `true` and invoke `continuation.resume(data)` asynchronously.
     */
    public fun <P> interceptResume(data: P, continuation: Continuation<P>): Boolean = false

    /**
     * Intercepts [Continuation.resumeWithException] invocation.
     * This function must either return `false` or return `true` and invoke `continuation.resumeWithException(exception)` asynchronously.
     */
    public fun interceptResume(exception: Throwable, continuation: Continuation<*>): Boolean = false
}

/**
 * Creates coroutine with result type `T` from a callable reference to `suspend` lambda.
 * Use [Coroutine.start] to start running the resulting coroutine until its first suspension point.
 */
@SinceKotlin("1.1")
public inline fun <T> coroutine(
        noinline lambda: /*suspend*/ () -> T,
        builder: Coroutine<T>.() -> Unit): Coroutine<T> {
    val coroutine = Coroutine(lambda)
    coroutine.builder()
    return coroutine
}

/**
 * Creates restricted coroutine for receiver type `R` with result type `T` from a callable reference to `suspend` lambda.
 * Use [RestrictedCoroutine.start] to start running the resulting coroutine until its first suspension point.
 */
@SinceKotlin("1.1")
public inline fun <R, T> coroutine(
        noinline lambda: /*suspend*/ R.() -> T,
        builder: RestrictedCoroutine<R, T>.() -> Unit): RestrictedCoroutine<R, T> {
    val coroutine = RestrictedCoroutine(lambda)
    coroutine.builder()
    return coroutine
}

/**
 * Coroutine is a suspendable computation instance.
 * You can use [coroutine] builder function to create one in a more convenient way.
 */
@SinceKotlin("1.1")
public class Coroutine<out T>(private val lambda: /*suspend*/ () -> T) {
    private val delimiter = CoroutineDelimiter<T>()

    /**
     * Installs handler for the final result of this coroutine.
     * The previously installed handler is replaced.
     */
    public fun handleResult(resultHandler: (T) -> Unit) {
        delimiter.resultHandler = resultHandler
    }

    /**
     * Installs handler for the exception produced by this coroutine.
     * The previously installed handler is replaced.
     */
    public fun handleException(exceptionHandler: ((Throwable) -> Unit)) {
        delimiter.exceptionHandler = exceptionHandler
    }

    /**
     * Installs interceptor for resumptions inside this coroutine.
     * The previously installed handler is replaced.
     */
    public fun interceptResume(resumeInterceptor: ResumeInterceptor) {
        delimiter.resumeInterceptor = resumeInterceptor
    }

    /**
     * Starts running this coroutine in the current execution frame until its first suspension point.
     */
    @Suppress("UNCHECKED_CAST")
    public fun start() {
        try {
            val result = (lambda as Function1<Continuation<*>, Any?>).invoke(delimiter)
            if (result == Suspend) return // coroutine had suspended and will provide its result via delimiter.resume
            delimiter.resume(result as T)
        } catch (exception: Throwable) {
            delimiter.resumeWithException(exception)
        }
    }

    /**
     * Starts running this coroutine via [ResumeInterceptor.interceptResume] function of the
     * installed interceptor. Interceptor may shift initial execution into another execution frame.
     */
    public fun interceptAndStart() {
        // this is just a convenience method
        if (!(delimiter.resumeInterceptor?.interceptResume(Unit, object : Continuation<Unit> {
            override fun resume(data: Unit) = start()
            override fun resumeWithException(exception: Throwable) {}
        }) ?: false))
            start()
    }
}

/**
 * Restricted coroutine is a suspendable computation instance that cannot be intercepted.
 * You can use [coroutine] builder function to create one in a more convenient way.
 */
@SinceKotlin("1.1")
public class RestrictedCoroutine<R, T>(private val lambda: /*suspend*/ R.() -> T) {
    private val delimiter = CoroutineDelimiter<T>()

    /**
     * Installs handler for the final result of this coroutine.
     * The previously installed handler is replaced.
     */
    public fun handleResult(resultHandler: (T) -> Unit) {
        delimiter.resultHandler = resultHandler
    }

    /**
     * Installs handler for the exception produced by this coroutine.
     * The previously installed handler is replaced.
     */
    public fun handleException(exceptionHandler: ((Throwable) -> Unit)) {
        delimiter.exceptionHandler = exceptionHandler
    }

    /**
     * Starts running this coroutine with a specified `receiver` in the current execution frame until its first suspension point.
     */
    @Suppress("UNCHECKED_CAST")
    public fun start(receiver: R) {
        try {
            val result = (lambda as Function2<R, Continuation<*>, Any?>).invoke(receiver, delimiter)
            if (result == Suspend) return // coroutine had suspended and will provide its result via delimiter.resume
            delimiter.resume(result as T)
        } catch (exception: Throwable) {
            delimiter.resumeWithException(exception)
        }
    }
}

// ------- internal stuff -------

internal interface InterceptableContinuation<P> : Continuation<P> {
    val resumeInterceptor: ResumeInterceptor?
}

// Delimiter for suspendable computation frame
private class CoroutineDelimiter<R> : InterceptableContinuation<R> {
    override var resumeInterceptor: ResumeInterceptor? = null
    internal var resultHandler: ((R) -> Unit)? = null
    internal var exceptionHandler: ((Throwable) -> Unit)? = null

    override fun resume(data: R) {
        resultHandler?.invoke(data)
    }

    override fun resumeWithException(exception: Throwable) {
        exceptionHandler?.invoke(exception) ?: throw exception
    }
}

