package com.example.sublive.audio

import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.math.min

/**
 * Thread-safe circular audio ring buffer for 16-bit PCM audio samples.
 * Designed to buffer continuous audio streaming from AudioRecord and
 * support extracting sliding/overlapping audio windows for speech inference.
 */
class AudioRingBuffer(val capacity: Int = AudioConfig.RING_BUFFER_CAPACITY_SAMPLES) {

    private val buffer = ShortArray(capacity)
    private var writeHead = 0
    private var availableSamples = 0
    private val lock = ReentrantLock()

    // Diagnostics
    var totalSamplesWritten: Long = 0
        private set
    var droppedSamplesCount: Long = 0
        private set

    /**
     * Write new PCM audio samples into the ring buffer.
     * If the buffer overflows, older samples are overwritten and dropped count is tracked.
     */
    fun write(samples: ShortArray, offset: Int = 0, length: Int = samples.size) {
        if (length <= 0) return

        lock.withLock {
            var samplesToWrite = length
            var srcOffset = offset

            // If incoming batch is larger than capacity, keep only the latest `capacity` samples
            if (samplesToWrite > capacity) {
                srcOffset += (samplesToWrite - capacity)
                droppedSamplesCount += (samplesToWrite - capacity)
                samplesToWrite = capacity
            }

            // Track dropped samples if existing unread samples are being overwritten
            val overflow = (availableSamples + samplesToWrite) - capacity
            if (overflow > 0) {
                droppedSamplesCount += overflow
                availableSamples = capacity - samplesToWrite
            }

            // Write in one or two segments if wrapping around
            val firstChunk = min(samplesToWrite, capacity - writeHead)
            System.arraycopy(samples, srcOffset, buffer, writeHead, firstChunk)

            val secondChunk = samplesToWrite - firstChunk
            if (secondChunk > 0) {
                System.arraycopy(samples, srcOffset + firstChunk, buffer, 0, secondChunk)
            }

            writeHead = (writeHead + samplesToWrite) % capacity
            availableSamples += samplesToWrite
            totalSamplesWritten += length
        }
    }

    /**
     * Read the most recent `count` samples (e.g. For a 2.5s sliding window)
     * without advancing the read position.
     * Returns the actual number of samples copied into [outBuffer].
     */
    fun readLatest(outBuffer: ShortArray, count: Int): Int {
        lock.withLock {
            val toRead = min(count, min(availableSamples, outBuffer.size))
            if (toRead <= 0) return 0

            // Start position is writeHead - toRead (with wrap-around)
            var start = writeHead - toRead
            if (start < 0) {
                start += capacity
            }

            val firstChunk = min(toRead, capacity - start)
            System.arraycopy(buffer, start, outBuffer, 0, firstChunk)

            val secondChunk = toRead - firstChunk
            if (secondChunk > 0) {
                System.arraycopy(buffer, 0, outBuffer, firstChunk, secondChunk)
            }

            return toRead
        }
    }

    /**
     * Read and consume `count` oldest samples from the buffer.
     */
    fun drain(outBuffer: ShortArray, count: Int): Int {
        lock.withLock {
            val toRead = min(count, min(availableSamples, outBuffer.size))
            if (toRead <= 0) return 0

            var start = writeHead - availableSamples
            if (start < 0) {
                start += capacity
            }

            val firstChunk = min(toRead, capacity - start)
            System.arraycopy(buffer, start, outBuffer, 0, firstChunk)

            val secondChunk = toRead - firstChunk
            if (secondChunk > 0) {
                System.arraycopy(buffer, 0, outBuffer, firstChunk, secondChunk)
            }

            availableSamples -= toRead
            return toRead
        }
    }

    /**
     * Get the count of available unread samples.
     */
    fun available(): Int = lock.withLock { availableSamples }

    /**
     * Clear all buffered audio.
     */
    fun clear() {
        lock.withLock {
            writeHead = 0
            availableSamples = 0
        }
    }
}
