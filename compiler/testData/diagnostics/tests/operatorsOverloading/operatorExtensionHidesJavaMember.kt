// !DIAGNOSTICS: -UNUSED_PARAMETER
// FILE: J.java
public class J {
    public boolean get(int key) { return false; }
    public boolean get(String key) { return false; }
}

// FILE: K.kt
operator fun J.<!EXTENSION_SHADOWED_BY_MEMBER!>get<!>(key: Int): String = "OK"

fun test1(j: J): String =
        j[0]

fun test2(j: J): Boolean =
        j[""]

fun test3(j: J): Boolean =
        j.get(1)