package kotlin



public interface ImmutableCollection<out E>: Collection<E> {
    suppress("TYPE_VARIANCE_CONFLICT")
    fun add(element: E): ImmutableCollection<E>

    suppress("TYPE_VARIANCE_CONFLICT")
    fun addAll(elements: Iterable<E>): ImmutableCollection<E>

    fun remove(element: Any?): ImmutableCollection<E>

    fun removeAll(c: Iterable<Any?>): ImmutableCollection<E>

    fun retainAll(c: Iterable<Any?>): ImmutableCollection<E>

    fun clear(): ImmutableCollection<E>

    interface Builder<E>: MutableCollection<E> {
        fun build(): ImmutableCollection<E>
    }

    suppress("TYPE_VARIANCE_CONFLICT")
    fun builder(): Builder<E>
}




public interface ImmutableSet<out E>: Set<E>, ImmutableCollection<E> {
    suppress("TYPE_VARIANCE_CONFLICT")
    override fun add(element: E): ImmutableSet<E>

    suppress("TYPE_VARIANCE_CONFLICT")
    override fun addAll(elements: Iterable<E>): ImmutableSet<E>

    override fun remove(element: Any?): ImmutableSet<E>

    override fun removeAll(c: Iterable<Any?>): ImmutableSet<E>

    override fun retainAll(c: Iterable<Any?>): ImmutableSet<E>

    override fun clear(): ImmutableSet<E>

    interface Builder<E>: MutableSet<E>, ImmutableCollection.Builder<E> {
        override fun build(): ImmutableSet<E>
    }

    suppress("TYPE_VARIANCE_CONFLICT")
    override fun builder(): Builder<E>
}


public interface ImmutableList<out E>: List<E>, ImmutableCollection<E> {
    suppress("TYPE_VARIANCE_CONFLICT")
    override fun add(element: E): ImmutableList<E>

    suppress("TYPE_VARIANCE_CONFLICT")
    override fun addAll(elements: Iterable<E>): ImmutableList<E> // = super<ImmutableCollection>.addAll(elements) as ImmutableList

    override fun remove(element: Any?): ImmutableList<E>

    override fun removeAll(c: Iterable<Any?>): ImmutableList<E>

    override fun retainAll(c: Iterable<Any?>): ImmutableList<E>

    override fun clear(): ImmutableList<E>


    suppress("TYPE_VARIANCE_CONFLICT")
    fun addAll(index: Int, c: Iterable<E>): ImmutableList<E> // = builder().apply { addAll(index, c.toList()) }.build()

    suppress("TYPE_VARIANCE_CONFLICT")
    fun set(index: Int, element: E): ImmutableList<E>

    /**
     * Inserts an element into the list at the specified [index].
     */
    suppress("TYPE_VARIANCE_CONFLICT")
    public fun add(index: Int, element: E): ImmutableList<E>

    public fun removeAt(index: Int): ImmutableList<E>


    override fun subList(fromIndex: Int, toIndex: Int): ImmutableList<E>

    interface Builder<E>: MutableList<E>, ImmutableCollection.Builder<E> {
        override fun build(): ImmutableList<E>
    }

    suppress("TYPE_VARIANCE_CONFLICT")
    override fun builder(): Builder<E>
}


public interface ImmutableMap<K, out V>: Map<K, V> {

    override fun keySet(): ImmutableSet<K>

    override fun values(): ImmutableCollection<V>

    override fun entrySet(): ImmutableSet<Map.Entry<K, V>>

    suppress("TYPE_VARIANCE_CONFLICT")
    fun put(key: K, value: V): ImmutableMap<K, V>

    public fun remove(key: Any?): ImmutableMap<K, V>

    suppress("TYPE_VARIANCE_CONFLICT")
    public fun putAll(m: Iterable<Map.Entry<K, V>>): ImmutableMap<K, V>  // m: Iterable<Map.Entry<K, V>> or Map<out K,V> or Iterable<Pair<K, V>>

    public fun clear(): ImmutableMap<K, V>

    interface Builder<K, V>: MutableMap<K, V> {
        fun build(): ImmutableMap<K, V>
    }

    suppress("TYPE_VARIANCE_CONFLICT")
    fun builder(): Builder<K, V>
}