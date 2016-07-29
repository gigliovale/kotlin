function box() {
    try {
        try {
            if (test1()) {
                causesException();
            }
            else if (test2()) {
                return;
            }
            body();
        } finally {
            cleanup();
        }
        afterBody();
    }
    finally {
        cleanup2();
    }
    after();
}