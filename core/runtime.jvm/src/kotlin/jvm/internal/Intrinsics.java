/*
 * Copyright 2010-2015 JetBrains s.r.o.
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

package kotlin.jvm.internal;

import kotlin.KotlinNullPointerException;
import kotlin.UninitializedPropertyAccessException;

import java.util.Arrays;
import java.util.List;

@SuppressWarnings({"unused", "deprecation"})
@Deprecated
@kotlin.Deprecated(message = "This class supports the compiler infrastructure and is not intended to be used directly from user code.", level = kotlin.DeprecationLevel.HIDDEN)
public class Intrinsics {
    private Intrinsics() {
    }

    @Deprecated
    @kotlin.Deprecated(message = "This function supports the compiler infrastructure and is not intended to be used directly from user code.", level = kotlin.DeprecationLevel.HIDDEN)
    public static String stringPlus(String self, Object other) {
        return self + other;
    }

    @Deprecated
    @kotlin.Deprecated(message = "This function supports the compiler infrastructure and is not intended to be used directly from user code.", level = kotlin.DeprecationLevel.HIDDEN)
    public static void checkNotNull(Object object) {
        if (object == null) {
            throwNpe();
        }
    }

    @Deprecated
    @kotlin.Deprecated(message = "This function supports the compiler infrastructure and is not intended to be used directly from user code.", level = kotlin.DeprecationLevel.HIDDEN)
    public static void checkNotNull(Object object, String message) {
        if (object == null) {
            throwNpe(message);
        }
    }

    @Deprecated
    @kotlin.Deprecated(message = "This function supports the compiler infrastructure and is not intended to be used directly from user code.", level = kotlin.DeprecationLevel.HIDDEN)
    public static void throwNpe() {
        throw sanitizeStackTrace(new KotlinNullPointerException());
    }

    @Deprecated
    @kotlin.Deprecated(message = "This function supports the compiler infrastructure and is not intended to be used directly from user code.", level = kotlin.DeprecationLevel.HIDDEN)
    public static void throwNpe(String message) {
        throw sanitizeStackTrace(new KotlinNullPointerException(message));
    }

    @Deprecated
    @kotlin.Deprecated(message = "This function supports the compiler infrastructure and is not intended to be used directly from user code.", level = kotlin.DeprecationLevel.HIDDEN)
    public static void throwUninitializedProperty(String message) {
        throw sanitizeStackTrace(new UninitializedPropertyAccessException(message));
    }

    @Deprecated
    @kotlin.Deprecated(message = "This function supports the compiler infrastructure and is not intended to be used directly from user code.", level = kotlin.DeprecationLevel.HIDDEN)
    public static void throwUninitializedPropertyAccessException(String propertyName) {
        throwUninitializedProperty("lateinit property " + propertyName + " has not been initialized");
    }

    @Deprecated
    @kotlin.Deprecated(message = "This function supports the compiler infrastructure and is not intended to be used directly from user code.", level = kotlin.DeprecationLevel.HIDDEN)
    public static void throwAssert() {
        throw sanitizeStackTrace(new AssertionError());
    }

    @Deprecated
    @kotlin.Deprecated(message = "This function supports the compiler infrastructure and is not intended to be used directly from user code.", level = kotlin.DeprecationLevel.HIDDEN)
    public static void throwAssert(String message) {
        throw sanitizeStackTrace(new AssertionError(message));
    }

    @Deprecated
    @kotlin.Deprecated(message = "This function supports the compiler infrastructure and is not intended to be used directly from user code.", level = kotlin.DeprecationLevel.HIDDEN)
    public static void throwIllegalArgument() {
        throw sanitizeStackTrace(new IllegalArgumentException());
    }

    @Deprecated
    @kotlin.Deprecated(message = "This function supports the compiler infrastructure and is not intended to be used directly from user code.", level = kotlin.DeprecationLevel.HIDDEN)
    public static void throwIllegalArgument(String message) {
        throw sanitizeStackTrace(new IllegalArgumentException(message));
    }

    @Deprecated
    @kotlin.Deprecated(message = "This function supports the compiler infrastructure and is not intended to be used directly from user code.", level = kotlin.DeprecationLevel.HIDDEN)
    public static void throwIllegalState() {
        throw sanitizeStackTrace(new IllegalStateException());
    }

    @Deprecated
    @kotlin.Deprecated(message = "This function supports the compiler infrastructure and is not intended to be used directly from user code.", level = kotlin.DeprecationLevel.HIDDEN)
    public static void throwIllegalState(String message) {
        throw sanitizeStackTrace(new IllegalStateException(message));
    }

    @Deprecated
    @kotlin.Deprecated(message = "This function supports the compiler infrastructure and is not intended to be used directly from user code.", level = kotlin.DeprecationLevel.HIDDEN)
    public static void checkExpressionValueIsNotNull(Object value, String expression) {
        if (value == null) {
            throw sanitizeStackTrace(new IllegalStateException(expression + " must not be null"));
        }
    }

    @Deprecated
    @kotlin.Deprecated(message = "This function supports the compiler infrastructure and is not intended to be used directly from user code.", level = kotlin.DeprecationLevel.HIDDEN)
    public static void checkNotNullExpressionValue(Object value, String message) {
        if (value == null) {
            throw sanitizeStackTrace(new IllegalStateException(message));
        }
    }

    @Deprecated
    @kotlin.Deprecated(message = "This function supports the compiler infrastructure and is not intended to be used directly from user code.", level = kotlin.DeprecationLevel.HIDDEN)
    public static void checkReturnedValueIsNotNull(Object value, String className, String methodName) {
        if (value == null) {
            throw sanitizeStackTrace(
                    new IllegalStateException("Method specified as non-null returned null: " + className + "." + methodName)
            );
        }
    }

    @Deprecated
    @kotlin.Deprecated(message = "This function supports the compiler infrastructure and is not intended to be used directly from user code.", level = kotlin.DeprecationLevel.HIDDEN)
    public static void checkReturnedValueIsNotNull(Object value, String message) {
        if (value == null) {
            throw sanitizeStackTrace(new IllegalStateException(message));
        }
    }

    @Deprecated
    @kotlin.Deprecated(message = "This function supports the compiler infrastructure and is not intended to be used directly from user code.", level = kotlin.DeprecationLevel.HIDDEN)
    public static void checkFieldIsNotNull(Object value, String className, String fieldName) {
        if (value == null) {
            throw sanitizeStackTrace(new IllegalStateException("Field specified as non-null is null: " + className + "." + fieldName));
        }
    }

    @Deprecated
    @kotlin.Deprecated(message = "This function supports the compiler infrastructure and is not intended to be used directly from user code.", level = kotlin.DeprecationLevel.HIDDEN)
    public static void checkFieldIsNotNull(Object value, String message) {
        if (value == null) {
            throw sanitizeStackTrace(new IllegalStateException(message));
        }
    }

    @Deprecated
    @kotlin.Deprecated(message = "This function supports the compiler infrastructure and is not intended to be used directly from user code.", level = kotlin.DeprecationLevel.HIDDEN)
    public static void checkParameterIsNotNull(Object value, String paramName) {
        if (value == null) {
            throwParameterIsNullException(paramName);
        }
    }

    @Deprecated
    @kotlin.Deprecated(message = "This function supports the compiler infrastructure and is not intended to be used directly from user code.", level = kotlin.DeprecationLevel.HIDDEN)
    public static void checkNotNullParameter(Object value, String message) {
        if (value == null) {
            throw sanitizeStackTrace(new IllegalArgumentException(message));
        }
    }

    private static void throwParameterIsNullException(String paramName) {
        StackTraceElement[] stackTraceElements = Thread.currentThread().getStackTrace();

        // #0 Thread.getStackTrace()
        // #1 Intrinsics.throwParameterIsNullException
        // #2 Intrinsics.checkParameterIsNotNull
        // #3 our caller
        StackTraceElement caller = stackTraceElements[3];
        String className = caller.getClassName();
        String methodName = caller.getMethodName();

        IllegalArgumentException exception =
                new IllegalArgumentException("Parameter specified as non-null is null: " +
                                             "method " + className + "." + methodName +
                                             ", parameter " + paramName);
        throw sanitizeStackTrace(exception);
    }

    @Deprecated
    @kotlin.Deprecated(message = "This function supports the compiler infrastructure and is not intended to be used directly from user code.", level = kotlin.DeprecationLevel.HIDDEN)
    public static int compare(long thisVal, long anotherVal) {
        return thisVal < anotherVal ? -1 : thisVal == anotherVal ? 0 : 1;
    }

    @Deprecated
    @kotlin.Deprecated(message = "This function supports the compiler infrastructure and is not intended to be used directly from user code.", level = kotlin.DeprecationLevel.HIDDEN)
    public static int compare(int thisVal, int anotherVal) {
        return thisVal < anotherVal ? -1 : thisVal == anotherVal ? 0 : 1;
    }

    @Deprecated
    @kotlin.Deprecated(message = "This function supports the compiler infrastructure and is not intended to be used directly from user code.", level = kotlin.DeprecationLevel.HIDDEN)
    public static boolean areEqual(Object first, Object second) {
        return first == null ? second == null : first.equals(second);
    }

    @Deprecated
    @kotlin.Deprecated(message = "This function supports the compiler infrastructure and is not intended to be used directly from user code.", level = kotlin.DeprecationLevel.HIDDEN)
    public static void throwUndefinedForReified() {
        throwUndefinedForReified(
                "This function has a reified type parameter and thus can only be inlined at compilation time, not called directly."
        );
    }

    @Deprecated
    @kotlin.Deprecated(message = "This function supports the compiler infrastructure and is not intended to be used directly from user code.", level = kotlin.DeprecationLevel.HIDDEN)
    public static void throwUndefinedForReified(String message) {
        throw new UnsupportedOperationException(message);
    }

    @Deprecated
    @kotlin.Deprecated(message = "This function supports the compiler infrastructure and is not intended to be used directly from user code.", level = kotlin.DeprecationLevel.HIDDEN)
    public static void reifiedOperationMarker(int id, String typeParameterIdentifier) {
        throwUndefinedForReified();
    }

    @Deprecated
    @kotlin.Deprecated(message = "This function supports the compiler infrastructure and is not intended to be used directly from user code.", level = kotlin.DeprecationLevel.HIDDEN)
    public static void reifiedOperationMarker(int id, String typeParameterIdentifier, String message) {
        throwUndefinedForReified(message);
    }

    @Deprecated
    @kotlin.Deprecated(message = "This function supports the compiler infrastructure and is not intended to be used directly from user code.", level = kotlin.DeprecationLevel.HIDDEN)
    public static void needClassReification() {
        throwUndefinedForReified();
    }

    @Deprecated
    @kotlin.Deprecated(message = "This function supports the compiler infrastructure and is not intended to be used directly from user code.", level = kotlin.DeprecationLevel.HIDDEN)
    public static void needClassReification(String message) {
        throwUndefinedForReified(message);
    }

    @Deprecated
    @kotlin.Deprecated(message = "This function supports the compiler infrastructure and is not intended to be used directly from user code.", level = kotlin.DeprecationLevel.HIDDEN)
    public static void checkHasClass(String internalName) throws ClassNotFoundException {
        String fqName = internalName.replace('/', '.');
        try {
            Class.forName(fqName);
        }
        catch (ClassNotFoundException e) {
            throw sanitizeStackTrace(new ClassNotFoundException(
                    "Class " + fqName + " is not found. Please update the Kotlin runtime to the latest version", e
            ));
        }
    }

    @Deprecated
    @kotlin.Deprecated(message = "This function supports the compiler infrastructure and is not intended to be used directly from user code.", level = kotlin.DeprecationLevel.HIDDEN)
    public static void checkHasClass(String internalName, String requiredVersion) throws ClassNotFoundException {
        String fqName = internalName.replace('/', '.');
        try {
            Class.forName(fqName);
        }
        catch (ClassNotFoundException e) {
            throw sanitizeStackTrace(new ClassNotFoundException(
                    "Class " + fqName + " is not found: this code requires the Kotlin runtime of version at least " + requiredVersion, e
            ));
        }
    }

    private static <T extends Throwable> T sanitizeStackTrace(T throwable) {
        return sanitizeStackTrace(throwable, Intrinsics.class.getName());
    }

    static <T extends Throwable> T sanitizeStackTrace(T throwable, String classNameToDrop) {
        StackTraceElement[] stackTrace = throwable.getStackTrace();
        int size = stackTrace.length;

        int lastIntrinsic = -1;
        for (int i = 0; i < size; i++) {
            if (classNameToDrop.equals(stackTrace[i].getClassName())) {
                lastIntrinsic = i;
            }
        }

        List<StackTraceElement> list = Arrays.asList(stackTrace).subList(lastIntrinsic + 1, size);
        throwable.setStackTrace(list.toArray(new StackTraceElement[list.size()]));
        return throwable;
    }
}
