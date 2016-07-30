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

public class LocklessStorageManagerTest extends StorageManagerTest {
    @Override
    protected void setUp() throws Exception {
        super.setUp();
        m = new LocklessStorageManager(false);
    }

    public void testFallThrough() throws Exception {
        final CounterImpl c = new CounterImpl();
        class C {
            NotNullLazyValue<Integer> rec = LockBasedStorageManager.NO_LOCKS.createLazyValue(new Function0<Integer>() {
                @Override
                public Integer invoke() {
                    c.inc();
                    if (c.getCount() < 2) {
                        return rec.invoke();
                    }
                    else {
                        return c.getCount();
                    }
                }
            });
        }

        assertEquals(2, new C().rec.invoke().intValue());
        assertEquals(2, c.getCount());
    }
}
