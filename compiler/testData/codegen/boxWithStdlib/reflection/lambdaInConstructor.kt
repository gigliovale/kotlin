class C {
    val l: Any = {}
}

fun box(): String {
    val enclosingConstructor = C().l.javaClass.getEnclosingConstructor()
    if (enclosingConstructor?.getDeclaringClass()?.getName() != "C") return "ctor: $enclosingConstructor"

    val enclosingClass = C().l.javaClass.getEnclosingClass()
    if (enclosingClass?.getName() != "C") return "enclosing class: $enclosingClass"

    val declaringClass = C().l.javaClass.getDeclaringClass()
    if (declaringClass != null) return "anonymous function has a declaring class: $declaringClass"

    return "OK"
}