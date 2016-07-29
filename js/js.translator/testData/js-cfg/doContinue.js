function box() {
    before();
    do {
        body1();
        if (test1()) {
            continue;
        }
        body2();
    }
    while (test2());
    after();
}