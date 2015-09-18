// FILE: j/JavaPublic.java
package j;

public class JavaPublic {
    public static void javaM() {}
    public static int javaP = 4
}

// FILE: j/JavaPackage.java
package j;

class JavaPackage {
    static void javaMPackage() {}
    static int javaPPackage = 4
}

// FILE: j/JavaProtected.java
package j;

public class JavaProtected {
    protected static void javaMProtected() {}
    protected static int javaPProtected = 4
}

// FILE: j/JavaPrivate.java
package j;

public class JavaPrivate {
    private static void javaMPrivate() {}
    private static int javaPPrivate = 4
}


// FILE: k1.kt
package k

import j.JavaPublic
import j.JavaPublic.javaM
import j.JavaPublic.javaP

import j.<!INVISIBLE_REFERENCE!>JavaPackage<!>
import j.<!INVISIBLE_REFERENCE!>JavaPackage<!>.<!INVISIBLE_REFERENCE!>javaMPackage<!>
import j.<!INVISIBLE_REFERENCE!>JavaPackage<!>.<!INVISIBLE_REFERENCE!>javaPPackage<!>

import j.JavaProtected
import j.JavaProtected.javaMProtected
import j.JavaProtected.javaPProtected

import j.JavaPrivate
import j.JavaPrivate.<!INVISIBLE_REFERENCE!>javaMPrivate<!>
import j.JavaPrivate.<!INVISIBLE_REFERENCE!>javaPPrivate<!>

// FILE: k2.kt
package j

import j.JavaPublic
import j.JavaPublic.javaM
import j.JavaPublic.javaP

import j.JavaPackage
import j.JavaPackage.javaMPackage
import j.JavaPackage.javaPPackage

import j.JavaProtected
import j.JavaProtected.javaMProtected
import j.JavaProtected.javaPProtected

import j.JavaPrivate
import j.JavaPrivate.<!INVISIBLE_REFERENCE!>javaMPrivate<!>
import j.JavaPrivate.<!INVISIBLE_REFERENCE!>javaPPrivate<!>