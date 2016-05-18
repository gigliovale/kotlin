function box() {
    if (foo()) {
        bar(); /*final*/
    }
    else {
        return 2;
    }
}