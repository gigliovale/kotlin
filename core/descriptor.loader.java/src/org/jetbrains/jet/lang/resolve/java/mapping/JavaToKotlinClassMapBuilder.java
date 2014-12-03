/*
 * Copyright 2010-2013 JetBrains s.r.o.
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

package org.jetbrains.jet.lang.resolve.java.mapping;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jet.lang.descriptors.ClassDescriptor;
import org.jetbrains.jet.lang.types.lang.KotlinBuiltIns;

import java.lang.annotation.Annotation;
import java.util.*;

public abstract class JavaToKotlinClassMapBuilder {

    public enum Direction {
        JAVA_TO_KOTLIN,
        KOTLIN_TO_JAVA,
        BOTH
    }

    protected void init(KotlinBuiltIns builtIns) {

        register(Object.class, builtIns.getAny());
        register(String.class, builtIns.getString());
        register(CharSequence.class, builtIns.getCharSequence());
        register(Throwable.class, builtIns.getThrowable());
        register(Cloneable.class, builtIns.getCloneable());
        register(Number.class, builtIns.getNumber());
        register(Comparable.class, builtIns.getComparable());
        register(Enum.class, builtIns.getEnum());
        register(Annotation.class, builtIns.getAnnotation());
        register(Deprecated.class, builtIns.getDeprecatedAnnotation(), Direction.JAVA_TO_KOTLIN);
        register(Void.class, builtIns.getNothing(), Direction.KOTLIN_TO_JAVA);

        register(Iterable.class, builtIns.getIterable(), builtIns.getMutableIterable());
        register(Iterator.class, builtIns.getIterator(), builtIns.getMutableIterator());
        register(Collection.class, builtIns.getCollection(), builtIns.getMutableCollection());
        register(List.class, builtIns.getList(), builtIns.getMutableList());
        register(Set.class, builtIns.getSet(), builtIns.getMutableSet());
        register(Map.class, builtIns.getMap(), builtIns.getMutableMap());
        register(Map.Entry.class, builtIns.getMapEntry(), builtIns.getMutableMapEntry());
        register(ListIterator.class, builtIns.getListIterator(), builtIns.getMutableListIterator());
    }

    /*package*/ void register(@NotNull Class<?> javaClass, @NotNull ClassDescriptor kotlinDescriptor) {
        register(javaClass, kotlinDescriptor, Direction.BOTH);
    }
    protected abstract void register(@NotNull Class<?> javaClass, @NotNull ClassDescriptor kotlinDescriptor, @NotNull Direction direction);

    /*package*/ void register(@NotNull Class<?> javaClass, @NotNull ClassDescriptor kotlinDescriptor, @NotNull ClassDescriptor kotlinMutableDescriptor) {
         register(javaClass, kotlinDescriptor, kotlinMutableDescriptor, Direction.BOTH);
    }
    protected abstract void register(@NotNull Class<?> javaClass, @NotNull ClassDescriptor kotlinDescriptor, @NotNull ClassDescriptor kotlinMutableDescriptor, @NotNull Direction direction);
}
