import kotlin.reflect.KProperty
import kotlin.properties.ReadWriteProperty
import kotlin.properties.ObservableProperty

inline fun <T> observable(initialValue: T, crossinline onChange: (property: KProperty<*>, oldValue: T, newValue: T) -> Unit):
        ReadWriteProperty<Any?, T> = object : ObservableProperty<T>(initialValue) {
    override fun afterChange(property: KProperty<*>, oldValue: T, newValue: T) = onChange(property, oldValue, newValue)
}

var selected: Int? by <!DELEGATE_SPECIAL_FUNCTION_NONE_APPLICABLE!>observable(null) { p, old, new -> println("$<!DEBUG_INFO_CONSTANT!>old<!>-->$<!DEBUG_INFO_CONSTANT!>new<!>") }<!>
