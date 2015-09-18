// FILE: j/JavaPublic.java
package j;

public class JavaPublic {
    public static void javaM() {}
    public static int javaP = 4
}

// FILE: j/JavaPackage.java
package j;

class JavaPackage {
    static void javaM() {}
    static int javaP = 4
}

// FILE: j/JavaProtected.java
package j;

public class JavaProtected {
    protected static void javaM() {}
    protected static int javaP = 4
}

// FILE: j/JavaPrivate.java
package j;

public class JavaPrivate {
    private static void javaM() {}
    private static int javaP = 4
}


// FILE: k1.kt
package k

import j.JavaPublic
import j.JavaPublic.javaM
import j.JavaPublic.javaP

import j.<!INVISIBLE_REFERENCE!>JavaPackage<!>
import j.<!INVISIBLE_REFERENCE!>JavaPackage<!>.<!INVISIBLE_REFERENCE!>javaM<!>
import j.<!INVISIBLE_REFERENCE!>JavaPackage<!>.<!INVISIBLE_REFERENCE!>javaP<!>

import j.JavaProtected
import j.JavaProtected.javaM
import j.JavaProtected.javaP

import j.JavaPrivate
import j.JavaPrivate.<!INVISIBLE_REFERENCE!>javaM<!>
import j.JavaPrivate.<!INVISIBLE_REFERENCE!>javaP<!>

// FILE: k2.kt
package j

import j.JavaPublic
import j.JavaPublic.javaM
import j.JavaPublic.javaP

import j.JavaPackage
import j.JavaPackage.javaM
import j.JavaPackage.javaP

import j.JavaProtected
import j.JavaProtected.javaM
import j.JavaProtected.javaP

import j.JavaPrivate
import j.JavaPrivate.<!INVISIBLE_REFERENCE!>javaM<!>
import j.JavaPrivate.<!INVISIBLE_REFERENCE!>javaP<!>