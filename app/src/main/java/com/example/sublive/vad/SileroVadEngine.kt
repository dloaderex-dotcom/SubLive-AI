package com.example.sublive.vad

import android.content.Context
import android.util.Log
import com.k2fsa.sherpa.onnx.SileroVadModelConfig
import com.k2fsa.sherpa.onnx.TenVadModelConfig
import com.k2fsa.sherpa.onnx.Vad
import com.k2fsa.sherpa.onnx.VadModelConfig
import kotlin.math.sqrt

/**
 * High-precision neural Voice Activity Detector using Silero VAD (ONNX quantized).
 * Eliminates background noise and silence before feeding into SenseVoice-Small ASR.
 */
class SileroVadEngine(
    private val context: Context,
    private val modelManager: SileroVadManager
) {
    companion object {
        private const val TAG = "SileroVadEngine"
    }

    private var vad: Vad? = null
    private var isLoaded: Boolean = false

    @Synchronized
    fun ensureLoaded(): Boolean {
        if (!modelManager.isModelReady()) return false
        if (isLoaded && vad != null) return true

        return try {
            release()
            val sileroConfig = SileroVadModelConfig(
                model = modelManager.vadFile.absolutePath,
                threshold = 0.5f,
                minSilenceDuration = 0.25f,
                minSpeechDuration = 0.1f,
                windowSize = 512,
                maxSpeechDuration = 30.0f
            )

            val tenConfig = TenVadModelConfig(
                model = "",
                threshold = 0.5f,
                minSilenceDuration = 0.25f,
                minSpeechDuration = 0.1f,
                windowSize = 512,
                maxSpeechDuration = 30.0f
            )

            val vadConfig = VadModelConfig(
                sileroVadModelConfig = sileroConfig,
                tenVadModelConfig = tenConfig,
                sampleRate = 16000,
                numThreads = 2,
                provider = "cpu",
                debug = false
            )

            vad = Vad(assetManager = null, config = vadConfig)
            isLoaded = true
            Log.i(TAG, "Silero VAD successfully initialized: ${modelManager.vadFile.absolutePath}")
            true
        } catch (e: Throwable) {
            Log.w(TAG, "Failed initializing Silero VAD, will use energy VAD fallback: ${e.message}")
            release()
            false
        }
    }

    /**
     * Determines whether human speech is active in the 16kHz audio window.
     */
    fun isSpeech(samples: ShortArray, sampleCount: Int): Boolean {
        if (sampleCount <= 0) return false

        // Compute RMS energy as baseline gate
        var sumEnergy = 0.0
        for (i in 0 until sampleCount) {
            val f = samples[i] / 32768.0f
            sumEnergy += (f * f).toDouble()
        }
        val rms = sqrt(sumEnergy / sampleCount).toFloat()
        if (rms < 0.001f) {
            return false
        }

        if (ensureLoaded()) {
            val v = vad
            if (v != null) {
                try {
                    val floatSamples = FloatArray(sampleCount) { i -> samples[i] / 32768.0f }
                    v.acceptWaveform(floatSamples)
                    val speechDetected = v.isSpeechDetected() || !v.empty()
                    return speechDetected
                } catch (e: Throwable) {
                    Log.w(TAG, "Silero compute error: ${e.message}")
                }
            }
        }

        // Reliable fallback if model is downloading
        return rms > 0.0015f
    }

    @Synchronized
    fun release() {
        try {
            vad?.release()
        } catch (e: Throwable) {
            Log.w(TAG, "Error releasing Vad: ${e.message}")
        }
        vad = null
        isLoaded = false
    }
}
