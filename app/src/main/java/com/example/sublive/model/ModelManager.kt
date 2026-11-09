package com.example.sublive.model

import android.content.Context
import android.os.StatFs
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

enum class ModelTier(
    val id: String,
    val title: String,
    val description: String,
    val fileName: String,
    val downloadUrl: String,
    val fallbackUrl: String? = null,
    val estimatedBytes: Long,
    val sizeLabel: String,
    val latencyLabel: String,
    val ramLabel: String,
    val recommendation: String
) {
    TFLITE_TINY(
        id = "whisper_tiny_tflite",
        title = "Whisper Tiny (TFLite INT8)",
        description = "Lightweight, ultra-low latency TFLite Whisper model optimized for mobile ARM NEON and LiteRT GPU acceleration.",
        fileName = "whisper-tiny.tflite",
        downloadUrl = "https://huggingface.co/cik009/whisper/resolve/main/whisper-tiny.tflite",
        fallbackUrl = "https://huggingface.co/nyadla-sys/whisper-tiny.en.tflite/resolve/main/whisper-tiny.en.tflite",
        estimatedBytes = 42_458_960L,
        sizeLabel = "42 MB",
        latencyLabel = "~80-140 ms",
        ramLabel = "~110 MB",
        recommendation = "Fast & Low Battery"
    ),
    TFLITE_BASE(
        id = "whisper_base_tflite",
        title = "Whisper Base (TFLite INT8)",
        description = "Balanced Whisper TFLite model providing high speech recognition accuracy across multilingual audio streams.",
        fileName = "whisper-base.tflite",
        downloadUrl = "https://huggingface.co/cik009/whisper/resolve/main/whisper-base.tflite",
        fallbackUrl = "https://huggingface.co/cik009/whisper/resolve/main/whisper-base.en.tflite",
        estimatedBytes = 78_729_264L,
        sizeLabel = "79 MB",
        latencyLabel = "~200-300 ms",
        ramLabel = "~220 MB",
        recommendation = "Best Accuracy Balance"
    ),
    TFLITE_SMALL(
        id = "whisper_small_tflite",
        title = "Whisper Small (TFLite INT8)",
        description = "High fidelity Whisper TFLite model preserving complex speech vocabulary, accents, and noisy environments.",
        fileName = "whisper-small.tflite",
        downloadUrl = "https://huggingface.co/cik009/whisper/resolve/main/whisper-small.tflite",
        fallbackUrl = "https://huggingface.co/cik009/whisper/resolve/main/whisper-small.en.tflite",
        estimatedBytes = 248_064_048L,
        sizeLabel = "248 MB",
        latencyLabel = "~600-800 ms",
        ramLabel = "~480 MB",
        recommendation = "Highest Transcription Fidelity"
    )
}

sealed class ModelDownloadState {
    data object NotDownloaded : ModelDownloadState()
    data class Downloading(val progress: Float, val bytesDownloaded: Long, val totalBytes: Long) : ModelDownloadState()
    data object Downloaded : ModelDownloadState()
    data object Loaded : ModelDownloadState()
    data class Error(val message: String) : ModelDownloadState()
}

data class ModelStatus(
    val tier: ModelTier,
    val state: ModelDownloadState,
    val localFile: File? = null,
    val isSelected: Boolean = false
)

class ModelManager(private val context: Context) {
    companion object {
        private const val TAG = "ModelManager"
    }

    private val modelsDir = File(context.filesDir, "whisper_models").apply { mkdirs() }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(300, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .retryOnConnectionFailure(true)
        .build()

    private val _modelStates = MutableStateFlow<Map<ModelTier, ModelStatus>>(emptyMap())
    val modelStates: StateFlow<Map<ModelTier, ModelStatus>> = _modelStates.asStateFlow()

    private val _activeModel = MutableStateFlow<ModelTier>(ModelTier.TFLITE_TINY)
    val activeModel: StateFlow<ModelTier> = _activeModel.asStateFlow()

    private var activeDownloadJob: Job? = null
    private var currentlyDownloadingTier: ModelTier? = null

    init {
        refreshStatus()
    }

    fun hasDownloadedModel(): Boolean {
        return ModelTier.entries.any { tier ->
            val file = File(modelsDir, tier.fileName)
            file.exists() && file.length() > 1024 * 1024
        }
    }

    fun getFirstDownloadedTier(): ModelTier? {
        return ModelTier.entries.firstOrNull { tier ->
            val file = File(modelsDir, tier.fileName)
            file.exists() && file.length() > 1024 * 1024
        }
    }

    fun refreshStatus() {
        val downloadedTier = getFirstDownloadedTier()
        if (downloadedTier != null && getModelFile(_activeModel.value) == null) {
            _activeModel.value = downloadedTier
        }

        val map = mutableMapOf<ModelTier, ModelStatus>()
        for (tier in ModelTier.entries) {
            val file = File(modelsDir, tier.fileName)
            val isDownloaded = file.exists() && file.length() > 1024 * 1024 // at least 1MB
            val isLoaded = isDownloaded && tier == _activeModel.value

            val state = when {
                isLoaded -> ModelDownloadState.Loaded
                isDownloaded -> ModelDownloadState.Downloaded
                currentlyDownloadingTier == tier -> _modelStates.value[tier]?.state ?: ModelDownloadState.NotDownloaded
                else -> ModelDownloadState.NotDownloaded
            }

            map[tier] = ModelStatus(
                tier = tier,
                state = state,
                localFile = if (isDownloaded) file else null,
                isSelected = tier == _activeModel.value
            )
        }
        _modelStates.value = map
    }

    /**
     * Download a Whisper TFLite model file with streaming progress updates and space validation.
     */
    fun downloadModel(tier: ModelTier, scope: CoroutineScope) {
        if (currentlyDownloadingTier != null) {
            Log.w(TAG, "Another download is already in progress: $currentlyDownloadingTier")
            return
        }

        // Check available disk space safely
        try {
            val stat = StatFs(modelsDir.path)
            val availableBytes = stat.availableBlocksLong * stat.blockSizeLong
            if (availableBytes in 1 until tier.estimatedBytes) {
                updateState(
                    tier,
                    ModelDownloadState.Error(
                        "Insufficient storage. Available: ${availableBytes / (1024 * 1024)} MB, Required: ${tier.sizeLabel}"
                    )
                )
                return
            }
        } catch (e: Exception) {
            Log.w(TAG, "StatFs check skipped: ${e.message}")
        }

        currentlyDownloadingTier = tier
        val destinationFile = File(modelsDir, tier.fileName)
        val tempFile = File(modelsDir, "${tier.fileName}.tmp")

        activeDownloadJob = scope.launch(Dispatchers.IO) {
            updateState(tier, ModelDownloadState.Downloading(0f, 0, tier.estimatedBytes))

            // Attempt primary URL, then fallback if primary fails
            val urlsToTry = mutableListOf(tier.downloadUrl)
            tier.fallbackUrl?.let { urlsToTry.add(it) }

            var downloadSuccess = false
            var lastErrorMessage = "Unknown download error"

            for (targetUrl in urlsToTry) {
                if (!isActive) break

                try {
                    Log.i(TAG, "Attempting download from: $targetUrl")
                    val response = executeWithManualRedirect(targetUrl)

                    if (!response.isSuccessful) {
                        lastErrorMessage = "Server returned HTTP ${response.code}"
                        Log.w(TAG, "HTTP ${response.code} for $targetUrl")
                        continue
                    }

                    val body = response.body ?: throw IllegalStateException("Empty response body")
                    val totalLength = if (body.contentLength() > 0) body.contentLength() else tier.estimatedBytes

                    body.byteStream().use { input ->
                        FileOutputStream(tempFile).use { output ->
                            val buffer = ByteArray(32 * 1024)
                            var bytesRead: Int
                            var downloadedSoFar = 0L
                            var lastReportTime = 0L

                            while (input.read(buffer).also { bytesRead = it } != -1) {
                                if (!isActive) {
                                    tempFile.delete()
                                    return@launch
                                }
                                output.write(buffer, 0, bytesRead)
                                downloadedSoFar += bytesRead

                                val now = System.currentTimeMillis()
                                if (now - lastReportTime > 250) {
                                    lastReportTime = now
                                    val progress = (downloadedSoFar.toFloat() / totalLength.toFloat()).coerceIn(0f, 1f)
                                    updateState(tier, ModelDownloadState.Downloading(progress, downloadedSoFar, totalLength))
                                }
                            }
                        }
                    }

                    if (tempFile.length() < 1024 * 1024) {
                        throw IllegalStateException("Downloaded file is too small (${tempFile.length()} bytes)")
                    }

                    if (destinationFile.exists()) destinationFile.delete()
                    if (!tempFile.renameTo(destinationFile)) {
                        throw IllegalStateException("Failed to move downloaded model to destination file")
                    }

                    downloadSuccess = true
                    break
                } catch (e: Exception) {
                    Log.w(TAG, "Failed downloading from $targetUrl: ${e.message}", e)
                    lastErrorMessage = e.message ?: "Connection error"
                    tempFile.delete()
                }
            }

            currentlyDownloadingTier = null

            if (downloadSuccess) {
                Log.i(TAG, "Successfully downloaded model: ${tier.title}")
                refreshStatus()
            } else {
                updateState(tier, ModelDownloadState.Error("Download failed: $lastErrorMessage"))
            }
        }
    }

    /**
     * Executes GET request and follows redirects (HTTP 301, 302, 307, 308) up to 5 hops.
     */
    private fun executeWithManualRedirect(initialUrl: String): Response {
        var currentUrl = initialUrl
        var hops = 0

        while (hops < 5) {
            val request = Request.Builder()
                .url(currentUrl)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) SubLiveAI/1.0")
                .header("Accept", "*/*")
                .build()

            val response = httpClient.newCall(request).execute()

            if (response.isRedirect) {
                val location = response.header("Location")
                response.close()
                if (!location.isNullOrBlank()) {
                    currentUrl = location
                    hops++
                    continue
                }
            }

            return response
        }

        throw IllegalStateException("Too many redirects ($hops)")
    }

    fun cancelDownload() {
        activeDownloadJob?.cancel()
        activeDownloadJob = null
        currentlyDownloadingTier?.let { tier ->
            File(modelsDir, "${tier.fileName}.tmp").delete()
            updateState(tier, ModelDownloadState.NotDownloaded)
        }
        currentlyDownloadingTier = null
    }

    fun selectModel(tier: ModelTier) {
        _activeModel.value = tier
        refreshStatus()
    }

    fun deleteModel(tier: ModelTier) {
        val file = File(modelsDir, tier.fileName)
        if (file.exists()) {
            file.delete()
        }
        refreshStatus()
    }

    fun getModelFile(tier: ModelTier): File? {
        val file = File(modelsDir, tier.fileName)
        return if (file.exists()) file else null
    }

    private fun updateState(tier: ModelTier, state: ModelDownloadState) {
        val current = _modelStates.value.toMutableMap()
        current[tier] = ModelStatus(
            tier = tier,
            state = state,
            localFile = getModelFile(tier),
            isSelected = tier == _activeModel.value
        )
        _modelStates.value = current
    }
}
