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

package org.jetbrains.kotlin.di

import com.intellij.util.containers.ContainerUtil
import gnu.trove.TObjectHashingStrategy
import java.lang.reflect.Constructor
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.ArrayList
import java.util.LinkedHashSet


//TODO_R: package

private object ClassTraversalCache {
    //TODO_R: parameters
    private val cache = ContainerUtil.createConcurrentWeakKeySoftValueMap<Class<*>, ClassInfo>(50, 0.5f, 3, TObjectHashingStrategy.CANONICAL as TObjectHashingStrategy<Class<*>>)

    fun getClassInfo(c: Class<*>): ClassInfo {
        val classInfo = cache.get(c)
        if (classInfo == null) {
            val newClassInfo = traverseClass(c)
            cache.put(c, newClassInfo)
            return newClassInfo
        }
        return classInfo
    }
}

fun Class<*>.getInfo(): ClassInfo {
    return ClassTraversalCache.getClassInfo(this)
}

data class ClassInfo(
        val constructorInfo: ConstructorInfo?,
        val setterInfos: List<SetterInfo>,
        val registrations: List<Class<*>>
)

data class ConstructorInfo(
        val constructor: Constructor<*>,
        val parameters: List<Class<*>>
)

data class SetterInfo(
        val method: Method,
        val parameters: List<Class<*>>)

private fun traverseClass(c: Class<*>): ClassInfo {
    return ClassInfo(getConstructorInfo(c), getSetterInfos(c), getRegistrations(c))
}

private fun getSetterInfos(c: Class<*>): List<SetterInfo> {
    val setterInfos = ArrayList<SetterInfo>()
    for (member in c.getMethods()) {
        val annotations = member.getDeclaredAnnotations()
        for (annotation in annotations) {
            val annotationType = annotation.annotationType()
            if (annotationType.getName().substringAfterLast('.') == "Inject") {
                setterInfos.add(SetterInfo(member, member.getParameterTypes().toList()))
            }
        }
    }
    return setterInfos
}

private fun getConstructorInfo(c: Class<*>): ConstructorInfo? {
    val modifiers = c.getModifiers()
    if (Modifier.isInterface(modifiers) || Modifier.isAbstract(modifiers) || c.isPrimitive())
        return null

    val constructors = c.getConstructors()
    val hasSinglePublicConstructor = constructors.singleOrNull()?.let { Modifier.isPublic(it.getModifiers()) } ?: false
    if (!hasSinglePublicConstructor)
        return null


    val constructor = constructors.single()
    return ConstructorInfo(constructor, constructor.getParameterTypes().toList())
}


private fun collectInterfacesRecursive(cl: Class<*>, result: MutableSet<Class<*>>) {
    val interfaces = cl.getInterfaces()
    interfaces.forEach {
        if (result.add(it)) {
            collectInterfacesRecursive(it, result)
        }
    }
}

private fun getRegistrations(klass: Class<*>): List<Class<*>> {
    val registrations = ArrayList<Class<*>>()
    val superClasses = sequence(klass) { it: Class<*> ->
        (it as Class<Any>).getSuperclass()
    }
    registrations.addAll(superClasses)
    val interfaces = LinkedHashSet<Class<*>>()
    superClasses.forEach { collectInterfacesRecursive(it, interfaces) }
    registrations.addAll(interfaces)
    registrations.remove(javaClass<java.lang.Object>())
    return registrations
}