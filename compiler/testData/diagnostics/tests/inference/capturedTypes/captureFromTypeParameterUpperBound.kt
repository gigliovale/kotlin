interface Inv<T>

fun <X : Inv<out String>> foo(x: X) {
    val r = bar(x)
    r.length
}

fun <Y> bar(<!UNUSED_PARAMETER!>l<!>: Inv<Y>): Y = TODO()