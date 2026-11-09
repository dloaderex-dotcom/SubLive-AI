package com.example.sublive.vosk

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.vosk.LibVosk
import org.vosk.LogLevel
import org.vosk.Model
import org.vosk.Recognizer
import java.io.File

class VoskInferenceEngine(
    private val context: Context,
    private val modelManager: VoskModelManager
) {
    companion object {
        private const val TAG = "VoskInferenceEngine"
    }

    private var voskModel: Model? = null
    private var voskRecognizer: Recognizer? = null
    private var loadedModelPath: String? = null

    init {
        try {
            LibVosk.setLogLevel(LogLevel.INFO)
        } catch (e: Throwable) {
            Log.w(TAG, "LibVosk setLogLevel note: ${e.message}")
        }
    }

    val isModelLoaded: Boolean get() = voskModel != null && voskRecognizer != null

    @Synchronized
    fun loadModel(): Boolean {
        if (!modelManager.isModelReady()) return false
        val path = modelManager.modelDir.absolutePath
        if (isModelLoaded && loadedModelPath == path) return true

        return try {
            closeModel()
            Log.i(TAG, "Initializing Vosk model from $path")
            val model = Model(path)
            val recognizer = Recognizer(model, 16000.0f)
            voskModel = model
            voskRecognizer = recognizer
            loadedModelPath = path
            Log.i(TAG, "Vosk recognizer successfully initialized at 16000 Hz")
            true
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to initialize Vosk ASR model: ${e.message}", e)
            closeModel()
            false
        }
    }

    suspend fun processAudioWindow(
        samples: ShortArray,
        sampleCount: Int
    ): String = withContext(Dispatchers.Default) {
        if (sampleCount <= 0) return@withContext ""

        if (!isModelLoaded) {
            val loaded = loadModel()
            if (!loaded) return@withContext ""
        }

        val recognizer = voskRecognizer ?: return@withContext ""

        try {
            val isFinal = recognizer.acceptWaveForm(samples, sampleCount)
            val jsonStr = if (isFinal) {
                recognizer.result
            } else {
                recognizer.partialResult
            }

            val jsonObj = JSONObject(jsonStr)
            val text = if (isFinal) {
                jsonObj.optString("text", "")
            } else {
                jsonObj.optString("partial", "")
            }

            text.trim()
        } catch (e: Exception) {
            Log.e(TAG, "Error in Vosk speech recognition: ${e.message}", e)
            ""
        }
    }

    @Synchronized
    fun closeModel() {
        try {
            voskRecognizer?.close()
        } catch (e: Throwable) {
            Log.w(TAG, "Error closing Vosk Recognizer", e)
        }
        try {
            voskModel?.close()
        } catch (e: Throwable) {
            Log.w(TAG, "Error closing Vosk Model", e)
        }
        voskRecognizer = null
        voskModel = null
        loadedModelPath = null
    }
}
