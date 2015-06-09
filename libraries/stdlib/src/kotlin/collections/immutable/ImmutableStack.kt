package kotlin

import java.util.*
import kotlin.support.AbstractIterator

/**
 * Created by Ilya.Gorbunov on 08.06.2015.
 */


public abstract class ImmutableStack<out E> private constructor(): ImmutableCollection<E> {

    public object EMPTY: ImmutableStack<Nothing>()  {
        override fun size(): Int = 0
        override fun isEmpty(): Boolean = true
        override fun contains(o: Any?): Boolean = false
        override fun containsAll(c: Collection<Any?>): Boolean = c.isEmpty()
        override fun iterator(): Iterator<Nothing> = EmptyIterator
        override fun remove(element: Any?): ImmutableStack<Nothing> = this
        override fun removeAll(c: Iterable<Any?>): ImmutableStack<Nothing> = this
        override fun retainAll(c: Iterable<Any?>): ImmutableStack<Nothing> = this
        override fun filter(predicate: (Nothing) -> Boolean): ImmutableStack<Nothing> = this
    }

    private class Cons<out E>(public val head: E, public val tail: ImmutableStack<E>): ImmutableStack<E>() {
        private val size = 1 + tail.size()

        override fun size() = size
        override fun isEmpty(): Boolean = false
        override fun contains(o: Any?): Boolean = head == o || tail.contains(head)

        override fun remove(element: Any?): ImmutableStack<E> {
            if (this.head == element)
                return tail
            else {
                val newNext = tail.remove(element)
                return if (newNext !== tail) Cons(this.head, newNext) else this
            }
        }

        override fun filter(predicate: (E) -> Boolean): ImmutableStack<E> {
            if (!predicate(head))
                return tail.filter(predicate)
            else {
                val newNext = tail.filter(predicate)
                return if (newNext !== tail) Cons(this.head, newNext) else this
            }
        }

    }



    suppress("TYPE_VARIANCE_CONFLICT")
    fun push(element: E): ImmutableStack<E> = Cons(element, this)

    fun pop(): Pair<E, ImmutableStack<E>> =
        if (this is Cons) head to tail else throw NoSuchElementException()

    fun peek(): E? = if (this is Cons) head else null

    suppress("TYPE_VARIANCE_CONFLICT")
    override fun add(element: E): ImmutableStack<E> = Cons(element, this)

    suppress("TYPE_VARIANCE_CONFLICT")
    override fun addAll(elements: Iterable<E>): ImmutableStack<E> = elements.fold(this, { c, e -> c.add(e) })

    override fun containsAll(c: Collection<Any?>): Boolean {
        val set = c.toHashSet()
        this.forEach { set.remove(it) }
        return set.isEmpty()
    }

    protected abstract fun filter(predicate: (E) -> Boolean): ImmutableStack<E>

    abstract override fun remove(element: Any?): ImmutableStack<E>

    override fun removeAll(c: Iterable<Any?>): ImmutableStack<E> = c.toHashSet().let { set -> if (set.isEmpty()) this else filter { !set.contains(it) } }

    override fun retainAll(c: Iterable<Any?>): ImmutableStack<E> = c.toHashSet().let { set -> if (set.isEmpty()) EMPTY else filter { set.contains(it) } }

    override fun hashCode(): Int {
        var hash = 1
        var next = this
        while (next is Cons) {
            hash = hash * 31 + next.head.hashCode()
            next = next.tail
        }
        return hash
    }

    override fun toString(): String {
        return super.toString()
    }

    override fun iterator(): Iterator<E> = object: AbstractIterator<E>() {
        var next = this@ImmutableStack
        override fun computeNext() {
            val current = next
            when (current) {
                is Cons -> {
                    setNext(current.head)
                    next = current.tail
                }
                is EMPTY -> done()
            }
        }
    }

    override fun clear(): ImmutableStack<E> = EMPTY

    suppress("TYPE_VARIANCE_CONFLICT")
    override fun builder(): ImmutableCollection.Builder<E> = Builder(this)

    private class Builder<E>(override var value: ImmutableStack<E>): AbstractCollection<E>(), ImmutableCollection.Builder<E>, Mutator<ImmutableStack<E>> {
        override fun isEmpty(): Boolean = value.isEmpty()
        override fun size(): Int = value.size()
        override fun hashCode(): Int = value.hashCode()
        override fun add(e: E): Boolean {
            value = value.add(e)
            return true
        }

        override fun remove(o: Any?): Boolean = mutate { it.remove(o) }
        override fun removeAll(c: Collection<Any?>): Boolean = mutate { it.removeAll(c) }
        override fun retainAll(c: Collection<Any?>): Boolean = mutate { it.retainAll(c) }
        override fun addAll(c: Collection<E>): Boolean = mutate { it.addAll(c) }

        override fun clear() {
            value = EMPTY
        }

        override fun contains(o: Any?): Boolean = value.contains(o)
        override fun containsAll(c: Collection<Any?>): Boolean = value.containsAll(c)
        override fun equals(other: Any?): Boolean = value.equals(other)

        override fun iterator(): MutableIterator<E> = object: MutableIterator<E>, Iterator<E> by value.iterator() {
            override fun remove() {
                // TODO: ?
                throw UnsupportedOperationException()
            }
        }

        override fun build(): ImmutableCollection<E> = value
    }
}


private interface Mutator<T> {
    var value: T
}

private inline fun <T> Mutator<T>.mutate(operation: (T) -> T): Boolean {
    val newValue = operation(value)
    if (newValue !== value) {
        value = newValue
        return true
    }
    return false
}