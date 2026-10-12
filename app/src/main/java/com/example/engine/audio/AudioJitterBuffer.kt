package com.example.engine.audio

import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * Thread-safe bounded jitter ring buffer for raw audio PCM frames.
 * Decouples hardware AudioRecord readers from the mixing & encoder feed loop.
 *
 * Prevents memory leaks under CPU load by enforcing a maximum bounded queue depth.
 * Automatically provides padded silence on underruns to prevent timestamp drift.
 */
class AudioJitterBuffer(
    private val name: String,
    private val maxCapacity: Int = 16 // 16 * 21.33ms = ~341ms buffer headroom
) {
    private val queue = ConcurrentLinkedQueue<ShortArray>()
    private val currentSize = AtomicInteger(0)

    val underrunsCount = AtomicLong(0)
    val overrunsCount = AtomicLong(0)
    val totalFramesEnqueued = AtomicLong(0)
    val totalFramesDequeued = AtomicLong(0)

    /**
     * Enqueues a PCM audio frame. If the bounded buffer is full (overrun),
     * the oldest frame is discarded to maintain low latency.
     */
    fun push(pcm: ShortArray) {
        val copy = pcm.clone()

        while (currentSize.get() >= maxCapacity) {
            val dropped = queue.poll()
            if (dropped != null) {
                currentSize.decrementAndGet()
                overrunsCount.incrementAndGet()
            } else {
                break
            }
        }

        queue.offer(copy)
        currentSize.incrementAndGet()
        totalFramesEnqueued.incrementAndGet()
    }

    /**
     * Dequeues the next PCM frame into [dest].
     * If the buffer is empty (underrun), fills [dest] with silence (zeros)
     * so that the encoder timeline NEVER stalls or loses sync.
     *
     * @return true if real audio was supplied; false if padded with silence due to underrun.
     */
    fun poll(dest: ShortArray): Boolean {
        val frame = queue.poll()
        return if (frame != null) {
            currentSize.decrementAndGet()
            totalFramesDequeued.incrementAndGet()
            val len = dest.size.coerceAtMost(frame.size)
            System.arraycopy(frame, 0, dest, 0, len)
            true
        } else {
            dest.fill(0)
            underrunsCount.incrementAndGet()
            false
        }
    }

    fun size(): Int = currentSize.get()

    fun clear() {
        queue.clear()
        currentSize.set(0)
    }

    fun resetStats() {
        underrunsCount.set(0)
        overrunsCount.set(0)
        totalFramesEnqueued.set(0)
        totalFramesDequeued.set(0)
    }
}
