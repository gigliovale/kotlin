// !DIAGNOSTICS: -UNUSED_PARAMETER

interface I

open class S(
        n: A.Nested,
        n2: A.Nested,
        inn: A.Inner,
        c: Int,
        cc: Int,
        cn: Int,
        ci: Int,
        t1: Int,
        t2: Int
) : I

class A : I by S(
        foo(),
        Nested(),
        <!INSTANCE_ACCESS_FROM_CLASS_DELEGATION!>Inner<!>(),
        CONST,
        Companion.CONST,
        Nested.CONST,
        Interface.CONST,
        <!INSTANCE_ACCESS_FROM_CLASS_DELEGATION, UNINITIALIZED_VARIABLE!>a<!>,
        <!INSTANCE_ACCESS_FROM_CLASS_DELEGATION!>b<!>()
) {

    class Nested {
        companion object {
            const val CONST = 2
        }
    }

    inner class Inner

    interface Interface {
        companion object {
            const val CONST = 3
        }
    }

    val a = 1
    fun b() = 2

    companion object {
        const val CONST = 1
        fun foo(): Nested = null!!
    }
}
