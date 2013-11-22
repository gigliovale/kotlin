fun box(): String {
    val l: Any = {}

    val enclosingMethod = l.javaClass.getEnclosingMethod()
    if (enclosingMethod?.getName() != "box") return "method: $enclosingMethod"

    val enclosingClass = l.javaClass.getEnclosingClass()
    if (!enclosingClass!!.getName().startsWith("_DefaultPackage-lambdaInFunction-")) return "enclosing class: $enclosingClass"

    val declaringClass = l.javaClass.getDeclaringClass()
    if (declaringClass != null) return "anonymous function has a declaring class: $declaringClass"

    return "OK"
}