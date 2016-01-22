package demo

inline fun myRun(f: () -> Unit) {
    println("Enter myRun")
    f()
    println("Exit myRun")
}
