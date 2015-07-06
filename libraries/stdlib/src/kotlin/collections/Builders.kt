package kotlin

import java.util.*

public interface BuildableCollection<out E>: Collection<E> {
    suppress("TYPE_VARIANCE_CONFLICT")
    public fun builder(): CollectionBuilder<E>
}

public interface CollectionBuilder<E>: MutableCollection<E> {
    public fun build(): Collection<E>
    public fun expectedSize(value: Int) {}
}

public interface BuildableList<out E>: List<E>, BuildableCollection<E> {
    suppress("TYPE_VARIANCE_CONFLICT")
    public override fun builder(): ListBuilder<E>
}

public interface ListBuilder<E>: MutableList<E>, CollectionBuilder<E> {
    public override fun build(): List<E>
}

public interface BuildableSet<out E>: Set<E>, BuildableCollection<E> {
    suppress("TYPE_VARIANCE_CONFLICT")
    public override fun builder(): SetBuilder<E>
}


public interface SetBuilder<E>: MutableSet<E>, CollectionBuilder<E> {
    public override fun build(): Set<E>
}


// some operations

// also works for sets, lists
public fun <T, C : BuildableCollection<T>> C.plus(element: T): C {
    val builder = builder()
    builder.expectedSize(this.size() + 1)
    builder.add(element)
    return builder.build() as C
}

public fun <T, C : BuildableCollection<T>> C.plus(collection: Iterable<T>): C {
    val builder = builder()
    if (collection is Collection)
        builder.expectedSize(this.size() + collection.size())
    builder.addAll(collection)
    return builder.build() as C
}

public fun <T, C : BuildableCollection<T>> C.plus(array: Array<out T>): C {
    val builder = builder()
    builder.expectedSize(this.size() + array.size())
    builder.addAll(array)
    return builder.build() as C
}

public fun <T, C : BuildableCollection<T>> C.drop(n: Int): C {
    val builder = builder()
    builder.clear()
    val resultSize = size() - n
    if (resultSize > 0) {
        builder.expectedSize(resultSize)
        var count = 0
        for (item in this as Collection<T>) {
            if (count++ >= n) builder.add(item)
        }
    }
    return builder.build() as C
}


public inline fun <T, C : BuildableCollection<T>> C.filter(predicate: (T) -> Boolean): C {
    val builder = this.builder()
    builder.clear()
    this.filterTo(builder, predicate)
    return builder.build() as C
}