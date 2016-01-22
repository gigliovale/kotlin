package demo

fun noinlineRun(f: () -> Unit) {
    println("Enter myRun")
    f()
    println("Exit myRun")
}
