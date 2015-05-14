// "Move enum entries to the enum beginning" "true"

enum class MixedEnum {
    companion object {
        val first = 1
    }
    fun foo(): String = "xyz"
    ENTRY1<caret>
    ENTRY2
    ENTRY3
}