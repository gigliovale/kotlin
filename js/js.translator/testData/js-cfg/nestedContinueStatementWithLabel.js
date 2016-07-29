function box() {
    before();
    outer: while (test1()) {
        body1();
        while (test2()) {
            if (test3()) {
                continue outer;
                redundant();
            }
            body2();
        }
        body3();
    }
    after();
}