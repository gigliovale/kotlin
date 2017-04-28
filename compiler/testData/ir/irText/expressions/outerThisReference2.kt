class Outer(val x: String) {
    inner class Inner(val y: String) {
        val z = x + y
    }
}