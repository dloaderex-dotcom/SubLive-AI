package com.example.sublive.audio

import kotlin.math.log10
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Audio analysis utility for calculating RMS, Peak amplitude, dBFS,
 * and detecting silence or restricted DRM audio streams.
 */
data class AudioLevel(
    val rms: Float, // Normalized 0.0 to 1.0
    val peak: Float, // Normalized 0.0 to 1.0
    val decibels: Float, // dBFS, typically -96 dB to 0 dB
    val isSpeechLikely: Boolean,
    val isZeroSignal: Boolean
)

object AudioLevelMeter {
    private const val SILENCE_DB_THRESHOLD = -45.0f
    private const val ZERO_SIGNAL_TOLERANCE = 5 // max absolute sample value to consider as zero

    /**
     * Compute audio levels from a buffer of 16-bit PCM samples.
     */
    fun analyze(samples: ShortArray, length: Int): AudioLevel {
        if (length <= 0) {
            return AudioLevel(
                rms = 0f,
                peak = 0f,
                decibels = -96f,
                isSpeechLikely = false,
                isZeroSignal = true
            )
        }

        var sumSquare = 0.0
        var maxSample = 0
        var nonZeroCount = 0

        for (i in 0 until length) {
            val sample = samples[i].toInt()
            val absSample = if (sample < 0) -sample else sample
            if (absSample > maxSample) {
                maxSample = absSample
            }
            if (absSample > ZERO_SIGNAL_TOLERANCE) {
                nonZeroCount++
            }
            sumSquare += (sample * sample).toDouble()
        }

        val meanSquare = sumSquare / length
        val rmsRaw = sqrt(meanSquare)
        val normalizedRms = (rmsRaw / 32768.0).toFloat().coerceIn(0f, 1f)
        val normalizedPeak = (maxSample / 32768.0f).coerceIn(0f, 1f)

        // Calculate dBFS
        val db = if (rmsRaw > 0.0001) {
            (20.0 * log10(rmsRaw / 32768.0)).toFloat().coerceIn(-96f, 0f)
        } else {
            -96f
        }

        val isZeroSignal = nonZeroCount == 0
        val isSpeechLikely = !isZeroSignal && db > SILENCE_DB_THRESHOLD

        return AudioLevel(
            rms = normalizedRms,
            peak = normalizedPeak,
            decibels = db,
            isSpeechLikely = isSpeechLikely,
            isZeroSignal = isZeroSignal
        )
    }
}
