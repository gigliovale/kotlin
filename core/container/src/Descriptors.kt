package org.jetbrains.container

import java.util.*


public trait ValueDescriptor
{
    fun getValue(): Any
}

public trait ComponentDescriptor : ValueDescriptor
{
    fun getRegistrations(): Iterable<Class<*>>
}

public class ObjectComponentDescriptor(val instance: Any) : ComponentDescriptor {
    override fun getValue(): Any = instance
    override fun getRegistrations(): Iterable<Class<*>> {
        val list = ArrayList<Class<*>>()
        list.addAll(instance.javaClass.getInterfaces())
        val superClasses = sequence<Class<*>>(instance.javaClass) { it.getGenericSuperclass() as? Class<*> }
        list.addAll(superClasses)
        return list
    }
}
