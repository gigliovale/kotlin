function box() {
    return {
        "a" : 23,
        b : foo(),
        c : test() ? first() : second(),
        "d" : "!"
    }
}