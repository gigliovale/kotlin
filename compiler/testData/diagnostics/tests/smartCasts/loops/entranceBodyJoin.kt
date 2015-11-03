class Node(val parent: Node?, val value: Int)

fun test1() {
    var node: Node? = null
    if (node == null) return
    do {
        // Should be Ok
        node = <!DEBUG_INFO_SMARTCAST!>node<!>.parent
    } while (node != null)
}

fun test2() {
    var node: Node? = null
    while (node != null) {
        // Should be Ok
        node = <!DEBUG_INFO_SMARTCAST!>node<!>.parent
    }
}

fun test3() {
    var node: Node? = null
    if (node == null) return

    // Node is not-null
    while (<!DEBUG_INFO_SMARTCAST!>node<!>.parent?.value != 0) {
        // Node is not-null also here
        node = <!DEBUG_INFO_SMARTCAST!>node<!>.parent!!
    }
}
