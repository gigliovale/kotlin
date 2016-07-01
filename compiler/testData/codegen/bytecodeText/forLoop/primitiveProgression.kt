fun f() {
    for (i in 0..5 step 2) {
    }

    val dt = 5 downTo 1 // suppress optimized code generation for 'for-in-downTo'
    for (i in dt) {
    }
}

// 0 iterator
// 2 getFirst
// 2 getLast
// 2 getStep