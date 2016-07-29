function box(limit) {
    before();
    for (var i = 0; i < limit; ) {
        body();
        if (test()) {
            continue;
        }
        ++i;
    }
    after();
}