// p.Inheritor
package p

// annotatation added to test regression
annotation class Anno(vararg val s: String)

annotation class Bueno(anno: Anno)

class Inheritor: I, I2 {

    fun f() {

    }

    override fun g() {
    }
}

interface I : I1 {
    fun g()
}

interface I1 {
    @Bueno(Anno("G"))
    fun foo() = "foo"
}

interface I2 {
    @Anno("S")
    fun bar() = "bar"
}