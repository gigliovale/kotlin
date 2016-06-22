class ExplicitAccessorForAnnotation {
    val tt: String? = "good"
        get

    fun foo(): String {
        if (tt is String) {
            return <!DEBUG_INFO_SMARTCAST!>tt<!>
        }
        return ""
    }
}