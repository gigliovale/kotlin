inline fun <reified T> foo() {}

inline fun <reified R, N> test() {
    foo<String>()
    foo<Array<String>>()
    foo<Array<Array<String>>>()
    foo<Array<*>>()
    foo<Array<out Any?>>()
    foo<List<*>>()
    foo<Array<List<*>>>()
    foo<R>()
    foo<Array<R>>()

    foo<String?>()
    foo<Array<String?>?>()

    <!REIFIED_TYPE_FORBIDDEN_SUBSTITUTION!>foo<!><Nothing>()
    <!REIFIED_TYPE_FORBIDDEN_SUBSTITUTION!>foo<!><Array<out Any>>()
    <!REIFIED_TYPE_FORBIDDEN_SUBSTITUTION!>foo<!><Array<in String>>()
    <!REIFIED_TYPE_FORBIDDEN_SUBSTITUTION!>foo<!><List<String>>()
    <!REIFIED_TYPE_FORBIDDEN_SUBSTITUTION!>foo<!><List<R>>()
    <!REIFIED_TYPE_FORBIDDEN_SUBSTITUTION!>foo<!><Array<N>>()
    <!REIFIED_TYPE_FORBIDDEN_SUBSTITUTION!>foo<!><Array<Nothing>>()
    <!REIFIED_TYPE_FORBIDDEN_SUBSTITUTION!>foo<!><Array<Array<N>>>()
    <!REIFIED_TYPE_FORBIDDEN_SUBSTITUTION!>foo<!><Array<List<String>>>()
    <!REIFIED_TYPE_FORBIDDEN_SUBSTITUTION!>foo<!><List<Array<String>>>()
}

annotation class C(val a: Array<Class<*>>)
annotation class COut(val a: Array<out Class<*>>)
annotation class CStr(val a: Array<Class<String>>)
annotation class CVararg(vararg val a: Class<*>)

[C(array(javaClass<String>()))]
[COut(array(javaClass<String>()))]
[CStr(array(javaClass<String>()))]
[CVararg(*array(javaClass<String>()))]
[CVararg(javaClass<String>())]
fun annotationTest() {}

fun arrayTest() {
    <!REIFIED_TYPE_FORBIDDEN_SUBSTITUTION!>array<!>(javaClass<String>(), javaClass<Int>())
}
