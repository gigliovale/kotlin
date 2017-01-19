function EC1() {
}
Object.defineProperty(EC1.prototype, "x", {
    "get": function() { return 111; },
    "set": function() {}
});

function EC2() {
}
EC2.prototype = Object.create(EC1.prototype);

function EC3() {
}