function box() {
    before();
    if (test1() && test2()) {
        if (test3()) {
            body();
        }
    }
    after();
}