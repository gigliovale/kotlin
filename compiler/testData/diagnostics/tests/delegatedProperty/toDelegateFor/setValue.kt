// !DIAGNOSTICS: -UNUSED_PARAMETER

class Delegate

operator fun Delegate.getValue(receiver: Any, p: Any): Int = 42
operator fun <T> Delegate.setValue(receiver: Any, p: Any, value: T) {}

operator fun String.toDelegateFor(receiver: Any, p: Any) = Delegate()

// No 'toDelegateFor': doesn't work
val test1 by <!DELEGATE_SPECIAL_FUNCTION_NONE_APPLICABLE!>Delegate()<!>

val test2 by <!DELEGATE_SPECIAL_FUNCTION_NONE_APPLICABLE, DELEGATE_SPECIAL_FUNCTION_MISSING!>"OK"<!>