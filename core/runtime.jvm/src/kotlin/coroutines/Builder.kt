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
    public fun interceptResumeWithException(exception: Throwable, continuation: Continuation<*>): Boolean = false
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
 * Use [Coroutine.start] to start running the resulting coroutine until its first suspension point.
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
 * Creates and starts coroutine for receiver type `R` with result type `T` using a callable reference to `suspend` [lambda].
 * The result of the coroutine's execution is provided via invocation of [resultContinuation].
 */
@SinceKotlin("1.1")
@Suppress("UNCHECKED_CAST")
public fun <R, T> startCoroutine(
        lambda: /*suspend*/ R.() -> T,
        receiver: R,
        resultContinuation: Continuation<T>
) {
    try {
        val result = (lambda as Function2<R, Continuation<T>, Any?>).invoke(receiver, resultContinuation)
        if (result == Suspend) return
        resultContinuation.resume(result as T)
    } catch (e: Throwable) {
        resultContinuation.resumeWithException(e)
    }
}

/**
 * Creates and starts coroutine with result type `T` using a callable reference to `suspend` [lambda].
 * The result of the coroutine's execution is provided via invocation of [resultContinuation].
 * An optional [resumeInterceptor] may be specified to intercept resumes at suspension points inside the coroutine.
 */
@SinceKotlin("1.1")
@Suppress("UNCHECKED_CAST")
public fun <T> startCoroutine(
        lambda: /*suspend*/ () -> T,
        resultContinuation: Continuation<T>,
        resumeInterceptor: ResumeInterceptor? = null
) {
    val adapter = if (resumeInterceptor == null) resultContinuation else
        object : InterceptableContinuation<T>, Continuation<T> by resultContinuation {
            override val resumeInterceptor: ResumeInterceptor? = resumeInterceptor
        }
    try {
        val result = (lambda as Function1<Continuation<T>, Any?>).invoke(adapter)
        if (result == Suspend) return
        resultContinuation.resume(result as T)
    } catch (e: Throwable) {
        resultContinuation.resumeWithException(e)
    }
}

/**
 * Coroutine is a suspendable computation instance.
 * You can use [coroutine] builder function to create one in a more convenient way.
 */
@SinceKotlin("1.1")
public class Coroutine<out T>(private val lambda: /*suspend*/ () -> T) {
    private val adapter = CoroutineAdapter<T>()

    /**
     * Installs handler for the final result of this coroutine.
     * The previously installed handler is replaced.
     */
    public fun handleResult(resultHandler: (T) -> Unit) {
        adapter.resultHandler = resultHandler
    }

    /**
     * Installs handler for the exception produced by this coroutine.
     * The previously installed handler is replaced.
     */
    public fun handleException(exceptionHandler: ((Throwable) -> Unit)) {
        adapter.exceptionHandler = exceptionHandler
    }

    /**
     * Installs interceptor for resumptions inside this coroutine.
     * The previously installed handler is replaced.
     */
    public fun interceptResume(resumeInterceptor: ResumeInterceptor) {
        adapter.resumeInterceptor = resumeInterceptor
    }

    /**
     * Starts running this coroutine in the current execution frame until its first suspension point.
     */
    @Suppress("UNCHECKED_CAST")
    public fun start() {
        try {
            val result = (lambda as Function1<Continuation<*>, Any?>).invoke(adapter)
            if (result == Suspend) return // coroutine had suspended and will provide its result via adapter.resume
            adapter.resume(result as T)
        } catch (exception: Throwable) {
            adapter.resumeWithException(exception)
        }
    }

    /**
     * Starts running this coroutine via [ResumeInterceptor.interceptResumeWithException] function of the
     * installed interceptor. Interceptor may shift initial execution into another execution frame.
     */
    public fun interceptAndStart() {
        // this is just a convenience method
        if (!(adapter.resumeInterceptor?.interceptResume(Unit, object : Continuation<Unit> {
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
    private val adapter = CoroutineAdapter<T>()

    /**
     * Installs handler for the final result of this coroutine.
     * The previously installed handler is replaced.
     */
    public fun handleResult(resultHandler: (T) -> Unit) {
        adapter.resultHandler = resultHandler
    }

    /**
     * Installs handler for the exception produced by this coroutine.
     * The previously installed handler is replaced.
     */
    public fun handleException(exceptionHandler: ((Throwable) -> Unit)) {
        adapter.exceptionHandler = exceptionHandler
    }

    /**
     * Starts running this coroutine with a specified `receiver` in the current execution frame until its first suspension point.
     */
    @Suppress("UNCHECKED_CAST")
    public fun start(receiver: R) {
        startCoroutine(lambda, receiver, adapter)
    }
}

// ------- internal stuff -------

internal interface InterceptableContinuation<P> : Continuation<P> {
    val resumeInterceptor: ResumeInterceptor?
}

// adapter for suspendable computation frame
private class CoroutineAdapter<T>(
    internal var resultHandler: ((T) -> Unit)? = null,
    internal var exceptionHandler: ((Throwable) -> Unit)? = null,
    override var resumeInterceptor: ResumeInterceptor? = null
) : InterceptableContinuation<T> {
    override fun resume(data: T) {
        resultHandler?.invoke(data)
    }

    override fun resumeWithException(exception: Throwable) {
        exceptionHandler?.invoke(exception) ?: throw exception
    }
}

