open class C(val a: Any)

fun box(): String {
    val l = object : C({}) {
    }

    val enclosingMethod = l.a.javaClass.getEnclosingMethod()
    if (enclosingMethod?.getName() != "box") return "method: $enclosingMethod"

    val enclosingClass = l.a.javaClass.getEnclosingClass()
    if (!enclosingClass!!.getName().startsWith("_DefaultPackage-lambdaInObjectExpression-")) return "enclosing class: $enclosingClass"

    val declaringClass = l.a.javaClass.getDeclaringClass()
    if (declaringClass != null) return "anonymous function has a declaring class: $declaringClass"

    return "OK"
}