package com.nhn.gps.location.phone.tracker.ui.location

internal class PerModeResultStore<M, V> {
    private val values = mutableMapOf<M, V>()

    operator fun get(mode: M): V? = values[mode]

    operator fun set(mode: M, value: V) {
        values[mode] = value
    }

    fun clear() = values.clear()
}

internal class RouteRequestGeneration {
    private var generation: Long = 0L

    fun next(): Long = ++generation
    fun current(): Long = generation
    fun isCurrent(token: Long): Boolean = token == generation
}
