package test.properties.delegation

import org.junit.Test as test
import kotlin.test.*
import kotlin.properties.*

class NotNullVarTest() {
    test fun doTest() {
        NotNullVarTestGeneric("a", "b").doTest()
    }
}

private class NotNullVarTestGeneric<T>(val a1: String, val b1: T) {
    var a: String by Delegates.notNull()
    var b by Delegates.notNull<T>()

    public fun doTest() {
        a = a1
        b = b1
        assertTrue(a == "a", "fail: a should be a, but was $a")
        assertTrue(b == "b", "fail: b should be b, but was $b")
    }
}

class ObservablePropertyInChangeSupportTest: ChangeSupport() {

    var b by property(init = 2)
    var c by property(3)

    test fun doTest() {
        var result = false
        addChangeListener("b", object: ChangeListener {
            public override fun onPropertyChange(event: ChangeEvent) {
                result = true
            }
        })
        addChangeListener("c", object: ChangeListener {
            public override fun onPropertyChange(event: ChangeEvent) {
                result = false
            }
        })
        b = 4
        assertTrue(b == 4, "fail: b != 4")
        assertTrue(result, "fail: result should be true")
    }
}

class ObservablePropertyTest {
    var result = false

    var b: Int by Delegates.observable(1, { property, old, new ->
        assertEquals("b", property.name)
        result = true
        assertEquals(new, b, "New value has already been set")
    })

    test fun doTest() {
        b = 4
        assertTrue(b == 4, "fail: b != 4")
        assertTrue(result, "fail: result should be true")
    }
}

class A(val p: Boolean)

class VetoablePropertyTest {
    var result = false
    var b: A by Delegates.vetoable(A(true), { property, old, new ->
        assertEquals("b", property.name)
        assertEquals(old, b, "New value hasn't been set yet")
        result = new.p == true;
        result
    })

    test fun doTest() {
        val firstValue = A(true)
        b = firstValue
        assertTrue(b == firstValue, "fail1: b should be firstValue = A(true)")
        assertTrue(result, "fail2: result should be true")
        b = A(false)
        assertTrue(b == firstValue, "fail3: b should be firstValue = A(true)")
        assertFalse(result, "fail4: result should be false")
    }
}


private class ChangeState {
    public var state: Any? = null
}

class CorrelatedObservablePropertyTest {

    var beforeChangeCalled = 0
    var afterChangeCalled = 0
    var releaseCorrelationCalled = 0

    var prop by object : CorrelatedObservableProperty<Int, ChangeState>(11) {
        override fun createCorrelation(property: PropertyMetadata, oldValue: Int, newValue: Int) = ChangeState()

        override fun beforeChange(correlation: ChangeState, property: PropertyMetadata, oldValue: Int, newValue: Int): Boolean {
            beforeChangeCalled += 1
            correlation.state = oldValue + newValue
            if (newValue - oldValue == 1)
                throw IllegalArgumentException("Successive values are not allowed")
            return newValue > 0
        }

        override fun afterChange(correlation: ChangeState, property: PropertyMetadata, oldValue: Int, newValue: Int) {
            afterChangeCalled += 1
            assertEquals(oldValue + newValue, correlation.state)
        }

        override fun releaseCorrelation(correlation: ChangeState) {
            releaseCorrelationCalled += 1
        }
    }

    test fun doTest() {
        prop = 13
        prop = -1
        fails { prop = 14 }
        assertEquals(3, beforeChangeCalled)
        assertEquals(1, afterChangeCalled)
        assertEquals(3, releaseCorrelationCalled)
    }
}
