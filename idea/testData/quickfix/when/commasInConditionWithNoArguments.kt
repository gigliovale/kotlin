// "Replace ',' with '||' in when-condition" "true"
fun test(i: Int, j: Int) {
    var b = false
    when {
        i > 0<caret>, j > 0 -> { /* some code */ }
        else -> { /* other code */ }
    }
}