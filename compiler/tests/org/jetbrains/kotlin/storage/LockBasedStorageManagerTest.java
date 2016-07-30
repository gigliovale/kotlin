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

public class LockBasedStorageManagerTest extends StorageManagerTest {
    @Override
    public void setUp() throws Exception {
        super.setUp();
        m = new LockBasedStorageManager();
    }

    // toString()

    public void testToString() throws Exception {
        assertTrue("Should mention the setUp() method of this class: " + m.toString(),
                   m.toString().contains(getClass().getSimpleName() + ".setUp("));
    }

    public void testIsComputedAfterException() throws Exception {
        NotNullLazyValue<String> value = m.createLazyValue(new ExceptionCounterValue());
        assertFalse(value.isComputed());

        try {
            value.invoke();
        }
        catch (Exception ignored) {
        }

        assertTrue(value.isComputed());
    }

    public void testIsNullableComputedAfterException() throws Exception {
        NullableLazyValue<String> value = m.createNullableLazyValue(new ExceptionCounterValue());
        assertFalse(value.isComputed());

        try {
            value.invoke();
        }
        catch (Exception ignored) {
        }

        assertTrue(value.isComputed());
    }

    public void testNotNullLazyPreservesException() throws Exception {
        ExceptionCounterValue counter = new ExceptionCounterValue();
        NotNullLazyValue<String> value = m.createLazyValue(counter);
        doTestExceptionPreserved(value, UnsupportedOperationException.class, counter);
    }

    public void testNullableLazyPreservesException() throws Exception {
        ExceptionCounterValue counter = new ExceptionCounterValue();
        NullableLazyValue<String> value = m.createNullableLazyValue(counter);
        doTestExceptionPreserved(value, UnsupportedOperationException.class, counter);
    }

    public void testFunctionPreservesExceptions() throws Exception {
        ExceptionCounterFunction counter = new ExceptionCounterFunction();
        MemoizedFunctionToNotNull<String, String> f = m.createMemoizedFunction(counter);
        doTestExceptionPreserved(apply(f, ""), UnsupportedOperationException.class, counter);
    }

    public void testNullableFunctionPreservesExceptions() throws Exception {
        ExceptionCounterFunction counter = new ExceptionCounterFunction();
        MemoizedFunctionToNullable<String, String> f = m.createMemoizedFunctionWithNullableValues(counter);
        doTestExceptionPreserved(apply(f, ""), UnsupportedOperationException.class, counter);
    }

    private static class ExceptionCounterValue extends CounterValueNull {
        @Override
        public String invoke() {
            inc();
            throw new UnsupportedOperationException();
        }
    }

    private static class ExceptionCounterFunction extends CounterFunctionToNull {
        @Override
        public String invoke(String s) {
            inc();
            throw new UnsupportedOperationException();
        }
    }
}
