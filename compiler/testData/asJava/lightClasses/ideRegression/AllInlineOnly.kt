// a.A
@file:kotlin.jvm.JvmMultifileClass
@file:kotlin.jvm.JvmName("A")
package a

@kotlin.internal.InlineOnly
inline fun <T> listOf(): List<T> = emptyList()

@kotlin.internal.InlineOnly
inline fun f() {}