package com.example.sublive.audio

import android.media.AudioFormat

/**
 * Standardized audio configuration for SubLive AI.
 * Whisper and offline speech recognition models require 16,000 Hz, 16-bit Mono PCM.
 */
object AudioConfig {
    const val SAMPLE_RATE = 16000
    const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
    const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    const val BYTES_PER_SAMPLE = 2 // 16-bit PCM = 2 bytes per sample

    // Bytes per second = 16000 * 2 = 32,000 bytes/sec
    const val BYTES_PER_SECOND = SAMPLE_RATE * BYTES_PER_SAMPLE

    // Default chunk size read from AudioRecord (e.g. 100ms = 1,600 samples = 3,200 bytes)
    const val READ_CHUNK_SAMPLES = 1600
    const val READ_CHUNK_BYTES = READ_CHUNK_SAMPLES * BYTES_PER_SAMPLE

    // Rolling window for inference (2.5 seconds = 40,000 samples)
    const val WINDOW_SAMPLES = 40000
    const val WINDOW_BYTES = WINDOW_SAMPLES * BYTES_PER_SAMPLE

    // Sliding step interval (0.5 seconds = 8,000 samples)
    const val STEP_SAMPLES = 8000
    const val STEP_BYTES = STEP_SAMPLES * BYTES_PER_SAMPLE

    // Ring buffer capacity (holds up to 6.0 seconds of audio = 96,000 samples)
    const val RING_BUFFER_CAPACITY_SAMPLES = 96000
}
