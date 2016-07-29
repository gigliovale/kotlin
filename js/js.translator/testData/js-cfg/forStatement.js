function box() {
    before();
    for (var i = start(); i < end; i++) {
        handle(i);
    }
    after();
}