function box() {
    while (foo()) {
        if (bar()) {
            f(); /*final*/
            break;
        }
        bar();
    }
}