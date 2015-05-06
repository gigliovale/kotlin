package org.jetbrains.container

import com.intellij.util.containers.MultiMap
import java.util.*

public fun ComponentRegisterEntry(value: ComponentRegisterEntry): ComponentRegisterEntry {
    val entry = ComponentRegisterEntry()
    entry.descriptors.addAll(value.descriptors)
    return entry
}


class ComponentRegisterEntry() : Iterable<ComponentDescriptor> {
    val descriptors: MutableList<ComponentDescriptor> = ArrayList()

    override fun iterator(): Iterator<ComponentDescriptor> = descriptors.iterator()

    public fun singleOrNull(): ComponentDescriptor? {
        if (descriptors.size() == 1)
            return descriptors[0]
        else if (descriptors.size() == 0)
            return null

        throw UnresolvedDependenciesException("Invalid arity")
    }

    public fun add(item: ComponentDescriptor) {
        descriptors.add(item)
    }

    public fun addAll(items: Collection<ComponentDescriptor>) {
        descriptors.addAll(items)
    }

    public fun remove(item: ComponentDescriptor) {
        descriptors.remove(item)
    }

    public fun removeAll(items: Collection<ComponentDescriptor>) {
        descriptors.removeAll(items)
    }
}

internal class ComponentRegistry {
    fun buildRegistrationMap(descriptors: Collection<ComponentDescriptor>): MultiMap<Class<*>, ComponentDescriptor> {
        val registrationMap = MultiMap<Class<*>, ComponentDescriptor>()
        for (descriptor in descriptors) {
            for (registration in descriptor.getRegistrations()) {
                registrationMap.putValue(registration, descriptor)
            }
        }
        return registrationMap
    }

    private var registrationMap = MultiMap.createLinkedSet<Class<*>, ComponentDescriptor>()

    public fun addAll(descriptors: Collection<ComponentDescriptor>) {
        registrationMap.putAllValues(buildRegistrationMap(descriptors))
    }

    public fun tryGetEntry(request: Class<*>): Collection<ComponentDescriptor> {
        return registrationMap.get(request)
    }
}