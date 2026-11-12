package io.github.kemurixd.lurk.chat

/**
 * Fixed-capacity buffer that drops the oldest items first. Not thread-safe: the owner confines
 * it to a single coroutine.
 */
class RingBuffer<T>(
    val capacity: Int = 1000,
) {
    init {
        require(capacity > 0) { "capacity must be positive" }
    }

    private val items = ArrayDeque<T>()

    val size: Int get() = items.size

    /** Appends [added] in order and returns the evicted oldest items, oldest first. */
    fun addAll(added: List<T>): List<T> {
        items.addAll(added)
        val overflow = items.size - capacity
        if (overflow <= 0) return emptyList()
        val evicted = ArrayList<T>(overflow)
        repeat(overflow) { evicted += items.removeFirst() }
        return evicted
    }

    fun toList(): List<T> = items.toList()

    fun replaceAll(transform: (T) -> T) {
        for (i in items.indices) items[i] = transform(items[i])
    }

    fun removeIf(predicate: (T) -> Boolean) {
        items.removeAll(predicate)
    }
}
