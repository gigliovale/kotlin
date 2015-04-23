package org.jetbrains.container

import java.lang.reflect.Constructor
import java.lang.reflect.Method
import java.lang.reflect.Type
import java.util.ArrayList

public trait ValueResolver
{
    fun resolve(request: Class<*>, context: ValueResolveContext): ValueDescriptor?
}

public trait ValueResolveContext
{
    fun resolve(registration: Class<*>): ValueDescriptor?
}

internal class ComponentResolveContext(val container: StorageComponentContainer, val requestingDescriptor: ValueDescriptor) : ValueResolveContext
{
    override fun resolve(registration: Class<*>): ValueDescriptor? = container.resolve(registration, this)
    public override fun toString(): String = "for $requestingDescriptor in $container"
}

fun ComponentContainer.createInstance(klass: Class<*>): Any {
    val context = createResolveContext(DynamicComponentDescriptor)
    return klass.bindToConstructor(context).createInstance()
}

public class ConstructorBinding(val constructor: Constructor<*>, val argumentDescriptors: List<ValueDescriptor>) {
    fun createInstance(): Any = constructor.createInstance(argumentDescriptors)
}

public class MethodBinding(val instance : Any, val method: Method, val argumentDescriptors: List<ValueDescriptor>) {
    fun invoke() {
        val arguments = bindArguments(argumentDescriptors).toTypedArray()
        method.invoke(instance, *arguments)
    }
}

fun Constructor<*>.createInstance(argumentDescriptors: List<ValueDescriptor>) = newInstance(bindArguments(argumentDescriptors))!!

public fun bindArguments(argumentDescriptors: List<ValueDescriptor>): List<Any> = argumentDescriptors.map { it.getValue() }

fun Class<*>.bindToConstructor(context: ValueResolveContext): ConstructorBinding
{
    val candidates = getConstructors()
    val resolved = ArrayList<ConstructorBinding>()
    val rejected = ArrayList<Pair<Constructor<*>, List<Type>>>()
    for (candidate in candidates)
    {
        val parameters = candidate.getParameterTypes()!!
        val arguments = ArrayList<ValueDescriptor>(parameters.size())
        var unsatisfied: MutableList<Type>? = null

        for (parameter in parameters)
        {
            val descriptor = context.resolve(parameter)
            if (descriptor == null)
            {
                if (unsatisfied == null)
                    unsatisfied = ArrayList<Type>()
                unsatisfied.add(parameter)
            } else {
                arguments.add(descriptor)
            }
        }

        if (unsatisfied == null) // constructor is satisfied with arguments
            resolved.add(ConstructorBinding(candidate, arguments))
        else
            rejected.add(candidate to unsatisfied)
    }

    if (resolved.size() != 1) {
        if (rejected.size() > 0)
            throw UnresolvedConstructorException("Unsatisfied constructor for type `$this` with these types:\n  ${rejected[0].second}")

        throw UnresolvedConstructorException("Cannot find suitable constructor for type `$this`")
    }

    return resolved[0]
}

fun Method.bindToMethod(instance : Any, context: ValueResolveContext): MethodBinding
{
    val resolved = ArrayList<MethodBinding>()
    val rejected = ArrayList<Pair<Method, List<Type>>>()
        val parameters = getParameterTypes()!!
        val arguments = ArrayList<ValueDescriptor>(parameters.size())
        var unsatisfied: MutableList<Type>? = null

        for (parameter in parameters)
        {
            val descriptor = context.resolve(parameter)
            if (descriptor == null)
            {
                if (unsatisfied == null)
                    unsatisfied = ArrayList<Type>()
                unsatisfied.add(parameter)
            } else {
                arguments.add(descriptor)
            }
        }

        if (unsatisfied == null) // constructor is satisfied with arguments
            resolved.add(MethodBinding(instance, this, arguments))
        else
            rejected.add(this to unsatisfied)

    if (resolved.size() != 1) {
        if (rejected.size() > 0)
            throw UnresolvedConstructorException("Unsatisfied injection method for type `$this` with these types:\n  ${rejected[0].second}")

        throw UnresolvedConstructorException("Cannot find suitable constructor for type `$this`")
    }

    return resolved[0]
}

class UnresolvedConstructorException(message: String) : Exception(message)

