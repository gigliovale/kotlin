function box() {
    switch (condition()) {
        case 0:
            a();
        case 1:
            b();
            return;
        default:
            c();
            return;
    }
    d();
} 