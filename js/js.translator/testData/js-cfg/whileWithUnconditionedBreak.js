function box() {
    before();
    while (test()) {
        body1();
        break;
        body2();
    }
    after();
}