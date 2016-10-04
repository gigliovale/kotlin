import kotlin.reflect.KClass

interface Fragment

interface Fragment1 : Fragment
interface Fragment2 : Fragment
interface Fragment3 : Fragment


fun test() = arrayOf(Fragment1::class.java, Fragment2::class.java, Fragment3::class.java)

val <T : Any> KClass<T>.java: Class<T> get() = null!!
