function box() {
    for (var property in { a : 1, b : 2 }) {
        print(property);
    }
    var key;
    for (key in { c : 3, d : 4 }) {
        print(property);
    }
    after();
}