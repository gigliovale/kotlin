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

abstract class CoroutineImpl(arity: Int) : Lambda(arity), InterceptableContinuation<Any?> {
    @JvmField
    protected var _resultContinuation: Continuation<*>? = null

    @JvmField
    protected var _resumeInterceptor: ResumeInterceptor? = null

    override val resumeInterceptor: ResumeInterceptor?
        get() = _resumeInterceptor

    // invoked from a coroutine-compiler-generated code after a new instance is created with the corresponding result continuation
    protected fun setResultContinuation(resultContinuation: Continuation<*>) {
        _resultContinuation = resultContinuation
        _resumeInterceptor = (resultContinuation as? InterceptableContinuation<*>)?.resumeInterceptor

    }

    // Any label state less then zero indicates that coroutine is not run and can't be resumed in any way.
    // Specific values do not matter by now, but currently -2 used for uninitialized coroutine (no controller is assigned),
    // and -1 will mean that coroutine execution is over (does not work yet).
    @JvmField
    protected var label: Int = -2

    override fun resume(data: Any?) {
        doResume(data, null)
    }

    override fun resumeWithException(exception: Throwable) {
        doResume(null, exception)
    }

    protected abstract fun doResume(data: Any?, exception: Throwable?)
}
