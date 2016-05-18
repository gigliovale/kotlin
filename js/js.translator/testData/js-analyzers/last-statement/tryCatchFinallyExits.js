function box() {
    try {
        a();
    }
    catch (e) {
        b();
    }
    finally {
        c();
        return 4;
    }
}