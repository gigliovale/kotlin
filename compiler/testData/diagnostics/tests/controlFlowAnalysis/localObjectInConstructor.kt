fun println(obj: Any?) = obj

class Demo0 {
    private val some = object {
        fun foo() {
            println(<!UNINITIALIZED_VARIABLE!>state<!>)
        }
    }

    private var state: Boolean = true
}

class Demo1 {
    private val some = object {
        fun foo() {
            if (<!UNINITIALIZED_VARIABLE!>state<!>)
                state = true

            println(<!UNINITIALIZED_VARIABLE!>state<!>)
        }
    }

    private var state: Boolean = true
}

class Demo1A {
    fun foo() {
        if (state)
            state = true

        println(state)
    }

    private var state: Boolean = true
}

class Demo2 {
    private val some = object {
        fun foo() {
            if (<!UNINITIALIZED_VARIABLE!>state<!>)
                state = true
            else
                state = false

            println(state)
        }
    }

    private var state: Boolean = true
}

class Demo3 {
    private val some = run {
        // Error is not reported here because we have here dispatch receiver that is an ExtensionReceiver
        // (but should be "this" or "none" ==> see isThisOrNoDispatchReceiver)
        if (state)
            state = true

        println(state)
    }

    private var state: Boolean = true
}

fun <T> exec(f: () -> T): T = f()

class Demo4 {
    private val some = exec {
        if (<!UNINITIALIZED_VARIABLE!>state<!>)
            state = true

        println(<!UNINITIALIZED_VARIABLE!>state<!>)
    }

    private var state: Boolean = true
}

class Demo5 {
    private var state: Boolean = true

    private val some = object {
        fun foo() {
            if (state)
                state = true

            println(state)
        }
    }
}
