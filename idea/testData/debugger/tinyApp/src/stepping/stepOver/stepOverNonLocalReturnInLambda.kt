package stepOverNonLocalReturnInLambda

fun main(args: Array<String>) {
    bar()
    val c = 1
}

fun bar() {
    //Breakpoint!
    val a = "aaa"
    synchronized(a) {
        if (a == "bbb") {
            return
        }
    }

    synchronized(a) {
        if (a == "aaa") {
            return
        }
    }

    val c = 1
}

// STEP_OVER: 4