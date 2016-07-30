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

package org.jetbrains.kotlin.storage;

import kotlin.jvm.functions.Function0;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;

public class StorageUtils {
    @NotNull
    static <T extends Throwable> T sanitizeStackTrace(@NotNull T throwable) {
        String storagePackageName = LockBasedStorageManager.class.getPackage().getName();
        StackTraceElement[] stackTrace = throwable.getStackTrace();
        int size = stackTrace.length;

        int firstNonStorage = -1;
        for (int i = 0; i < size; i++) {
            // Skip everything (memoized functions and lazy values) from package org.jetbrains.kotlin.storage
            if (!stackTrace[i].getClassName().startsWith(storagePackageName)) {
                firstNonStorage = i;
                break;
            }
        }
        assert firstNonStorage >= 0 : "This method should only be called on exceptions created in LockBasedStorageManager";

        List<StackTraceElement> list = Arrays.asList(stackTrace).subList(firstNonStorage, size);
        throwable.setStackTrace(list.toArray(new StackTraceElement[list.size()]));
        return throwable;
    }

    enum NotValue {
        NOT_COMPUTED,
        COMPUTING,
        RECURSION_WAS_DETECTED,
        NULL
    }

    // equals and hashCode use only key
    static class KeyWithComputation<K, V> {
        private final K key;
        final Function0<? extends V> computation;

        public KeyWithComputation(K key, Function0<? extends V> computation) {
            this.key = key;
            this.computation = computation;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            KeyWithComputation<?, ?> that = (KeyWithComputation<?, ?>) o;

            if (!key.equals(that.key)) return false;

            return true;
        }

        @Override
        public int hashCode() {
            return key.hashCode();
        }
    }
}
