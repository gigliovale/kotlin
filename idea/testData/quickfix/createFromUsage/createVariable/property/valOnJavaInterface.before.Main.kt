// "Create property 'foo'" "true"
// ERROR: Unresolved reference: foo

fun test(a: A): String? {
    return a.<caret>foo
}