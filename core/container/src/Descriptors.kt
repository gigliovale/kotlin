package org.jetbrains.container

import java.lang.reflect.Method
import java.lang.reflect.ParameterizedType
import java.util.*


public trait ValueDescriptor {
    fun getValue(): Any
}

public trait ComponentDescriptor : ValueDescriptor {
    fun getRegistrations(): Iterable<Class<*>>

    fun injectProperties(context: ValueResolveContext) {
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

        for (injector in injectors) {
            val methodBinding = injector.bindToMethod(instance, context)
            methodBinding.invoke()
        }
    }
}

public class ObjectComponentDescriptor(val instance: Any) : ComponentDescriptor {

    override fun getValue(): Any = instance
    override fun getRegistrations(): Iterable<Class<*>> {
        val list = ArrayList<Class<*>>()
        list.addAll(instance.javaClass.getInterfaces())
        val superClasses = sequence<Class<*>>(instance.javaClass) {
            val superclass = it.getGenericSuperclass()
            when (superclass) {
                is ParameterizedType -> superclass.getRawType() as? Class<*>
                is Class<*> -> superclass
                else -> null
            }
            // todo: do not publish as Object
        }
        list.addAll(superClasses)
        return list
    }
}
