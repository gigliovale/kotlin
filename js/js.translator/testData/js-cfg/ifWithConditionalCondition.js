function box() {
    before();
    if (test() ? firstCondition() : secondCondition()) {
        first();
    }
    else {
        second();
    }
    after();
}