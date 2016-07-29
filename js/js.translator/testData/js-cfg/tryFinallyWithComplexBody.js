function box() {
    before();
    try {
        if (test1()) {
            causesException();
        }
        else if (test2()) {
            throw new Error();
        }
        else {
            return
        }
    }
    finally {
        cleanup();
    }
    after();
}