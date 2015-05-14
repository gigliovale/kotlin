// "Move enum entries to the enum beginning" "true"

enum class MixedEnum {
    ENTRY1;
    companion object {
        val first = 1
    }
    // Syntax error because of the semicolon, another because of wrong order
    ENTRY2<caret>;
    fun foo(): String = "xyz"
    ENTRY3
}