// "Move enum entries to the enum beginning" "true"

enum class MixedEnum {
    ENTRY1,
    companion object {
        val first = 1
    }
    ENTRY2<caret>,
    fun foo(): String = "xyz"
    ENTRY3
}