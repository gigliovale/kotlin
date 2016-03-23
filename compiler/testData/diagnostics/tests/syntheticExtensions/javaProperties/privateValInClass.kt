// FILE: J.java
public class J {
    String getName() { return ""; }
}

// FILE: 1.kt
class K(private val name: String): J() {
    override fun getName() = name
}