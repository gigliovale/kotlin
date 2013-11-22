fun box(): String {
    fun foo(): Any {
        return {}
    }

    val enclosingMethod = foo().javaClass.getEnclosingMethod()
    if (enclosingMethod?.getName() != "invoke") return "method: $enclosingMethod"

    val enclosingClass = foo().javaClass.getEnclosingClass()
    if (enclosingClass!!.getName() != "_DefaultPackage\$box$1") return "enclosing class: $enclosingClass"

    val declaringClass = foo().javaClass.getDeclaringClass()
    if (declaringClass != null) return "anonymous function has a declaring class: $declaringClass"

    return "OK"
}