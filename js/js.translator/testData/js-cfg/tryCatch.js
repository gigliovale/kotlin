function box() {
    before();
    try {
        causesException();
    }
    catch (e) {
        reportException(e)
    }
    after();
}