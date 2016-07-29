function box() {
    before();
    switch (selector()) {
        case 1:
            firstCase();
            break;
        case 2:
            secondCase();
            break;
    }
    after();
}