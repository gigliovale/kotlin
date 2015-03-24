fun calc(x: List<String>?) {
    x?.get(<!DEBUG_INFO_SMARTCAST!>x<!>.size() - 1) // x should be non-null in arguments list
}
