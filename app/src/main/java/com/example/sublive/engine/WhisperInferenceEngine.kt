package com.example.sublive.engine

import android.content.Context
import android.util.Log
import com.example.sublive.audio.AudioConfig
import com.example.sublive.model.ModelManager
import com.example.sublive.model.ModelTier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.sqrt

data class InferenceResult(
    val recognizedText: String,
    val latencyMs: Long,
    val modelTier: ModelTier,
    val isSpeechDetected: Boolean,
    val accelerationEngine: String = "LiteRT CPU (XNNPACK)"
) {
    val originalKorean: String get() = recognizedText
    val translatedEnglish: String get() = recognizedText
}

/**
 * Local Whisper TFLite Inference Pipeline.
 *
 * Runs purely on captured audio (via AudioPlaybackCapture system audio or microphone).
 * Strictly zero audio playback, zero earcon beeping, zero audio focus acquisition, and
 * zero synthetic fallback values.
 */
class WhisperInferenceEngine(
    private val context: Context,
    private val modelManager: ModelManager
) {
    companion object {
        private const val TAG = "WhisperInferenceEngine"
    }

    val liteRtEngine = LiteRtWhisperEngine(context)
    private var currentModelTier: ModelTier = ModelTier.TFLITE_TINY
    private var activeBackend: AccelerationBackend = AccelerationBackend.CPU

    fun setAccelerationBackend(backend: AccelerationBackend) {
        activeBackend = backend
    }

    /**
     * Run inference on a 16kHz PCM audio window.
     * Returns recognized live subtitles from the TFLite Whisper model.
     * Never emits test phrases or placeholder values.
     */
    suspend fun processAudioWindow(
        samples: ShortArray,
        sampleCount: Int,
        whisperTranslateToEnglish: Boolean = false
    ): InferenceResult = withContext(Dispatchers.Default) {
        val startTime = System.currentTimeMillis()
        val activeTier = modelManager.activeModel.value
        currentModelTier = activeTier

        if (sampleCount <= 0) {
            return@withContext InferenceResult(
                recognizedText = "",
                latencyMs = 0,
                modelTier = activeTier,
                isSpeechDetected = false,
                accelerationEngine = liteRtEngine.activeBackendName
            )
        }

        // Voice Activity Detection: RMS energy and Zero-Crossing Rate
        var sumEnergy = 0.0
        var zeroCrossings = 0
        var prevSign = samples[0] >= 0

        for (i in 0 until sampleCount) {
            val s = samples[i]
            val f = s / 32768.0f
            sumEnergy += (f * f).toDouble()

            val currentSign = s >= 0
            if (currentSign != prevSign) {
                zeroCrossings++
                prevSign = currentSign
            }
        }

        val rms = sqrt(sumEnergy / sampleCount).toFloat()

        // Voice Activity Detection: RMS energy
        // RMS > 0.001f (~ -60 dBFS) captures all audible voice and media playback.
        val isSpeechDetected = rms > 0.001f

        if (!isSpeechDetected) {
            return@withContext InferenceResult(
                recognizedText = "",
                latencyMs = System.currentTimeMillis() - startTime,
                modelTier = activeTier,
                isSpeechDetected = false,
                accelerationEngine = liteRtEngine.activeBackendName
            )
        }

        // Run inference via active Whisper TFLite model or first downloaded model on disk
        val resolvedTier = if (modelManager.getModelFile(activeTier)?.exists() == true) {
            activeTier
        } else {
            modelManager.getFirstDownloadedTier() ?: activeTier
        }

        val modelFile = modelManager.getModelFile(resolvedTier)
        if (modelFile != null && modelFile.exists()) {
            if (!liteRtEngine.isModelLoaded || liteRtEngine.activeModelPath != modelFile.absolutePath) {
                liteRtEngine.loadModel(modelFile, activeBackend)
            }
            val liteRtOutput = liteRtEngine.runInference(samples, sampleCount, whisperTranslateToEnglish)
            if (liteRtOutput.recognizedText.isNotBlank()) {
                return@withContext InferenceResult(
                    recognizedText = liteRtOutput.recognizedText,
                    latencyMs = liteRtOutput.latencyMs,
                    modelTier = resolvedTier,
                    isSpeechDetected = true,
                    accelerationEngine = liteRtOutput.backendUsed
                )
            }
        }

        // If model hasn't decoded text for this window yet, return clean empty result.
        // No fake fallbacks.
        val totalLatency = System.currentTimeMillis() - startTime
        InferenceResult(
            recognizedText = "",
            latencyMs = totalLatency,
            modelTier = resolvedTier,
            isSpeechDetected = false,
            accelerationEngine = if (modelFile?.exists() == true) liteRtEngine.activeBackendName else "No Model (Download in Models tab)"
        )
    }

    suspend fun processAudioWindow(
        samples: ShortArray,
        sampleCount: Int,
        targetMode: Any?
    ): InferenceResult = processAudioWindow(samples, sampleCount, false)
}
