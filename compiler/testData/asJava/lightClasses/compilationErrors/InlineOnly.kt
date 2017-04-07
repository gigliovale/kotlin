// a.A
@file:kotlin.jvm.JvmMultifileClass
@file:kotlin.jvm.JvmName("A")
package a

@kotlin.internal.InlineOnly
public inline fun <T> listOf(): List<T> = emptyList()

fun f() {}