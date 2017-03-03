external fun foo(<!WRONG_EXTERNAL_DECLARATION!>f: Int.() -> Int<!>)

external fun bar(<!WRONG_EXTERNAL_DECLARATION!>vararg f: Int.() -> Int<!>)

<!WRONG_EXTERNAL_DECLARATION!>external fun baz(): Int.() -> Int<!>

<!WRONG_EXTERNAL_DECLARATION!>external val prop: Int.() -> Int<!>

<!WRONG_EXTERNAL_DECLARATION!>external var prop2: Int.() -> Int<!>

external val propGet
    <!WRONG_EXTERNAL_DECLARATION!>get(): Int.() -> Int<!> = definedExternally

external var propSet
    <!WRONG_EXTERNAL_DECLARATION!>get(): Int.() -> Int<!> = definedExternally
    set(<!WRONG_EXTERNAL_DECLARATION!>v: Int.() -> Int<!>) = definedExternally

external class A(<!WRONG_EXTERNAL_DECLARATION!>f: Int.() -> Int<!>)

external data class <!WRONG_EXTERNAL_DECLARATION!>B(
        <!WRONG_EXTERNAL_DECLARATION, WRONG_EXTERNAL_DECLARATION!>val a: Int.() -> Int<!>,
        <!WRONG_EXTERNAL_DECLARATION, WRONG_EXTERNAL_DECLARATION!>var b: Int.() -> Int<!>
)<!> {
    <!WRONG_EXTERNAL_DECLARATION!>val c: Int.() -> Int<!>
}