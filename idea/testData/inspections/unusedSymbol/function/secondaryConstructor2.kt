open class CtorUsedParent {
    constructor(p: Int)
    constructor(p: String): this(0)
}

class CtorUsedChild : CtorUsedParent {
    constructor(p: Boolean) : super(0)
}

@test.anno.EntryPoint
fun use(): String {
    return CtorUsedChild(false).toString() + CtorUsedParent("a").toString()
}
