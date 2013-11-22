fun box(): String {
    val l = {
        {}
    }

    val enclosingMethod = l().javaClass.getEnclosingMethod()
    if (enclosingMethod?.getName() != "invoke") return "method: $enclosingMethod"

    val enclosingClass = l().javaClass.getEnclosingClass()
    if (enclosingClass!!.getName() != "_DefaultPackage\$box\$l$1") return "enclosing class: $enclosingClass"

    val declaringClass = l().javaClass.getDeclaringClass()
    if (declaringClass != null) return "anonymous function has a declaring class: $declaringClass"

    return "OK"
}