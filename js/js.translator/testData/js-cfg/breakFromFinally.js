function box() {
    before();
    label: {
        try {
            body();
        }
        finally {
            cleanup2();
            break label;
        }
    }
    after();
}