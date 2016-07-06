interface IBase {
    fun foo(): Any
}

open class CBase : IBase {
    override fun foo(): Any = 42
}

interface IDerived : IBase {
    override fun foo(): String
}

<!ABSTRACT_MEMBER_NOT_IMPLEMENTED!>class Test<!> : CBase(), IDerived