class My(var x: String) {

    var y: String
        get() = if (x != "") x else z
        set(arg) {
            if (arg != "") x = arg
        }

    val z: String

    var d: String = ""
        get
        set

    val z1: String

    init {
        d = "d"
        if (d != "") z1 = this.d else z1 = d

        // Dangerous: setter!
        <!DEBUG_INFO_LEAKING_THIS!>y<!> = "x"
        // Dangerous: getter!
        if (<!DEBUG_INFO_LEAKING_THIS!>y<!> != "") z = this.<!DEBUG_INFO_LEAKING_THIS!>y<!> else z = <!DEBUG_INFO_LEAKING_THIS!>y<!>
    }
}
