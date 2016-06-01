operator fun String.invoke() = this.hashCode()

class Test {
    var x: String

    val y: String

    init {
        x = "XYZ"
        x()
        y = "ABC"
    }
}
