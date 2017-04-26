// !DIAGNOSTICS: -UNUSED_PARAMETER
// !CHECK_TYPE
// FILE: A.java
public class A {
    public static void foo(String x) {}
    public static void foo(A a, int e) {}
    private void foo(int y) {}
}

// FILE: main.kt

interface X
interface Y
object Z : X, Y

fun baz(x: (String) -> Unit, y: X) = 1
fun baz(x: (A, Int) -> Unit, y: Y) = ""

fun bar() {
    // TODO: there should be a resolution ambiguity (and it's reported to the trace, but it gets lost somewhere)
    // To prove that there is ambiguity there is a calls to both `String` and `Int` methods and neither of them is resolved
    baz(A::<!INVISIBLE_MEMBER!>foo<!>, Z).<!DEBUG_INFO_ELEMENT_WITH_ERROR_TYPE!>length<!>
    baz(A::<!INVISIBLE_MEMBER!>foo<!>, Z).<!DEBUG_INFO_ELEMENT_WITH_ERROR_TYPE!>toDouble<!>()
}
