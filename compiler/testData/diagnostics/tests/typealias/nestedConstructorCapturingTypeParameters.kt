// !DIAGNOSTICS: -UNUSED_VARIABLE -UNUSED_PARAMETER

class Pair<T1, T2>(val x1: T1, val x2: T2)

class C<X> {
    inner class Inner<Y>

    typealias P<Y> = Pair<X, Y>
}

val innerClassInstance: C<Int>.Inner<String> =
        C<Int>().Inner<String>()

// TODO support type alias constructors for type alias for non-inner classes capturing type parameters of outer class
// Currently resolve expects constructor invocation for 'C<Int>'.
val capturingTypeAliasInstance: Pair<Int, String> =
        <!FUNCTION_CALL_EXPECTED!>C<Int><!>.<!UNRESOLVED_REFERENCE!>P<!><String>(1, "")
