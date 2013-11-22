class C {
    class D {
        fun foo(): Any {
            return {}
        }
    }
}


fun box(): String {
    val enclosingMethod = C.D().foo().javaClass.getEnclosingMethod()
    if (enclosingMethod?.getName() != "foo") return "method: $enclosingMethod"

    val enclosingClass = C.D().foo().javaClass.getEnclosingClass()
    if (enclosingClass?.getSimpleName() != "D") return "enclosing class: $enclosingClass"

    val declaringClass = C.D().foo().javaClass.getDeclaringClass()
    if (declaringClass != null) return "anonymous function has a declaring class: $declaringClass"

    return "OK"
}