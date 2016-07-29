function box() {
    before();
    for (var i = start(); i < end(); ++i) {
        body1();
        if (i == 5) {
            continue;
        }
        body2();
    }
    after();
}