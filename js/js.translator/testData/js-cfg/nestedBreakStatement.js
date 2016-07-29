function box() {
    before();
    while (test1()) {
        body1();
        while (test2()) {
            if (test3()) {
                break;
                redundant();
            }
            body2();
        }
        body3();
    }
    after();
}