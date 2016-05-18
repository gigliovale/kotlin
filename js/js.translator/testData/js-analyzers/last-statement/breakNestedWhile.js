function box() {
    outer: while (condition1()) {
        a();
        while (condition2()) {
            if (condition3()) {
                b(); /*final*/
                break outer;
            }
            if (condition3()) {
                c();
                break;
            }
        }

        if (condition4()) {
            d(); /*final*/
            break;
        }

        e();
    }
}