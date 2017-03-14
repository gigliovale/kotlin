package test

class Some(val s: String)

typealias Same = Some

fun usage() {
    <caret>Same("")
}

//MULTIRESOLVE true
//REF: (in test.Some).Some(String)
//REF: (test).Same