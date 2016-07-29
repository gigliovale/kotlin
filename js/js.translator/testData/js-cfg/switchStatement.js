function box() {
    before();
    switch (selector()) {
        case 1:
            firstCase();
            break;
        case 2:
            secondCase();
        case 3:
            thirdCase();
            break;
        default:
            fourthCase();
    }
    after();
}