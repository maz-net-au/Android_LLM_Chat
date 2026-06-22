package net.maz.llamachat.data

import java.util.concurrent.atomic.AtomicLong

/**
 * Monotonic id generator for conversations and messages. Seeded from the wall
 * clock so ids stay increasing across process restarts, with an in-process
 * counter to break ties when several ids are minted in the same millisecond.
 */
object IdGen {
    private val counter = AtomicLong(System.currentTimeMillis() * 1000)
    fun next(): Long = counter.incrementAndGet()
}
