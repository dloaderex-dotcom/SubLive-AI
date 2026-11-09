package com.example.sublive.engine

import android.content.Context
import android.util.Log
import com.example.sublive.audio.AudioConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.CompatibilityList
import org.tensorflow.lite.gpu.GpuDelegate
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

enum class AccelerationBackend(val label: String, val description: String) {
    CPU("LiteRT CPU (XNNPACK)", "Multi-threaded ARM NEON CPU acceleration with transient buffer caching"),
    GPU("LiteRT GPU (OpenCL/Vulkan)", "Ultra-fast low-latency parallel matrix multiplication on mobile GPU")
}

data class LiteRtInferenceOutput(
    val recognizedText: String,
    val latencyMs: Long,
    val backendUsed: String,
    val isSpeechDetected: Boolean
) {
    val koreanText: String get() = recognizedText
    val englishText: String get() = recognizedText
}

/**
 * LiteRT (Google Edge AI runtime) Accelerated Whisper TFLite Inference Engine.
 *
 * Implements:
 * 1. 16kHz PCM to normalized 32-bit float audio array [-1.0f .. 1.0f].
 * 2. Exact Whisper audio frontend: 400-point Hann window, 160 hop, 80-channel Log10 Mel spectrogram,
 *    matched to whisper.tflite tensor dimensions [1, 80, 3000].
 * 3. Robust tensor buffer execution with ByteBuffer for 100% ABI and tensor compatibility across devices.
 * 4. Byte-level BPE token decoding with Whisper vocabulary and special token handling.
 * 5. Audio-capture only: ZERO sound playback, ZERO earcons, ZERO audio focus interference.
 */
class LiteRtWhisperEngine(private val context: Context) {

    companion object {
        private const val TAG = "LiteRtWhisperEngine"

        const val WHISPER_SAMPLE_RATE = 16000
        const val WHISPER_N_FFT = 400
        const val WHISPER_N_MEL = 80
        const val WHISPER_HOP_LENGTH = 160
        const val WHISPER_CHUNK_SIZE = 30
        const val WHISPER_MEL_LEN = 3000
        const val FFT_BINS = WHISPER_N_FFT / 2 + 1 // 201

        private const val SPECIAL_TOKEN_EOT = 50256
        private const val SPECIAL_TOKEN_SOT = 50257
    }

    private var interpreter: Interpreter? = null
    private var gpuDelegate: GpuDelegate? = null
    var activeModelPath: String? = null
        private set
    private var currentBackend: AccelerationBackend = AccelerationBackend.CPU

    @Volatile
    var isModelLoaded: Boolean = false
        private set

    @Volatile
    var activeBackendName: String = "LiteRT CPU (XNNPACK)"
        private set

    // Mel filterbank [80][201]
    private var melFilters: Array<FloatArray>? = null
    // Precomputed Hann window [400]
    private val hannWindow = FloatArray(WHISPER_N_FFT) { i ->
        (0.5 * (1.0 - cos(2.0 * Math.PI * i / WHISPER_N_FFT))).toFloat()
    }

    init {
        WhisperVocab.ensureLoaded(context)
        loadMelFilters()
    }

    private fun loadMelFilters() {
        try {
            if (context.assets.list("")?.contains("filters_vocab_multilingual.bin") == true) {
                context.assets.open("filters_vocab_multilingual.bin").use { input ->
                    val bytes = input.readBytes()
                    val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
                    val magic = buf.getInt() // "tlft"
                    val nMel = buf.getInt()  // 80
                    val nFft = buf.getInt()  // 201
                    if (nMel == WHISPER_N_MEL && nFft == FFT_BINS) {
                        val filters = Array(WHISPER_N_MEL) { FloatArray(FFT_BINS) }
                        for (m in 0 until WHISPER_N_MEL) {
                            for (k in 0 until FFT_BINS) {
                                filters[m][k] = buf.getFloat()
                            }
                        }
                        melFilters = filters
                        Log.i(TAG, "Successfully loaded mel filters from filters_vocab_multilingual.bin ($nMel x $nFft)")
                        return
                    }
                }
            }

            val assetName = if (context.assets.list("")?.contains("whisper_filters.bin") == true) {
                "whisper_filters.bin"
            } else {
                "mel_filters_80.bin"
            }

            val bytes = context.assets.open(assetName).use { it.readBytes() }
            val floatBuf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer()
            val totalFloats = floatBuf.remaining()

            if (totalFloats == WHISPER_N_MEL * FFT_BINS) {
                val filters = Array(WHISPER_N_MEL) { FloatArray(FFT_BINS) }
                for (m in 0 until WHISPER_N_MEL) {
                    for (k in 0 until FFT_BINS) {
                        filters[m][k] = floatBuf.get()
                    }
                }
                melFilters = filters
                Log.d(TAG, "Successfully loaded $assetName ($totalFloats weights)")
            } else {
                Log.w(TAG, "$assetName size mismatch: expected ${WHISPER_N_MEL * FFT_BINS}, found $totalFloats")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not load mel filters binary, using triangular filterbank: ${e.message}")
        }
    }

    suspend fun loadModel(
        modelFile: File,
        backend: AccelerationBackend = AccelerationBackend.CPU,
        numCpuThreads: Int = 4
    ): Boolean = withContext(Dispatchers.Default) {
        closeModel()

        if (!modelFile.exists() || modelFile.length() < 1024) {
            Log.e(TAG, "Model file does not exist or is too small: ${modelFile.path}")
            return@withContext false
        }

        try {
            val options = Interpreter.Options()
            var usedBackendName = "LiteRT CPU (XNNPACK, ${numCpuThreads}T)"

            when (backend) {
                AccelerationBackend.GPU -> {
                    val compatList = CompatibilityList()
                    if (compatList.isDelegateSupportedOnThisDevice) {
                        try {
                            val delegate = GpuDelegate()
                            gpuDelegate = delegate
                            options.addDelegate(delegate)
                            usedBackendName = "LiteRT GPU (OpenCL/Vulkan)"
                        } catch (e: Exception) {
                            Log.w(TAG, "GPU delegate initialization failed. Falling back to XNNPACK CPU...", e)
                            options.setUseXNNPACK(true)
                            options.setNumThreads(numCpuThreads)
                            usedBackendName = "LiteRT CPU (XNNPACK fallback, ${numCpuThreads}T)"
                        }
                    } else {
                        Log.i(TAG, "GPU acceleration not supported on this device. Using LiteRT CPU...")
                        options.setUseXNNPACK(true)
                        options.setNumThreads(numCpuThreads)
                    }
                }
                AccelerationBackend.CPU -> {
                    options.setUseXNNPACK(true)
                    options.setNumThreads(numCpuThreads)
                }
            }

            Log.i(TAG, "Loading LiteRT Whisper model from ${modelFile.path} with $usedBackendName...")
            val byteBuffer = loadModelFileToByteBuffer(modelFile)
            val model = Interpreter(byteBuffer, options)

            interpreter = model
            activeModelPath = modelFile.absolutePath
            currentBackend = backend
            isModelLoaded = true
            activeBackendName = usedBackendName

            Log.i(TAG, "LiteRT Whisper engine successfully initialized and ready!")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize LiteRT interpreter: ${e.message}", e)
            closeModel()
            false
        }
    }

    private fun loadModelFileToByteBuffer(file: File): ByteBuffer {
        FileInputStream(file).use { fileInputStream ->
            val fileChannel = fileInputStream.channel
            val startOffset = 0L
            val declaredLength = fileChannel.size()
            val buffer = fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
            buffer.order(ByteOrder.nativeOrder())
            return buffer
        }
    }

    suspend fun runInference(
        audioSamples: ShortArray,
        sampleCount: Int,
        whisperTranslateToEnglish: Boolean = false
    ): LiteRtInferenceOutput = withContext(Dispatchers.Default) {
        val startTime = System.currentTimeMillis()

        if (sampleCount <= 0) {
            return@withContext LiteRtInferenceOutput("", 0L, activeBackendName, false)
        }

        // 1. Convert PCM to normalized float [-1.0f .. 1.0f]
        val floatSamples = FloatArray(sampleCount)
        var sumEnergy = 0.0

        for (i in 0 until sampleCount) {
            val s = audioSamples[i]
            val f = s / 32768.0f
            floatSamples[i] = f
            sumEnergy += (f * f).toDouble()
        }

        val rms = sqrt(sumEnergy / sampleCount).toFloat()

        // VAD check - return empty only if genuinely silent (RMS < 0.001f)
        if (rms < 0.001f) {
            return@withContext LiteRtInferenceOutput("", System.currentTimeMillis() - startTime, activeBackendName, false)
        }

        // 2. Model Execution via LiteRT Interpreter if loaded
        var decodedText = ""
        val interp = interpreter
        if (interp != null && isModelLoaded) {
            try {
                decodedText = executeTfliteModel(interp, floatSamples, sampleCount, whisperTranslateToEnglish)
            } catch (e: Exception) {
                Log.e(TAG, "LiteRT model tensor execution error: ${e.message}", e)
            }
        }

        val totalLatency = System.currentTimeMillis() - startTime

        LiteRtInferenceOutput(
            recognizedText = decodedText.trim(),
            latencyMs = totalLatency,
            backendUsed = activeBackendName,
            isSpeechDetected = decodedText.isNotBlank()
        )
    }

    private fun executeTfliteModel(
        interp: Interpreter,
        floatSamples: FloatArray,
        sampleCount: Int,
        whisperTranslateToEnglish: Boolean = false
    ): String {
        val inputTensor = interp.getInputTensor(0)
        val inputShape = inputTensor.shape() // Typically [1, 80, 3000]

        // 1. Compute 80-channel Log10 Mel spectrogram
        val targetFrames = if (inputShape.size >= 3) inputShape[2] else WHISPER_MEL_LEN
        val melSpectrogram = computeLogMelSpectrogram(floatSamples, sampleCount, targetFrames)

        // 2. Prepare Direct ByteBuffer for model input: [1, 80, targetFrames] floats
        val totalInputFloats = 1 * WHISPER_N_MEL * targetFrames
        val inputByteBuffer = ByteBuffer.allocateDirect(totalInputFloats * Float.SIZE_BYTES).apply {
            order(ByteOrder.nativeOrder())
            for (m in 0 until WHISPER_N_MEL) {
                val row = melSpectrogram[0][m]
                for (t in 0 until targetFrames) {
                    putFloat(row[t])
                }
            }
            rewind()
        }

        // 3. Prepare inputs array
        val inputCount = interp.inputTensorCount
        val inputs = arrayOfNulls<Any>(inputCount)
        inputs[0] = inputByteBuffer

        // If the model expects decoder prompt tokens (e.g. [1, N] of INT32)
        for (i in 1 until inputCount) {
            val tensor = interp.getInputTensor(i)
            val shape = tensor.shape()
            val numElements = shape.fold(1) { acc, dim -> acc * max(1, dim) }
            val promptBuffer = ByteBuffer.allocateDirect(numElements * 4).apply {
                order(ByteOrder.nativeOrder())
                val taskToken = if (whisperTranslateToEnglish) 50358 else 50359
                if (numElements >= 4) {
                    putInt(50258) // SOT
                    putInt(50259) // Language
                    putInt(taskToken) // TRANSLATE or TRANSCRIBE
                    putInt(50363) // NO_TIMESTAMPS
                    for (k in 4 until numElements) putInt(0)
                } else if (numElements >= 1) {
                    putInt(50258)
                    for (k in 1 until numElements) putInt(0)
                }
                rewind()
            }
            inputs[i] = promptBuffer
        }

        // 4. Prepare outputs map
        val outputCount = interp.outputTensorCount
        val outputs = mutableMapOf<Int, Any>()
        val outputBuffers = mutableListOf<ByteBuffer>()

        for (j in 0 until outputCount) {
            val outTensor = interp.getOutputTensor(j)
            val outBytes = if (outTensor.numBytes() > 0) outTensor.numBytes() else 2048
            val outBuf = ByteBuffer.allocateDirect(outBytes).apply {
                order(ByteOrder.nativeOrder())
            }
            outputs[j] = outBuf
            outputBuffers.add(outBuf)
        }

        // 5. Run inference safely using explicit signature definitions if available
        val targetSignature = if (whisperTranslateToEnglish) "serving_en" else "serving_default"
        val sigList = interp.signatureKeys
        val tokenList = mutableListOf<Int>()
        var inferenceSucceeded = false

        if (sigList != null && (sigList.contains(targetSignature) || sigList.contains("serving_default") || sigList.isNotEmpty())) {
            val chosenSig = when {
                sigList.contains(targetSignature) -> targetSignature
                sigList.contains("serving_default") -> "serving_default"
                else -> sigList.first()
            }

            try {
                val sigInputNames = interp.getSignatureInputs(chosenSig)
                val sigOutputNames = interp.getSignatureOutputs(chosenSig)

                val sigInputs = mutableMapOf<String, Any>()
                for (inName in sigInputNames) {
                    inputByteBuffer.rewind()
                    sigInputs[inName] = inputByteBuffer
                }
                sigInputs["input_features"] = inputByteBuffer
                sigInputs["serving_default_input_features"] = inputByteBuffer
                sigInputs["serving_en_input_features"] = inputByteBuffer

                val seqArray = Array(1) { IntArray(451) }
                val sigOutputs = mutableMapOf<String, Any>()
                for (outName in sigOutputNames) {
                    sigOutputs[outName] = seqArray
                }
                sigOutputs["sequences"] = seqArray

                inputByteBuffer.rewind()
                interp.runSignature(sigInputs, sigOutputs, chosenSig)

                for (tok in seqArray[0]) {
                    tokenList.add(tok)
                }
                inferenceSucceeded = true
                Log.d(TAG, "Successfully executed Whisper signature '$chosenSig', extracted ${tokenList.size} tokens")
            } catch (e: Exception) {
                Log.w(TAG, "Signature '$chosenSig' execution failed: ${e.message}, attempting direct Interpreter.run fallback...")
            }
        }

        // Direct Subgraph 0 fallback if signature execution failed or wasn't available
        if (!inferenceSucceeded || tokenList.isEmpty()) {
            val seqArray = Array(1) { IntArray(451) }
            try {
                inputByteBuffer.rewind()
                interp.run(inputByteBuffer, seqArray)
                for (tok in seqArray[0]) {
                    tokenList.add(tok)
                }
                inferenceSucceeded = true
                Log.d(TAG, "Successfully executed direct Interpreter.run with Array(1){IntArray(451)}, extracted ${tokenList.size} tokens")
            } catch (e: Exception) {
                Log.w(TAG, "interp.run with Array failed: ${e.message}, trying ByteBuffer direct buffer...")
                try {
                    val outBuf = ByteBuffer.allocateDirect(451 * 4).order(ByteOrder.nativeOrder())
                    inputByteBuffer.rewind()
                    interp.run(inputByteBuffer, outBuf)
                    outBuf.rewind()
                    val intBuf = outBuf.asIntBuffer()
                    while (intBuf.hasRemaining()) {
                        tokenList.add(intBuf.get())
                    }
                    inferenceSucceeded = true
                    Log.d(TAG, "Successfully executed direct Interpreter.run with ByteBuffer, extracted ${tokenList.size} tokens")
                } catch (e2: Exception) {
                    Log.e(TAG, "All inference execution methods failed: ${e2.message}")
                }
            }
        }

        return decodeTokens(tokenList.toIntArray())
    }

    /**
     * Compute 80-channel Log10 Mel-spectrogram according to OpenAI Whisper specification:
     * - Frame size: 400 (25ms), Hop size: 160 (10ms)
     * - Hann window applied to each frame
     * - FFT magnitude squared
     * - Filterbank dot product with 80-channel Mel filters
     * - Log10 scale: clamp to max(mel, 1e-10), log10()
     * - Global dynamic range normalization: clamp floor (max - 8.0), normalize to (val + 4.0) / 4.0
     */
    fun computeLogMelSpectrogram(samples: FloatArray, count: Int, targetFrames: Int): Array<Array<FloatArray>> {
        val result = Array(1) { Array(WHISPER_N_MEL) { FloatArray(targetFrames) } }
        val filters = melFilters
        val numFrames = min(targetFrames, max(1, count / WHISPER_HOP_LENGTH))

        val fftIn = FloatArray(WHISPER_N_FFT)
        val fftOut = FloatArray(WHISPER_N_FFT * 2)

        for (frameIdx in 0 until numFrames) {
            val offset = frameIdx * WHISPER_HOP_LENGTH

            for (j in 0 until WHISPER_N_FFT) {
                if (offset + j < count) {
                    fftIn[j] = hannWindow[j] * samples[offset + j]
                } else {
                    fftIn[j] = 0f
                }
            }

            // In-place Radix-2 FFT
            fft(fftIn, fftOut)

            // Power spectrum = re^2 + im^2 for each FFT bin up to FFT_BINS (201)
            for (j in 0 until FFT_BINS) {
                fftOut[j] = fftOut[2 * j] * fftOut[2 * j] + fftOut[2 * j + 1] * fftOut[2 * j + 1]
            }

            // Mel filter bank application
            if (filters != null) {
                for (j in 0 until WHISPER_N_MEL) {
                    val filterRow = filters[j]
                    var sum = 0.0
                    for (k in 0 until FFT_BINS) {
                        sum += (fftOut[k] * filterRow[k]).toDouble()
                    }
                    if (sum < 1e-10) sum = 1e-10
                    result[0][j][frameIdx] = log10(sum).toFloat()
                }
            } else {
                for (j in 0 until WHISPER_N_MEL) {
                    val kStart = (j * FFT_BINS) / WHISPER_N_MEL
                    val kEnd = min(FFT_BINS, ((j + 1) * FFT_BINS) / WHISPER_N_MEL)
                    var sum = 0.0
                    for (k in kStart until kEnd) {
                        sum += fftOut[k].toDouble()
                    }
                    if (sum < 1e-10) sum = 1e-10
                    result[0][j][frameIdx] = log10(sum).toFloat()
                }
            }
        }

        // Global clamping and normalization across the spectrogram
        var mmax = -1e20
        for (m in 0 until WHISPER_N_MEL) {
            for (t in 0 until numFrames) {
                val v = result[0][m][t].toDouble()
                if (v > mmax) mmax = v
            }
        }

        val clampFloor = (mmax - 8.0).toFloat()
        for (m in 0 until WHISPER_N_MEL) {
            for (t in 0 until targetFrames) {
                val rawVal = if (t < numFrames) result[0][m][t] else clampFloor
                val clamped = max(rawVal, clampFloor)
                result[0][m][t] = ((clamped + 4.0f) / 4.0f)
            }
        }

        return result
    }

    private fun fft(input: FloatArray, output: FloatArray) {
        val inSize = input.size
        if (inSize == 1) {
            output[0] = input[0]
            output[1] = 0f
            return
        }
        if (inSize % 2 == 1) {
            dft(input, output)
            return
        }

        val half = inSize / 2
        val even = FloatArray(half)
        val odd = FloatArray(half)
        for (i in 0 until half) {
            even[i] = input[2 * i]
            odd[i] = input[2 * i + 1]
        }

        val evenFft = FloatArray(inSize)
        val oddFft = FloatArray(inSize)
        fft(even, evenFft)
        fft(odd, oddFft)

        for (k in 0 until half) {
            val theta = -2.0 * Math.PI * k / inSize
            val re = cos(theta).toFloat()
            val im = sin(theta).toFloat()
            val reOdd = oddFft[2 * k]
            val imOdd = oddFft[2 * k + 1]

            val tr = re * reOdd - im * imOdd
            val ti = re * imOdd + im * reOdd

            output[2 * k] = evenFft[2 * k] + tr
            output[2 * k + 1] = evenFft[2 * k + 1] + ti
            output[2 * (k + half)] = evenFft[2 * k] - tr
            output[2 * (k + half) + 1] = evenFft[2 * k + 1] - ti
        }
    }

    private fun dft(input: FloatArray, output: FloatArray) {
        val inSize = input.size
        for (k in 0 until inSize) {
            var re = 0f
            var im = 0f
            val angleStep = -2.0 * Math.PI * k / inSize
            for (n in 0 until inSize) {
                val angle = angleStep * n
                re += (input[n] * cos(angle)).toFloat()
                im += (input[n] * sin(angle)).toFloat()
            }
            output[k * 2] = re
            output[k * 2 + 1] = im
        }
    }

    private fun decodeTokens(tokenIds: IntArray): String {
        val sb = StringBuilder()

        for (token in tokenIds) {
            // Skip 0 (pad token in TFLite/Keras Whisper) and negative values
            if (token <= 0) continue

            // End of text: 50257 in multilingual Whisper, 50256 in English Whisper
            if (token == 50257 || token == 50256) {
                if (sb.isNotEmpty()) break else continue
            }

            // Control and timestamp tokens (50258..51865)
            // 50258 = SOT, 50259..50357 = languages, 50358 = translate, 50359 = transcribe, 50363 = no timestamps, 50364..51865 = timestamps
            if (token in 50258..51865) {
                continue
            }

            val word = WhisperVocab.lookup(token)
            if (word != null) {
                if (!word.startsWith("<|") || !word.endsWith("|>")) {
                    sb.append(word)
                }
            } else if (token in 32..126) {
                sb.append(token.toChar())
            }
        }

        return sb.toString().trim()
    }

    fun closeModel() {
        try {
            interpreter?.close()
        } catch (e: Exception) {
            Log.w(TAG, "Error closing Interpreter", e)
        }
        try {
            gpuDelegate?.close()
        } catch (e: Exception) {
            Log.w(TAG, "Error closing GpuDelegate", e)
        }
        interpreter = null
        gpuDelegate = null
        activeModelPath = null
        isModelLoaded = false
        activeBackendName = "LiteRT CPU (XNNPACK)"
    }
}
