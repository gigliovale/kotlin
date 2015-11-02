abstract class Runnable(val arg: Int) {
    abstract fun run(): Short
}

fun println(arg: Short) = arg

fun foo(): Short {
    val a: Int? = 1
    return object: Runnable(a!!) {
        // Constructor argument should provide smart cast here
        override fun run() = <!DEBUG_INFO_SMARTCAST!>a<!>.toShort()
        init {
            // And also here
            println(<!DEBUG_INFO_SMARTCAST!>a<!>.toShort())
        }
    }.run()
}
