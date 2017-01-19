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

open class AC1 {
    open var x: Int
        get() = 55
        set(value) {}
}

abstract class AC2 : AC1() {
    override abstract var x: Int
}

class AC2Impl : AC2() {
    override var x: Int = 2
}

abstract class AC3 {
    abstract var x: Int
}

class AC3Impl : AC3() {
    override var x: Int = 2
}

interface I1 {
    open var x: Int
        get() = 66
        set(value) {}
}

class I1Impl : I1 {
    override var x: Int = 3
}

class I1Impl2 : I1

interface I2 : I1 {
    override var x: Int
}

class I2Impl : I2 {
    override var x: Int = 4
}


external open class EC1 {
    open var x: Int
        get() = noImpl
        set(value) = noImpl
}
class EC1Impl : EC1() {
    override var x: Int = 1
}
external abstract class EC2 : EC1 {
    override abstract var x: Int
}
class EC2Impl : EC2() {
    override var x: Int = 2
}
abstract class EC3 {
    abstract var x: Int
}
class EC3Impl : EC3() {
    override var x: Int = 3
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
        simple("x", 23)
        simple("y", 42)
    }

    check(AC1()) {
        getSet("x", 55)
    }
    check(AC2Impl()) {
        getSet("x", 2)
    }
    check(AC3Impl()) {
        simple("x", 2)
    }

    check(I1Impl()) {
        simple("x", 3)
    }
    check(I1Impl2()) {
        getSet("x", 66)
    }
    check(I2Impl()) {
        simple("x", 4)
    }

    check(EC1Impl()) {
        getSet("x", 1)
    }
    check(EC2Impl()) {
        getSet("x", 2)
    }
    check(EC3Impl()) {
        simple("x", 3)
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