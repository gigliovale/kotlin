package kotlin

import java.util.*
import kotlin.support.AbstractIterator

/**
 * Created by Ilya.Gorbunov on 08.06.2015.
 */


public abstract class ImmutableStack<out E> private(): ImmutableCollection<E> {

    public object EMPTY: ImmutableStack<Nothing>()  {
        override fun size(): Int = 0
        override fun isEmpty(): Boolean = true
        override fun contains(o: Any?): Boolean = false
        override fun containsAll(c: Collection<Any?>): Boolean = c.isEmpty()
        override fun iterator(): Iterator<Nothing> = EmptyIterator
        override fun remove(element: Any?): ImmutableStack<Nothing> = this
        override fun removeAll(c: Iterable<Any?>): ImmutableStack<Nothing> = this
        override fun retainAll(c: Iterable<Any?>): ImmutableStack<Nothing> = this
    }

    private class Cons<out E>(public val element: E, public val next: ImmutableStack<E>): ImmutableStack<E>() {
        private val size = 1 + next.size()

        override fun size() = size
        override fun isEmpty(): Boolean = false
        override fun contains(o: Any?): Boolean = element == o || next.contains(element)

        override fun remove(element: Any?): ImmutableStack<E> {
            if (this.element == element)
                return next
            else {
                val newNext = next.remove(element)
                return if (newNext !== next) Cons(this.element, newNext) else this
            }
        }

    }

    suppress("TYPE_VARIANCE_CONFLICT")
    override fun add(element: E): ImmutableStack<E> = Cons(element, this)

    suppress("TYPE_VARIANCE_CONFLICT")
    override fun addAll(elements: Iterable<E>): ImmutableStack<E> = elements.fold(this, { c, e -> c.add(e) })

    override fun containsAll(c: Collection<Any?>): Boolean {
        val set = c.toMutableSet()
        this.forEach { set.remove(it) }
        return set.isEmpty()
    }

    abstract override fun remove(element: Any?): ImmutableStack<E>

    override fun removeAll(c: Iterable<Any?>): ImmutableStack<E> {
        throw UnsupportedOperationException()
    }

    override fun retainAll(c: Iterable<Any?>): ImmutableStack<E> {
        throw UnsupportedOperationException()
    }

    override fun hashCode(): Int {
        return super.hashCode()
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
                    setNext(current.element)
                    next = current.next
                }
                is EMPTY -> done()
            }
        }
    }

    override fun clear(): ImmutableStack<E> = EMPTY

    suppress("TYPE_VARIANCE_CONFLICT")
    override fun builder(): ImmutableCollection.Builder<E> = Builder(this)

    private class Builder<E>(private var stack: ImmutableStack<E>): AbstractCollection<E>(), ImmutableCollection.Builder<E> {
        override fun size(): Int = stack.size()
        override fun hashCode(): Int = stack.hashCode()
        override fun add(e: E): Boolean {
            stack = stack.add(e)
            return true
        }

        override fun remove(o: Any?): Boolean {
            val newStack = stack.remove(o)
            if (newStack !== stack) {
                stack = newStack
                return true
            }
            return false
        }

        override fun clear() {
            stack = EMPTY
        }

        override fun contains(o: Any?): Boolean = stack.contains(o)
        override fun containsAll(c: Collection<Any?>): Boolean = stack.containsAll(c)
        override fun equals(other: Any?): Boolean = stack.equals(other)

        override fun iterator(): MutableIterator<E> = object: MutableIterator<E>, Iterator<E> by stack.iterator() {
            override fun remove() {
                // TODO: ?
                throw UnsupportedOperationException()
            }
        }

        override fun build(): ImmutableCollection<E> = stack
    }
}