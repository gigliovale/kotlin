// PSI_ELEMENT: org.jetbrains.kotlin.psi.KtNamedFunction
// OPTIONS: usages

class A {
    fun test() {
        foo()
    }

    companion object {
        private fun f<caret>oo() {}
    }
}
