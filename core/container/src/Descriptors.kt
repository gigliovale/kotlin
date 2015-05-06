package org.jetbrains.container

import java.lang.reflect.ParameterizedType
import java.util.ArrayList
import java.util.LinkedHashSet


public trait ValueDescriptor {
    public fun getValue(): Any
}

public trait ComponentDescriptor : ValueDescriptor {
    fun getRegistrations(): Iterable<Class<*>>
    fun getDependencies(context: ValueResolveContext): Collection<Class<*>>
}