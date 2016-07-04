// !DIAGNOSTICS: -UNUSED_PARAMETER -UNUSED_VARIABLE
// KT-12822 Warn if the expression body of a function is a closure
// See also: KT-5068 Add special error for scala-like syntax 'fun foo(): Int = { 1 }'

<!LAMBDA_USED_AS_BLOCK_EXPRESSION_IN_FUN!>fun testFunWarn()<!> = { 42 }
fun testFunOk() = { -> 42 }
fun testFunOk2(): () -> Int = { 42 }

val testFunLiteralWarn = <!LAMBDA_USED_AS_BLOCK_EXPRESSION_IN_FUN!>fun()<!> = { 42 }
val testFunLiteralOk = fun() = { -> 42 }
val testFunLiteralOk2 = fun(): () -> Int = { 42 }

fun outer() {
    <!LAMBDA_USED_AS_BLOCK_EXPRESSION_IN_FUN!>fun testFunWarn()<!> = { 42 }
    fun testFunOk() = { -> 42 }
    fun testFunOk2(): () -> Int = { 42 }

    val testFunLiteralWarn = <!LAMBDA_USED_AS_BLOCK_EXPRESSION_IN_FUN!>fun()<!> = { 42 }
    val testFunLiteralOk = fun() = { -> 42 }
    val testFunLiteralOk2 = fun(): () -> Int = { 42 }
}

class Outer {
    <!LAMBDA_USED_AS_BLOCK_EXPRESSION_IN_FUN!>fun testFunWarn()<!> = { 42 }
    fun testFunOk() = { -> 42 }
    fun testFunOk2(): () -> Int = { 42 }

    val testFunLiteralWarn = <!LAMBDA_USED_AS_BLOCK_EXPRESSION_IN_FUN!>fun()<!> = { 42 }
    val testFunLiteralOk = fun() = { -> 42 }
    val testFunLiteralOk2 = fun(): () -> Int = { 42 }

    class Nested {
        <!LAMBDA_USED_AS_BLOCK_EXPRESSION_IN_FUN!>fun testFunWarn()<!> = { 42 }
        fun testFunOk() = { -> 42 }
        fun testFunOk2(): () -> Int = { 42 }

        val testFunLiteralWarn = <!LAMBDA_USED_AS_BLOCK_EXPRESSION_IN_FUN!>fun()<!> = { 42 }
        val testFunLiteralOk = fun() = { -> 42 }
        val testFunLiteralOk2 = fun(): () -> Int = { 42 }
    }
}
