data class SomeObject(val n: SomeObject?) {
    fun doSomething() {}
    fun next(): SomeObject? = n    
}


fun list(start: SomeObject) {
    var e: SomeObject? = start
    while (e != null) {
        <!DEBUG_INFO_SMARTCAST!>e<!>.doSomething()
        e = <!DEBUG_INFO_SMARTCAST!>e<!>.next()
    }
}