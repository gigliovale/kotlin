var global = 0;

function test(n) {
    var $tmp;
    if (n >= 0) {
        $tmp = n;
        global++;
    }
    else {
        $tmp = -n;
    }

    return $tmp;
}

function box() {
    var result = test(20);
    if (result != 20) return "fail1: " + result;

    result = test(-20);
    if (result != 20) return "fail2: " + result;
    
    if (global != 1) return "fail3: " + global;

    return "OK"
}