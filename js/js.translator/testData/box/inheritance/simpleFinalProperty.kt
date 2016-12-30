interface I {
    val x: Int
}

interface J {
    val x: Int
        get() = 23
}

external interface K {
    val y: Int?
        get() = noImpl
}

abstract class C {
    abstract val x: Int

    open val y: Int
        get() = 42

    val z: Int = 99
}

class D : C(), I {
    override val x = 23

    override val y = 43
}

open class E : I {
    override open val x = 23
}

class F : I {
    override val x: Int
        get() = 23
}

class G : J, K {
    override val x = 23

    override val y = 42
}

fun box(): String {
    check(D()) {
        simple("x", 23)
        getSet("y", 43)
        simple("z", 99)
    }
    check(E()) {
        getSet("x", 23)
    }
    check(F()) {
        getSet("x", 23)
    }
    check(G()) {
        getSet("x", 23)
        simple("y", 42)
    }

    return "OK"
}

fun check(instance: Any, f: Checker.() -> Unit) {
    Checker(instance).f()
}

class Checker(val instance: Any) {
    fun simple(name: String, value: Any) = check(name, value, instance)

    fun getSet(name: String, value: Any) = check(name, value, instance.asDynamic().constructor.prototype)

    private fun check(name: String, value: Any, container: Any) {
        for (prop: String in js("Object").getOwnPropertyNames(container)) {
            if (prop == name) {
                val actualValue: Any = instance.asDynamic()[prop]
                assertEquals(value, actualValue, "${instance::class.simpleName}.$name")
                return
            }
        }
        fail("Property $name was not found in ${instance::class.simpleName}")
    }
}