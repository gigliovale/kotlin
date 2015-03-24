// FILE: a.kt

package a

public class X {
    public val x : String? = null
    public fun fn(): Int {
        if (x != null)
            return <!DEBUG_INFO_SMARTCAST!>x<!>.length() // bogus error here
        else
            return 0
    }
}

// FILE: b.kt

package b

import a.X

public fun X.gav(): Int {
    if (x != null)
        return x<!UNSAFE_CALL!>.<!>length() // bogus error here
    else
        return 0
}