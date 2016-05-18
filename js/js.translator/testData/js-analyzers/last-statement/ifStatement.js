function box() {
    if (foo()) {
        bar(); /*final*/
    }
    else {
        baz(); /*final*/
    }
}