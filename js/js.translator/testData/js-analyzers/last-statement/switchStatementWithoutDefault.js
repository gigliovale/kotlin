function box() {
    switch (condition()) {
        case 0:
            a();
        case 1:
            b();
            return;
        case 2:
            c();
            return;
    }
    d(); /*final*/
} 