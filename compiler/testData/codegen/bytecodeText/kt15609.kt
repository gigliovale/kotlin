private fun doSomething() {}

fun test1() {
    try { // NOP: debugger invariant
        doSomething()
    } catch (e: Throwable) {
        throw e
    }
}


fun test2() {
    // NB no NOP in TCB
    try { doSomething() } catch (e: Throwable) {
        throw e
    }
}

// 1 NOP