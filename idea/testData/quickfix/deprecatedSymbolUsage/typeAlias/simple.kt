// "Replace with 'StringList'" "true"

@Deprecated("", ReplaceWith("StringList"))
typealias TypeAlias = List<String>

class StringList

fun foo(): TypeAlias<caret>? {
    return null
}
