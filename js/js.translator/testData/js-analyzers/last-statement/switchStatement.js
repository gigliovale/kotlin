function box() {
    switch (condition()) {
        case 0:
            a();
        case 1:
            b(); /*final*/
            break;
        default:
            c(); /*final*/
    }
} 