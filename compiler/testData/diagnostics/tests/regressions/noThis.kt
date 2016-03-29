interface A { fun f() }

open class P(val z: B)

class B : A {
    override fun f() {}
    class C : A by <!INSTANCE_ACCESS_FROM_CLASS_DELEGATION!>this<!> {}
    class D(val x : B = <!NO_THIS!>this<!>)
    class E : P(<!NO_THIS!>this<!>)
}