interface IBase {
    fun foo(): Any
}

interface IDerived : IBase {
    override fun foo(): String
}

interface IGenericDerived<T : Any> : IBase {
    override fun foo(): T
}

<!RETURN_TYPE_MISMATCH_ON_INHERITANCE!>class Test1<!>(val b: IBase) : IBase by b, IDerived

<!RETURN_TYPE_MISMATCH_ON_INHERITANCE!>class Test2<!>(val b: IBase) : IBase by b, IGenericDerived<String>