import kotlin.reflect.KProperty
import kotlin.properties.ReadWriteProperty
import kotlin.properties.ObservableProperty

inline fun <T> observable(initialValue: T, crossinline onChange: (property: KProperty<*>, oldValue: T, newValue: T) -> Unit):
        ReadWriteProperty<Any?, T> = object : ObservableProperty<T>(initialValue) {
    override fun afterChange(property: KProperty<*>, oldValue: T, newValue: T) = onChange(property, oldValue, newValue)
}

var selected: Int? by observable(null) { <!UNUSED_PARAMETER!>p<!>, old, new -> println("$old-->$new") }
