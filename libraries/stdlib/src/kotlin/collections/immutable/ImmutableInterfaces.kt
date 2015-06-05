package kotlin


public interface ImmutableCollection<out E>: Collection<E> {
    suppress("TYPE_VARIANCE_CONFLICT")
    fun add(element: E): ImmutableCollection<E>

    suppress("TYPE_VARIANCE_CONFLICT")
    fun addAll(elements: Collection<E>): ImmutableCollection<E>

    fun remove(element: Any?): ImmutableCollection<E>

    fun removeAll(c: Collection<Any?>): ImmutableCollection<E>

    fun retainAll(c: Collection<Any?>): ImmutableCollection<E>

    fun clear(): ImmutableCollection<E>

    interface Builder<E>: MutableCollection<E> {
        fun build(): ImmutableCollection<E>
    }
}




public interface ImmutableSet<out E>: Set<E>, ImmutableCollection<E> {
    suppress("TYPE_VARIANCE_CONFLICT")
    override fun add(element: E): ImmutableSet<E>

    suppress("TYPE_VARIANCE_CONFLICT")
    override fun addAll(elements: Collection<E>): ImmutableSet<E>

    override fun remove(element: Any?): ImmutableSet<E>

    override fun removeAll(c: Collection<Any?>): ImmutableSet<E>

    override fun retainAll(c: Collection<Any?>): ImmutableSet<E>

    override fun clear(): ImmutableSet<E>

    interface Builder<E>: MutableSet<E>, ImmutableCollection.Builder<E> {
        override fun build(): ImmutableSet<E>
    }
}


public interface ImmutableList<out E>: List<E>, ImmutableCollection<E> {
    suppress("TYPE_VARIANCE_CONFLICT")
    override fun add(element: E): ImmutableList<E>

    suppress("TYPE_VARIANCE_CONFLICT")
    override fun addAll(elements: Collection<E>): ImmutableList<E>

    override fun remove(element: Any?): ImmutableList<E>

    override fun removeAll(c: Collection<Any?>): ImmutableList<E>

    override fun retainAll(c: Collection<Any?>): ImmutableList<E>

    override fun clear(): ImmutableList<E>


    suppress("TYPE_VARIANCE_CONFLICT")
    fun addAll(index: Int, c: Collection<E>): ImmutableList<E>

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
}


public interface ImmutableMap<K, out V>: Map<K, V> {

    override fun keySet(): ImmutableSet<K>

    override fun values(): ImmutableCollection<V>

    override fun entrySet(): ImmutableSet<Map.Entry<K, V>>

    suppress("TYPE_VARIANCE_CONFLICT")
    fun put(key: K, value: V): ImmutableMap<K, V>

    public fun remove(key: Any?): ImmutableMap<K, V>

    suppress("TYPE_VARIANCE_CONFLICT")
    public fun putAll(m: Map<out K, V>): ImmutableMap<K, V>

    public fun clear(): ImmutableMap<K, V>

    interface Builder<K, V>: MutableMap<K, V> {
        fun build(): ImmutableMap<K, V>
    }
}