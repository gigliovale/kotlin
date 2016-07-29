function box() {
    before();
    label: if (test1()) {
        foo();
        if (test2()) {
            break label;
        }
        bar();
    } else {
        baz();
    }
    after();
}