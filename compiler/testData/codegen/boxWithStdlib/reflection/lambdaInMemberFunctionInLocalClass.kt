fun box(): String {
    class C {
        fun foo(): Any {
            return {}
        }
    }


    val enclosingMethod = C().foo().javaClass.getEnclosingMethod()
    if (enclosingMethod?.getName() != "foo") return "method: $enclosingMethod"

    val enclosingClass = C().foo().javaClass.getEnclosingClass()
    if (enclosingClass?.getName() != "_DefaultPackage\$box\$C") return "enclosing class: $enclosingClass"

    val declaringClass = C().foo().javaClass.getDeclaringClass()
    if (declaringClass != null) return "anonymous function has a declaring class: $declaringClass"

    return "OK"
}