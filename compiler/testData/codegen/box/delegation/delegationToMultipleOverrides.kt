// WITH_RUNTIME

import kotlin.test.assertEquals

interface IBase {
    fun foo(): String
}

interface IDerived : IBase {
    fun bar(): String
}

class C1(val b: IBase): IBase by b, IDerived {
    override fun bar() = "C1.bar"
}

class C2(val b: IBase): IDerived, IBase by b {
    override fun bar() = "C2.bar"
}

object Delegate : IBase {
    override fun foo() = "Delegate.foo"
}

fun testBaseFoo(b: IBase, expected: String) {
    assertEquals(expected, b.foo())
}

fun testDerivedFoo(d: IDerived, expected: String) {
    assertEquals(expected, d.foo())
}

fun testDerivedBar(d: IDerived, expected: String) {
    assertEquals(expected, d.bar())
}

fun box(): String {
    testBaseFoo(C1(Delegate), "Delegate.foo")
    testDerivedFoo(C1(Delegate), "Delegate.foo")
    testDerivedBar(C1(Delegate), "C1.bar")

    testBaseFoo(C2(Delegate), "Delegate.foo")
    testDerivedFoo(C2(Delegate), "Delegate.foo")
    testDerivedBar(C2(Delegate), "C2.bar")

    return "OK"
}