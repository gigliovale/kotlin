// FILE: KotlinFile.kt
fun foo(javaInterface: JavaInterface) {
    javaInterface.<!NONE_APPLICABLE!>doIt<!>(null) { }
    javaInterface.<!NONE_APPLICABLE!>doIt<!>("", null)
}

// FILE: JavaInterface.java
import org.jetbrains.annotations.*;

public interface JavaInterface {
    void doIt(@NotNull String s, @NotNull Runnable runnable);
}