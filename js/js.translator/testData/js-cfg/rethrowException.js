function box() {
    try {
        causesException();
    }
    catch (e) {
        throw new Error();
    }
}