// LANGUAGE_VERSION: 1.0
// FILE: Base.java

public interface Base {
    String getValue();

    default String test() {
        return getValue();
    }
}

// FILE: main.kt
class Fail : Base {
    override fun getValue() = "OK"
}

fun box(): String {
    val z = object : Base by Fail() {
        override fun getValue() = "Fail"
    }
    return z.test()
}
