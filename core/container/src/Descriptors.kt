package org.jetbrains.container

import java.lang.reflect.Method
import java.lang.reflect.ParameterizedType
import java.util.*


public trait ValueDescriptor {
    public fun getValue(): Any
}

public trait ComponentDescriptor : ValueDescriptor {
    fun getRegistrations(): Iterable<Class<*>>

    fun injectProperties(context: ValueResolveContext, callback: (ComponentDescriptor) -> Unit) {
        val instance = getValue()
        val type = instance.javaClass
        val injectors = HashSet<Method>()
        for (member in type.getMethods()) {
            val annotations = member.getDeclaredAnnotations()
            for (annotation in annotations) {
                val annotationType = annotation.annotationType()
                if (annotationType.getName().substringAfterLast('.') == "Inject") {
                    injectors.add(member)
                }
            }
        }

        injectors.forEach { injector ->
            val methodBinding = injector.bindToMethod(instance, context)
            methodBinding.argumentDescriptors.filterIsInstance<ComponentDescriptor>().forEach(callback)
            methodBinding.invoke()
        }
    }
}

public class ObjectComponentDescriptor(val instance: Any) : ComponentDescriptor {

    override fun getValue(): Any = instance
    override fun getRegistrations(): Iterable<Class<*>> {
        val klass = instance.javaClass
        return getRegistrationsForClass(klass)
    }
}

private fun recCollectInterfaces(cl: Class<*>, result: MutableCollection<Class<*>>) {
    val interfaces = cl.getInterfaces()
    interfaces.forEach {
        if (it !in result) {
            result.add(it)
            recCollectInterfaces(it, result)
        }
    }
}

private fun getRegistrationsForClass(klass: Class<*>): List<Class<*>> {
    val list = ArrayList<Class<*>>()
    val superClasses = sequence<Class<*>>(klass) {
        val superclass = it.getGenericSuperclass()
        when (superclass) {
            is ParameterizedType -> superclass.getRawType() as? Class<*>
            is Class<*> -> superclass
            else -> null
        }
        // todo: do not publish as Object
    }.toList()
    list.addAll(superClasses)
    val interfaces = LinkedHashSet<Class<*>>()
    superClasses.forEach { recCollectInterfaces(it, interfaces) }
    list.addAll(interfaces)
    return list
}