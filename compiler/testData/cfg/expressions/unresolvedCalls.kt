fun test(a: Any) {
    foo
    foo(1, 2)
    a.foo(1, 2)
    foo[0, 1]
    ++foo
    foo + 1
    object O: Foo(1, 2)
}