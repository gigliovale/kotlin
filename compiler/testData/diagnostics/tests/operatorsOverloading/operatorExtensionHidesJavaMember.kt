// !DIAGNOSTICS: -UNUSED_PARAMETER
// FILE: J.java
public class J {
    public boolean get(int key) { return false; }
}

// FILE: K.kt
operator fun J.get(key: Int): String = "OK"

fun test(j: J): String =
        j[0]