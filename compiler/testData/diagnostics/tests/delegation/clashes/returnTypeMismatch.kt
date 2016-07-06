interface IStr {
    fun foo(): String
}

class CStr : IStr {
    override fun foo(): String = ""
}

interface IInt {
    fun foo(): Int
}

class CInt : IInt {
    override fun foo(): Int = 42
}

interface IAny {
    fun foo(): Any
}

class CAny : IAny {
    override fun foo(): Any = null!!
}

interface IGeneric<T> {
    fun foo(): T
}

class CGeneric<T> : IGeneric<T> {
    override fun foo(): T {
        throw UnsupportedOperationException()
    }
}

abstract <!RETURN_TYPE_MISMATCH_ON_INHERITANCE!>class Test1<!> : IStr by CStr(), IInt

abstract <!RETURN_TYPE_MISMATCH_ON_INHERITANCE!>class Test2<!> : IStr, IInt by CInt()

abstract <!MANY_IMPL_MEMBER_NOT_IMPLEMENTED, RETURN_TYPE_MISMATCH_ON_INHERITANCE!>class Test3<!> : IStr by CStr(), IInt by CInt()

abstract class Test4 : IStr by CStr(), IGeneric<String>

abstract class Test5 : IStr by CStr(), IGeneric<Any>

abstract <!RETURN_TYPE_MISMATCH_ON_INHERITANCE!>class Test6<!> : IStr by CStr(), IGeneric<Int>

abstract class Test7 : IGeneric<String> by CGeneric<String>(), IStr

abstract <!RETURN_TYPE_MISMATCH_ON_INHERITANCE!>class Test8<!> : IGeneric<String> by CGeneric<String>(), IInt

abstract class <!CONFLICTING_INHERITED_JVM_DECLARATIONS, CONFLICTING_INHERITED_JVM_DECLARATIONS, CONFLICTING_INHERITED_JVM_DECLARATIONS!>Test9<!> : <!INCONSISTENT_TYPE_PARAMETER_VALUES!>IGeneric<String> by CGeneric<String>(), <!SUPERTYPE_APPEARS_TWICE!>IGeneric<Int><!><!>

abstract <!MANY_IMPL_MEMBER_NOT_IMPLEMENTED, MANY_IMPL_MEMBER_NOT_IMPLEMENTED, RETURN_TYPE_MISMATCH_ON_INHERITANCE!>class Test10<!> : IInt by CInt(), IStr by CStr(), IAny by CAny()

abstract <!MANY_IMPL_MEMBER_NOT_IMPLEMENTED, RETURN_TYPE_MISMATCH_ON_INHERITANCE!>class Test11<!> : IInt, IStr by CStr(), IAny by CAny()

abstract <!RETURN_TYPE_MISMATCH_ON_INHERITANCE!>class Test12<!> : IInt, IStr, IAny by CAny()

