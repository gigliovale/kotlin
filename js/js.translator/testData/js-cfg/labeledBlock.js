function box(x) {
    before();
    label: {
        if (x > 10) {
            break label;
        }
        if (x < 0) {
            break label;
        }
        body();
    }
    after();
}