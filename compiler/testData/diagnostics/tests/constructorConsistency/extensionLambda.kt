class Test(extFun: Test.() -> String) {
    val x = <!DEBUG_INFO_LEAKING_THIS!>extFun<!>()
}

val kaboom = Test { x }.x // OOPS! kaboom is String but holds null value
