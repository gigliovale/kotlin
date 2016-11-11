interface Interface

fun context() {
    val <caret>v = object : Interface {}
}

//INFO: <b>val</b> v: Interface <i>defined in</i> context
