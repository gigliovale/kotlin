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

package kotlin.coroutines.internal

import kotlin.coroutines.Continuation
import kotlin.coroutines.ContinuationDispatcher

/**
 * This is an internal interface that is an implementation detail of an approach to attach dispatcher to a running
 * coroutine. DO NOT USE THIS INTERFACE DIRECTLY. It **WILL BE** changed in the future.
 * There is a public API to install and to get coroutine's dispatcher.
 */
@PublishedApi
@SinceKotlin("1.1")
internal interface DispatchedContinuation<in T> : Continuation<T> {
    val dispatcher: ContinuationDispatcher?
}

@PublishedApi
@SinceKotlin("1.1")
internal fun <T> getDispatcher(continuation: Continuation<T>?): ContinuationDispatcher? =
        (continuation as? DispatchedContinuation<*>)?.dispatcher

internal fun <T> withDispatcher(completion: Continuation<T>, dispatcher: ContinuationDispatcher?): Continuation<T> {
    val oldDispatcher = getDispatcher(completion)
    if (dispatcher === oldDispatcher) return completion // a small optimization
    return object : DispatchedContinuation<T>, Continuation<T> by completion {
        override val dispatcher: ContinuationDispatcher? = dispatcher
    }
}
