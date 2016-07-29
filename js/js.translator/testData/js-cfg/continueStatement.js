function box() {
    before();
    while (test1()) {
       body1();
       if (test2()) {
           continue;
           redundant();
       }
       body2();
    }
    after();
}