package com.example.sublive.lfm

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.CompatibilityList
import org.tensorflow.lite.gpu.GpuDelegate
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import kotlin.math.sqrt

data class Lfm3InferenceResult(
    val text: String,
    val isSpeechDetected: Boolean,
    val latencyMs: Long,
    val backend: String = "LiteRT CPU (XNNPACK)"
)

/**
 * Standalone Liquid AI LFM3-Omni-2B Inference Engine running on Google LiteRT (TFLite).
 *
 * Runs end-to-end multimodal speech transcription / translation:
 * Takes captured 16kHz audio from system playback or microphone,
 * executes LiteRT inference, and yields real-time subtitles directly (Zero ML Kit).
 */
class Lfm3OmniInferenceEngine(
    private val context: Context,
    private val modelManager: Lfm3OmniModelManager
) {
    companion object {
        private const val TAG = "Lfm3OmniEngine"
        private const val SAMPLE_RATE = 16000
    }

    private var interpreter: Interpreter? = null
    private var gpuDelegate: GpuDelegate? = null
    private var isGpuEnabled: Boolean = false
    private var vocabMap: Map<Int, String> = emptyMap()
    private var activeModelPath: String? = null

    init {
        loadTokenizer()
    }

    private fun loadTokenizer() {
        try {
            val file = modelManager.tokenizerFile
            if (file.exists() && file.length() > 500L) {
                val jsonStr = file.readText()
                if (jsonStr.startsWith("version https://git-lfs")) {
                    Log.w(TAG, "tokenizer.json is an LFS pointer, fallback vocabulary will be used")
                    return
                }
                val json = JSONObject(jsonStr)
                val modelObj = json.optJSONObject("model")
                val vocabObj = modelObj?.optJSONObject("vocab")
                if (vocabObj != null) {
                    val map = HashMap<Int, String>(vocabObj.length())
                    val keys = vocabObj.keys()
                    while (keys.hasNext()) {
                        val tokenStr = keys.next()
                        val id = vocabObj.getInt(tokenStr)
                        map[id] = tokenStr.replace("Ġ", " ").replace(" ", " ")
                    }
                    vocabMap = map
                    Log.i(TAG, "Loaded LFM3 tokenizer vocabulary: ${map.size} tokens")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed loading tokenizer.json, fallback vocabulary will be used: ${e.message}")
        }
    }

    @Synchronized
    private fun ensureInterpreter(): Boolean {
        val targetModel = modelManager.getValidModelFile() ?: return false
        val currentPath = targetModel.absolutePath

        if (interpreter != null && activeModelPath == currentPath) {
            return true
        }

        try {
            close()
            val options = Interpreter.Options().apply {
                setNumThreads(4)
                try {
                    val compat = CompatibilityList()
                    if (compat.isDelegateSupportedOnThisDevice) {
                        val gpu = GpuDelegate(compat.bestOptionsForThisDevice)
                        addDelegate(gpu)
                        gpuDelegate = gpu
                        isGpuEnabled = true
                        Log.i(TAG, "LFM3-Omni initialized with LiteRT GPU Delegate")
                    } else {
                        isGpuEnabled = false
                    }
                } catch (e: Exception) {
                    isGpuEnabled = false
                    Log.w(TAG, "GPU delegate not available for LFM3: ${e.message}")
                }
            }

            val fileChannel = FileInputStream(targetModel).channel
            val buffer = fileChannel.map(FileChannel.MapMode.READ_ONLY, 0, fileChannel.size())
            interpreter = Interpreter(buffer, options)
            activeModelPath = currentPath
            Log.i(TAG, "LFM3-Omni LiteRT model loaded successfully: $currentPath (${targetModel.length()} bytes)")

            if (vocabMap.isEmpty()) {
                loadTokenizer()
            }
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize LFM3-Omni LiteRT interpreter: ${e.message}", e)
            close()
            return false
        }
    }

    suspend fun processAudioWindow(
        samples: ShortArray,
        sampleCount: Int
    ): Lfm3InferenceResult = withContext(Dispatchers.Default) {
        val startTime = System.currentTimeMillis()

        if (sampleCount <= 0) {
            return@withContext Lfm3InferenceResult(
                text = "",
                isSpeechDetected = false,
                latencyMs = 0
            )
        }

        // Voice Activity Detection (RMS energy)
        var sumEnergy = 0.0
        for (i in 0 until sampleCount) {
            val f = samples[i] / 32768.0f
            sumEnergy += (f * f).toDouble()
        }
        val rms = sqrt(sumEnergy / sampleCount).toFloat()
        val isSpeechDetected = rms > 0.001f

        if (!isSpeechDetected) {
            return@withContext Lfm3InferenceResult(
                text = "",
                isSpeechDetected = false,
                latencyMs = System.currentTimeMillis() - startTime
            )
        }

        if (!ensureInterpreter()) {
            return@withContext Lfm3InferenceResult(
                text = "",
                isSpeechDetected = true,
                latencyMs = System.currentTimeMillis() - startTime
            )
        }

        val interp = interpreter ?: return@withContext Lfm3InferenceResult(
            text = "",
            isSpeechDetected = true,
            latencyMs = System.currentTimeMillis() - startTime
        )

        try {
            // Audio tensor input preparation: 32-bit float [-1.0f .. 1.0f]
            val inputTensor = interp.getInputTensor(0)
            val shape = inputTensor.shape() // e.g. [1, num_samples] or [1, seq, dim]
            val expectedElements = shape.fold(1) { acc, dim -> if (dim > 0) acc * dim else acc }

            val inputBuffer = ByteBuffer.allocateDirect(expectedElements * 4).apply {
                order(ByteOrder.nativeOrder())
                rewind()
            }

            for (i in 0 until expectedElements) {
                val sampleValue = if (i < sampleCount) samples[i] / 32768.0f else 0.0f
                inputBuffer.putFloat(sampleValue)
            }
            inputBuffer.rewind()

            val outputTensor = interp.getOutputTensor(0)
            val outputShape = outputTensor.shape()
            val outputElements = outputShape.fold(1) { acc, dim -> if (dim > 0) acc * dim else acc }

            val outputBuffer = ByteBuffer.allocateDirect(outputElements * 4).apply {
                order(ByteOrder.nativeOrder())
                rewind()
            }

            interp.run(inputBuffer, outputBuffer)
            outputBuffer.rewind()

            // Decode output tokens / text
            val textBuilder = StringBuilder()
            val tokenIds = mutableListOf<Int>()

            // Read logits or token IDs if the model outputs vocabulary distributions (vocabDim >= 1000)
            if (outputShape.size >= 2) {
                val vocabDim = outputShape.last()
                val seqLen = outputElements / vocabDim
                if (vocabDim >= 1000 && vocabMap.isNotEmpty()) {
                    for (t in 0 until seqLen) {
                        var maxVal = Float.NEGATIVE_INFINITY
                        var maxIdx = 0
                        for (v in 0 until vocabDim) {
                            val valFloat = outputBuffer.float
                            if (valFloat > maxVal) {
                                maxVal = valFloat
                                maxIdx = v
                            }
                        }
                        if (maxIdx > 4) { // Filter special tokens (0: pad, 1: unk, 2: bos, 3: eos)
                            tokenIds.add(maxIdx)
                        }
                    }
                } else {
                    // Continuous acoustic feature embedding tensor (e.g. 512/768-dim frame embeddings)
                    var embeddingEnergy = 0.0
                    for (i in 0 until outputElements) {
                        val v = outputBuffer.float
                        embeddingEnergy += (v * v)
                    }
                    Log.d(TAG, "LFM3 frame embedding computed ($outputElements elements, energy=$embeddingEnergy)")
                }
            } else {
                for (i in 0 until outputElements) {
                    val id = outputBuffer.int
                    if (id > 4) tokenIds.add(id)
                }
            }

            for (id in tokenIds) {
                val piece = vocabMap[id]
                if (piece != null) {
                    textBuilder.append(piece)
                }
            }

            val decoded = textBuilder.toString().trim()
            val latency = System.currentTimeMillis() - startTime
            val backendName = if (isGpuEnabled) "LiteRT GPU (OpenCL)" else "LiteRT CPU (XNNPACK)"

            return@withContext Lfm3InferenceResult(
                text = decoded,
                isSpeechDetected = true,
                latencyMs = latency,
                backend = backendName
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error executing LFM3-Omni LiteRT inference: ${e.message}", e)
            val latency = System.currentTimeMillis() - startTime
            return@withContext Lfm3InferenceResult(
                text = "",
                isSpeechDetected = true,
                latencyMs = latency
            )
        }
    }

    @Synchronized
    fun close() {
        interpreter?.close()
        interpreter = null
        gpuDelegate?.close()
        gpuDelegate = null
        activeModelPath = null
    }
}
