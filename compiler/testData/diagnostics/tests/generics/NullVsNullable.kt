// See also comments to KT-10390

fun foo(arg: Any?) {
    arg?.hashCode()
}

fun bar(arg: Any?): Int? = arg?.hashCode()

fun <T> id(arg: T) = arg

open class Base

class Derived : Base()

fun <T> adapt(x: T, a: (T) -> T) = a(x)

class Consume<T>(val x: T, val f: (T) -> Unit)

class Printer<T>(val x: T, val f: (T) -> String)

class Mapper<T, R>(val x: T, val f: (T) -> R)

class Transform<T>(val x: T, val f: (T) -> T)

class Produce<T>(val x: T, val f: () -> T)

val c: Consume<Int?> = <!TYPE_INFERENCE_EXPECTED_TYPE_MISMATCH!>Consume(null) { y -> foo(<!DEBUG_INFO_CONSTANT!>y<!>) }<!>

val c1: Consume<Int?> = <!TYPE_INFERENCE_EXPECTED_TYPE_MISMATCH!>id(Consume(null) { foo(<!DEBUG_INFO_CONSTANT!>it<!>) })<!>

val cc = Consume(null) { y -> foo(<!DEBUG_INFO_CONSTANT!>y<!>) }

val cc1 = Consume(null) { y: Int? -> foo(y) }

val ccc: Consume<Base?> = <!TYPE_INFERENCE_EXPECTED_TYPE_MISMATCH!>Consume(Derived()) { y -> foo(y) }<!>

val r: Printer<Int?> = <!TYPE_INFERENCE_EXPECTED_TYPE_MISMATCH!>Printer(null) { y -> y.toString() }<!>

val r1: Printer<Int?> = <!TYPE_INFERENCE_EXPECTED_TYPE_MISMATCH!>id(Printer(null) { "$<!DEBUG_INFO_CONSTANT!>it<!>" } )<!>

val rr = Printer(null) { y -> y.toString() }

val rr1 = Printer(null) { y: Int? -> y.toString() }

val rrr: Printer<Base?> = <!TYPE_INFERENCE_EXPECTED_TYPE_MISMATCH!>Printer(Derived()) { y -> y.toString() }<!>

val m: Mapper<Int?, String> = <!TYPE_INFERENCE_EXPECTED_TYPE_MISMATCH!>Mapper(null) { y -> y.toString() }<!>

val m1: Mapper<Int?, String> = <!TYPE_INFERENCE_EXPECTED_TYPE_MISMATCH!>id(Mapper(null) { "$<!DEBUG_INFO_CONSTANT!>it<!>" } )<!>

val mm = Mapper(null) { y -> y.toString() }

val mm1 = Mapper(null) { y: Int? -> y.toString() }

val mmm: Mapper<Base?, String> = <!TYPE_INFERENCE_EXPECTED_TYPE_MISMATCH!>Mapper(Derived()) { y -> y.toString() }<!>

val t: Transform<Int?> = <!TYPE_INFERENCE_CONFLICTING_SUBSTITUTIONS!>Transform<!>(null) { y -> bar(<!DEBUG_INFO_CONSTANT!>y<!>) }

val tt = <!TYPE_INFERENCE_CONFLICTING_SUBSTITUTIONS!>Transform<!>(null) { y -> bar(<!DEBUG_INFO_CONSTANT!>y<!>) }

val tt1 = Transform(null) { y: Int? -> bar(y) }

val ttt: Transform<Base?> = <!TYPE_INFERENCE_CONFLICTING_SUBSTITUTIONS!>Transform<!>(Derived()) { y -> bar(y) }

val i: Transform<Int?> = <!TYPE_INFERENCE_EXPECTED_TYPE_MISMATCH!>Transform(null) { y -> id(<!DEBUG_INFO_CONSTANT!>y<!>) }<!>

val ii = Transform(null) { y -> id(<!DEBUG_INFO_CONSTANT!>y<!>) }

val ii1 = Transform(null) { y: Int? -> id(y) }

val iii: Transform<Base?> = <!TYPE_INFERENCE_EXPECTED_TYPE_MISMATCH!>Transform(Derived()) { y -> id(y) }<!>

val j: Transform<Int?> = <!TYPE_INFERENCE_EXPECTED_TYPE_MISMATCH!>Transform(null) { <!DEBUG_INFO_CONSTANT!>it<!> }<!>

val jj = Transform(null) { <!DEBUG_INFO_CONSTANT!>it<!> }

val jjj: Transform<Base?> = <!TYPE_INFERENCE_EXPECTED_TYPE_MISMATCH!>Transform(Derived()) { it }<!>

val p: Produce<Int?> = Produce(null) { 42 }

val pp = Produce(null) { 42 }

val ppp: Produce<Base?> = Produce(Derived()) { Base() }

val a: Int? = <!TYPE_INFERENCE_CONFLICTING_SUBSTITUTIONS!>adapt<!>(null) { y -> 42 }

val aa = <!TYPE_INFERENCE_CONFLICTING_SUBSTITUTIONS!>adapt<!>(null) { y -> 42 }

val aa1 = adapt(null) { y: Int? -> 42 }

val aaa: Base? = adapt(Derived()) { it }

val aaa1: Base? = adapt(Base()) { it }

