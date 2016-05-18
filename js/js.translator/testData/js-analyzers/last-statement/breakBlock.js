function box(x) {
    block: {
        if (x) {
            foo(); /*final*/
            break block;
        }
        bar(); /*final*/
    }
}