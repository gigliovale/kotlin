class Test {
    val a: String

    init {
        val t = object {
            fun some() {
                a = "12"
            }
        }

        a = "2"
        t.some()
    }
}

class Test2 {
    init {
        val t = object {
            fun some() {
                a = "12"
            }
        }

        a = "2"
        t.some()
    }

    val a: String
}
