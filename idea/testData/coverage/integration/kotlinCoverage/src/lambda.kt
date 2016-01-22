package demo

fun main(args: Array<String>) { // <<<caret
    noinlineRun {
        println(0)
        println(1)
        println(2)
    }
}
