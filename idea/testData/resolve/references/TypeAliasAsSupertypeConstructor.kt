package test

open class C

typealias CA = C

class D : <caret>CA()

//MULTIRESOLVE
//REF: (test).C
//REF: (test).CA
