// TODO: muted automatically, investigate should it be ran for JS or not
// IGNORE_BACKEND: JS

// WITH_REFLECT
// FILE: J.java

public class J {
    void foo(String s, int i) {}

    static void bar(J j) {}
}

// FILE: K.kt

import kotlin.test.assertEquals

fun box(): String {
    if (isJDK8()) {
        assertEquals(listOf(null, "arg0", "arg1"), J::foo.parameters.map { it.name })
        assertEquals(listOf("arg0"), J::bar.parameters.map { it.name })
    }
    else {
        assertEquals(listOf(null, null, null), J::foo.parameters.map { it.name })
        assertEquals(listOf(null), J::bar.parameters.map { it.name })
    }
    return "OK"
}

private fun isJDK8() = getJavaVersion() >= 0x10008

private fun getJavaVersion(): Int {
    val default = 0x10006
    val version = System.getProperty("java.specification.version") ?: return default
    val components = version.split('.')
    return try {
        when (components.size) {
            0 -> default
            1 -> components[0].toInt() * 0x10000
            else -> components[0].toInt() * 0x10000 + components[1].toInt()
        }
    } catch (e: NumberFormatException) {
        default
    }
}
