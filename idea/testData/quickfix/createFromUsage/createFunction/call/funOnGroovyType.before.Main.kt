// "Create function 'foo'" "true"
// ERROR: Unresolved reference: foo

fun test(): Int {
    return A().<caret>foo()
}