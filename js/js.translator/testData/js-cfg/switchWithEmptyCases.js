function box() {
    before();
    switch (selector()) {
        case 0:
        case 1:
        case 2:
            first();
            break;
        case 3:
        case 4:
            second();
        case 5:
        default:
            third();
            break;
    }
    after();
}