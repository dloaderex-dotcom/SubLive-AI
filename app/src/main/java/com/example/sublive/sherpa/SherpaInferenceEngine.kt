package com.example.sublive.sherpa

import android.content.Context
import android.util.Log
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineSenseVoiceModelConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SherpaInferenceEngine(
    private val context: Context,
    private val modelManager: SenseVoiceModelManager
) {
    companion object {
        private const val TAG = "SherpaInferenceEngine"
        private val SPECIAL_TAGS_REGEX = Regex("<\\|.*?\\|>")
    }

    private var recognizer: OfflineRecognizer? = null
    private var isModelLoaded = false

    @Synchronized
    fun loadModel(): Boolean {
        if (!modelManager.isModelReady()) return false
        if (isModelLoaded && recognizer != null) return true

        return try {
            closeModel()
            Log.i(TAG, "Initializing Sherpa-ONNX SenseVoice engine...")

            val senseVoiceConfig = OfflineSenseVoiceModelConfig(
                model = modelManager.modelFile.absolutePath,
                language = "auto",
                useInverseTextNormalization = true
            )

            val modelConfig = OfflineModelConfig(
                senseVoice = senseVoiceConfig,
                tokens = modelManager.tokensFile.absolutePath,
                numThreads = 2,
                debug = false,
                provider = "cpu"
            )

            val recognizerConfig = OfflineRecognizerConfig(
                modelConfig = modelConfig,
                decodingMethod = "greedy_search"
            )

            // Pass null for assetManager since files are loaded from filesDir
            recognizer = OfflineRecognizer(
                assetManager = null,
                config = recognizerConfig
            )

            isModelLoaded = true
            Log.i(TAG, "Sherpa-ONNX SenseVoice recognizer successfully initialized.")
            true
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to initialize Sherpa-ONNX recognizer: ${e.message}", e)
            closeModel()
            false
        }
    }

    suspend fun processAudioWindow(
        samples: ShortArray,
        sampleCount: Int
    ): String = withContext(Dispatchers.Default) {
        if (!isModelLoaded) {
            val loaded = loadModel()
            if (!loaded) return@withContext ""
        }

        val rec = recognizer ?: return@withContext ""

        try {
            // Convert ShortArray PCM samples to normalized FloatArray [-1.0f, 1.0f]
            val floatSamples = FloatArray(sampleCount) { i ->
                samples[i] / 32768.0f
            }

            val stream = rec.createStream()
            try {
                stream.acceptWaveform(floatSamples, 16000)
                rec.decode(stream)
                val rawText = rec.getResult(stream).text

                // SenseVoice may include emotion/audio event tags like <|HAPPY|>, <|zh|>, <|Speech|>
                // Strip them cleanly for user display
                val cleanedText = rawText.replace(SPECIAL_TAGS_REGEX, " ")
                    .replace(Regex("\\s+"), " ")
                    .trim()

                cleanedText
            } finally {
                stream.release()
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Error in Sherpa-ONNX SenseVoice inference: ${e.message}", e)
            ""
        }
    }

    @Synchronized
    fun closeModel() {
        try {
            recognizer?.release()
        } catch (e: Throwable) {
            Log.w(TAG, "Error releasing Sherpa recognizer: ${e.message}")
        }
        recognizer = null
        isModelLoaded = false
    }
}
