//WITH_RUNTIME
class ClueTextView {

    data class Style(
            val color: Int? = null,
            val underlined: Boolean? = null,
            val separator: String = ""
    )

    init {
        var secondaryTextUnderlined: Boolean? = null

        val a2: String = "123"
        try {
            a2.let { a2 ->
                secondaryTextUnderlined = false
            }
        } finally {
            a2.hashCode()
        }
        val style = Style(null, secondaryTextUnderlined, "123")
    }
}


fun box(): String {
    ClueTextView()

    return "OK"
}