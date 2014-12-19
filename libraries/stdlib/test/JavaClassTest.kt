package testjc

import kotlin.test.*
import junit.framework.TestCase

class C()

class JavaClassTest() : TestCase() {
    fun testMe () {
        assertEquals("java.util.ArrayList", java.util.ArrayList<Any>().javaClass.getName())
        assertEquals("java.util.ArrayList", javaClass<java.util.ArrayList<*>>().getName())
        assertEquals("testjc.C", javaClass<C>().getName())
    }
}