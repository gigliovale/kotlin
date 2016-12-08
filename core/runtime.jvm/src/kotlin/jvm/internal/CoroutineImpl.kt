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

package kotlin.jvm.internal

import kotlin.coroutines.*

private const val INTERCEPT_BIT_SET = 1 shl 31
private const val INTERCEPT_BIT_CLEAR = INTERCEPT_BIT_SET.inv()

abstract class CoroutineImpl : RestrictedCoroutineImpl, InterceptableContinuation<Any?> {
    private val _resumeInterceptor: ResumeInterceptor?

    override val resumeInterceptor: ResumeInterceptor?
        get() = _resumeInterceptor

    // this constructor is used to create initial "factory" lambda object
    constructor(arity: Int) : super(arity) {
        _resumeInterceptor = null
    }

    // this constructor is used to create a continuation instance for coroutine
    constructor(arity: Int, resultContinuation: Continuation<Any?>?) : super(arity, resultContinuation) {
        _resumeInterceptor = (resultContinuation as? InterceptableContinuation<*>)?.resumeInterceptor
    }

    override fun resume(data: Any?) {
        if (_resumeInterceptor != null) {
            if (label and INTERCEPT_BIT_SET == 0) {
                label = label or INTERCEPT_BIT_SET
                if (_resumeInterceptor.interceptResume(data, this)) return
            }
            label = label and INTERCEPT_BIT_CLEAR
        }
        super.resume(data)
    }

    override fun resumeWithException(exception: Throwable) {
        if (_resumeInterceptor != null) {
            if (label and INTERCEPT_BIT_SET == 0) {
                label = label or INTERCEPT_BIT_SET
                if (_resumeInterceptor.interceptResumeWithException(exception, this)) return
            }
            label = label and INTERCEPT_BIT_CLEAR
        }
        doResume(null, exception)
    }
}

abstract class RestrictedCoroutineImpl : Lambda, Continuation<Any?> {
    @JvmField
    protected val resultContinuation: Continuation<Any?>?

    // label == -1 when coroutine cannot be started (it is just a factory object) or has already finished execution
    // label == 0 in initial part of the coroutine
    @JvmField
    protected var label: Int

    // this constructor is used to create initial "factory" lambda object
    constructor(arity: Int) : super(arity) {
        resultContinuation = null
        label = -1 // don't use this object as coroutine
    }

    // this constructor is used to create a continuation instance for coroutine
    constructor(arity: Int, resultContinuation: Continuation<Any?>?) : super(arity) {
        this.resultContinuation = resultContinuation
        label = 0
    }

    override fun resume(data: Any?) {
        try {
            val result = doResume(data, null)
            if (result != Suspend)
                resultContinuation!!.resume(result)
        } catch (e: Throwable) {
            resultContinuation!!.resumeWithException(e)
        }
    }

    override fun resumeWithException(exception: Throwable) {
        try {
            val result = doResume(null, exception)
            if (result != Suspend)
                resultContinuation!!.resume(result)
        } catch (e: Throwable) {
            resultContinuation!!.resumeWithException(e)
        }
    }

    protected abstract fun doResume(data: Any?, exception: Throwable?): Any?
}

/*
===========================================================================================================================
Showcase of coroutine compilation strategy.

Given this following "sample" suspend function code:

---------------------------------------------------------------------------------------------
@RestrictSuspension
class Receiver {
    suspend fun yield(): SomeResult
}

suspend fun Receiver.sample(): String {
    doSomethingBefore()
    val yieldResult = yield() // suspension point
    doSomethingAfter(yieldResult)
    return "Done"
}
---------------------------------------------------------------------------------------------

The compiler emits the class with the following logic:

---------------------------------------------------------------------------------------------
class XXXCoroutine : RestrictedCoroutineImpl, Function2<Receiver, Continuation<*>, Any?> {
    //               ^^^^^^^^^^^^^^^^^^^^^^^
    //          Replace with CoroutineImpl if the coroutine is non-restricted

    val receiver: Receiver?
    //  ^^^^^^^^^^^^^^^^^^^
    //    receiver is just a part of the captured scope

    // this constructor is used to create initial "factory" lambda object
    constructor() : super(2, null) {
        this.receiver = null
    }

    // this constructor is used to create a continuation instance for coroutine
    constructor(receiver: Receiver, resultContinuation: Continuation<Any?>) : super(2, resultContinuation) {
        this.receiver = receiver
    }

    // coroutine factory implementation of Kotlin type: suspend Receiver.() -> String
    override fun invoke(receiver: Receiver, resultContinuation: Continuation<*>): Any? {
        val coroutine = XXXCoroutine(receiver, resultContinuation) // create the actual instance
        return coroutine.doResume(Unit, null) // and run it until first suspension
    }

    override fun doResume(data: Any?, exception: Throwable?): Any? {
        var supensionResult = data
        switch (label) {
        default:
            throw IllegalStateException()
        case 0:
            doSomethingBefore()
            label = 1 // set next label before calling suspend function
            supensionResult = receiver.yield(this)
            if (supensionResult == Suspend) return Suspend
            // falls through with yeild result if it is available
        case 1:
            doSomethingAfter(supensionResult)
            label = -1 // execution is over
            return "Done" // we have result of execution
        }
    }
}
---------------------------------------------------------------------------------------------
*/