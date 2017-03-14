package test

open class C

typealias CA = C

class D : <caret>CA()

//MULTIRESOLVE true
//REF: (test).C
//REF: (test).CA
