package com.eduardoflores.rolabox.device.podcasts.impl

/**
 * The answers to the last [capacity] questions, in memory. Asking for one makes it the most recent, and adding one
 * past the capacity forgets the one asked for longest ago. Safe to use from any thread.
 */
internal class RecentAnswers<K : Any, V : Any>(capacity: Int) {
    // In access order, so the eldest entry is the least recently used.
    private val answers = object : LinkedHashMap<K, V>(capacity, LOAD_FACTOR, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>?): Boolean = size > capacity
    }

    @Synchronized
    operator fun get(key: K): V? = answers[key]

    @Synchronized
    operator fun set(key: K, value: V) {
        answers[key] = value
    }

    private companion object {
        const val LOAD_FACTOR = 0.75f
    }
}
