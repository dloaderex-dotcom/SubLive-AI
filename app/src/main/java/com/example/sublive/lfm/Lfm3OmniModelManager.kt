package com.example.sublive.lfm

import android.content.Context
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

sealed class Lfm3DownloadState {
    data object NotDownloaded : Lfm3DownloadState()
    data class Downloading(val progress: Float, val bytesDownloaded: Long, val totalBytes: Long) : Lfm3DownloadState()
    data object Downloaded : Lfm3DownloadState()
    data object Ready : Lfm3DownloadState()
    data class Error(val message: String) : Lfm3DownloadState()
}

/**
 * Model Manager for Liquid AI LFM3-Omni-2B (Google LiteRT / TFLite Standalone).
 * Manages downloading the 2B Multimodal edge weights and tokenizer from LiteRT community mirrors.
 */
class Lfm3OmniModelManager(private val context: Context) {
    companion object {
        private const val TAG = "Lfm3OmniModelManager"
        const val MODEL_DIR_NAME = "lfm3_omni_litert"
        const val MODEL_FILE_NAME = "LFM3-Omni-2B_frame_embed_f32.tflite"
        const val TOKENIZER_FILE_NAME = "tokenizer.json"

        val MODEL_URLS = listOf(
            "https://hf-mirror.com/litert-community/LFM3-Omni-2B-LiteRT/resolve/main/speech/LFM3-Omni-2B_frame_embed_f32.tflite",
            "https://huggingface.co/litert-community/LFM3-Omni-2B-LiteRT/resolve/main/speech/LFM3-Omni-2B_frame_embed_f32.tflite"
        )

        val TOKENIZER_URLS = listOf(
            "https://hf-mirror.com/litert-community/LFM3-Omni-2B-LiteRT/resolve/main/tokenizer.json",
            "https://huggingface.co/litert-community/LFM3-Omni-2B-LiteRT/resolve/main/tokenizer.json"
        )

        const val ESTIMATED_MODEL_BYTES = 198_000_000L
        private const val USER_AGENT = "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36"
    }

    val baseDir = File(context.filesDir, "lfm_models").apply { mkdirs() }
    val modelDir = File(baseDir, MODEL_DIR_NAME).apply { mkdirs() }
    val modelFile = File(modelDir, MODEL_FILE_NAME)
    val tokenizerFile = File(modelDir, TOKENIZER_FILE_NAME)

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(300, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private val _downloadState = MutableStateFlow<Lfm3DownloadState>(Lfm3DownloadState.NotDownloaded)
    val downloadState: StateFlow<Lfm3DownloadState> = _downloadState.asStateFlow()

    private var downloadJob: Job? = null

    init {
        promoteAnyCompletedTmpFiles()
        refreshState()
    }

    /**
     * Inspects model directories for any previously completed download files or temporary files
     * and promotes them to the primary model path.
     */
    private fun promoteAnyCompletedTmpFiles() {
        try {
            // Check for temp files that were already downloaded (> 10MB)
            val candidateTmpFiles = listOf(
                File(modelDir, "LFM3-Omni-2B.tflite.tmp"),
                File(modelDir, "LFM3-Omni-2B_frame_embed_f32.tflite.tmp"),
                File(baseDir, "LFM3-Omni-2B.tflite.tmp"),
                File(baseDir, "LFM3-Omni-2B_frame_embed_f32.tflite.tmp")
            )

            for (tmp in candidateTmpFiles) {
                if (tmp.exists() && tmp.length() > 10_000_000L) {
                    if (!modelFile.exists() || modelFile.length() < 10_000_000L) {
                        Log.i(TAG, "Promoting downloaded temp model (${tmp.length()} bytes) to $MODEL_FILE_NAME")
                        safeMove(tmp, modelFile)
                        break
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error checking temporary model files: ${e.message}")
        }
    }

    /**
     * Locates a valid LFM model file on disk. Searches modelFile, modelDir, and baseDir.
     */
    fun getValidModelFile(): File? {
        // 1. Primary path
        if (modelFile.exists() && modelFile.length() > 5_000_000L) {
            return modelFile
        }

        // 2. Check for any promoted or renamed temp file
        promoteAnyCompletedTmpFiles()
        if (modelFile.exists() && modelFile.length() > 5_000_000L) {
            return modelFile
        }

        // 3. Search modelDir for any .tflite or .litertlm file
        val dirFiles = modelDir.listFiles() ?: emptyArray()
        for (f in dirFiles) {
            if ((f.name.endsWith(".tflite", ignoreCase = true) || f.name.endsWith(".litertlm", ignoreCase = true)) &&
                f.length() > 5_000_000L && !f.name.endsWith(".tmp")
            ) {
                return f
            }
        }

        // 4. Search baseDir
        val baseFiles = baseDir.listFiles() ?: emptyArray()
        for (f in baseFiles) {
            if ((f.name.endsWith(".tflite", ignoreCase = true) || f.name.endsWith(".litertlm", ignoreCase = true)) &&
                f.length() > 5_000_000L && !f.name.endsWith(".tmp")
            ) {
                return f
            }
        }

        return null
    }

    /**
     * Returns true if a valid model file (> 5MB) exists on device.
     * Note: Tokenizer is optional and will not block model readiness.
     */
    fun isModelReady(): Boolean {
        return getValidModelFile() != null
    }

    fun isModelDownloaded(): Boolean = isModelReady()

    fun refreshState() {
        promoteAnyCompletedTmpFiles()
        if (isModelReady()) {
            _downloadState.value = Lfm3DownloadState.Ready
        } else {
            _downloadState.value = Lfm3DownloadState.NotDownloaded
        }
    }

    fun downloadModel(scope: CoroutineScope) {
        if (downloadJob?.isActive == true) return

        downloadJob = scope.launch(Dispatchers.IO) {
            val tempModel = File(modelDir, "LFM3-Omni-2B_frame_embed_f32.tflite.tmp")
            val tempTokenizer = File(modelDir, "tokenizer.json.tmp")

            try {
                _downloadState.value = Lfm3DownloadState.Downloading(0f, 0L, ESTIMATED_MODEL_BYTES)
                Log.i(TAG, "Starting LFM3-Omni-2B LiteRT model download")

                // Step 1: Download LFM3-Omni TFLite Model (Primary neural net)
                var lastProgressTime = 0L
                val modelSuccess = downloadWithMirrorFallback(MODEL_URLS, tempModel, 0L, ESTIMATED_MODEL_BYTES) { bytes, total ->
                    val now = System.currentTimeMillis()
                    if (now - lastProgressTime > 150) {
                        lastProgressTime = now
                        val ratio = (bytes.toFloat() / total.toFloat()).coerceIn(0f, 0.95f)
                        _downloadState.value = Lfm3DownloadState.Downloading(ratio, bytes, total)
                    }
                }

                if (!modelSuccess || !tempModel.exists() || tempModel.length() < 10_000_000L) {
                    throw IllegalStateException("Failed to download LFM3-Omni LiteRT model from mirrors")
                }

                // Promote model file immediately so it's ready even if tokenizer fails
                safeMove(tempModel, modelFile)
                Log.i(TAG, "LFM3-Omni model saved and ready: ${modelFile.length()} bytes")

                // Step 2: Download tokenizer.json (Optional, non-blocking)
                try {
                    _downloadState.value = Lfm3DownloadState.Downloading(0.96f, modelFile.length(), ESTIMATED_MODEL_BYTES)
                    val tokenizerSuccess = downloadWithMirrorFallback(TOKENIZER_URLS, tempTokenizer, 0L, 18_000_000L) { _, _ -> }
                    if (tokenizerSuccess && tempTokenizer.exists() && tempTokenizer.length() > 500L) {
                        val textStart = tempTokenizer.bufferedReader().use { it.readText().take(40) }
                        if (!textStart.startsWith("version https://git-lfs")) {
                            safeMove(tempTokenizer, tokenizerFile)
                            Log.i(TAG, "LFM3 tokenizer saved: ${tokenizerFile.length()} bytes")
                        } else {
                            tempTokenizer.delete()
                            Log.w(TAG, "Tokenizer was an LFS pointer, using built-in decoder")
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Optional tokenizer download skipped: ${e.message}")
                    tempTokenizer.delete()
                }

                _downloadState.value = Lfm3DownloadState.Ready
            } catch (e: Exception) {
                if (isActive) {
                    Log.e(TAG, "LFM3-Omni download failed: ${e.message}", e)
                    // If model file was already promoted, mark ready anyway
                    if (isModelReady()) {
                        _downloadState.value = Lfm3DownloadState.Ready
                    } else {
                        tempTokenizer.delete()
                        tempModel.delete()
                        _downloadState.value = Lfm3DownloadState.Error("Download failed: ${e.message}")
                    }
                }
            } finally {
                downloadJob = null
            }
        }
    }

    private fun safeMove(source: File, target: File) {
        if (!source.exists()) return
        if (target.exists()) target.delete()
        val renamed = source.renameTo(target)
        if (!renamed) {
            try {
                source.inputStream().use { input ->
                    target.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                source.delete()
            } catch (e: Exception) {
                Log.e(TAG, "Failed safeMove from ${source.name} to ${target.name}: ${e.message}")
            }
        }
    }

    private fun downloadWithMirrorFallback(
        urls: List<String>,
        targetFile: File,
        baseOffset: Long,
        grandTotal: Long,
        onProgress: (bytes: Long, total: Long) -> Unit
    ): Boolean {
        for (url in urls) {
            try {
                Log.d(TAG, "Trying LFM3 mirror: $url")
                if (tryDownloadFile(url, targetFile, baseOffset, grandTotal, onProgress)) {
                    return true
                }
            } catch (e: Exception) {
                Log.w(TAG, "Mirror failed ($url): ${e.message}")
            }
        }
        return false
    }

    private fun tryDownloadFile(
        initialUrl: String,
        targetFile: File,
        baseOffset: Long,
        grandTotal: Long,
        onProgress: (bytes: Long, total: Long) -> Unit
    ): Boolean {
        var currentUrl = initialUrl
        var finalResponse: Response? = null

        for (i in 0..5) {
            val request = Request.Builder()
                .url(currentUrl)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "*/*")
                .build()

            val response = httpClient.newCall(request).execute()
            if (response.isRedirect || response.code in 300..399) {
                val nextUrl = response.header("Location")
                response.close()
                if (nextUrl != null) {
                    currentUrl = if (nextUrl.startsWith("http")) nextUrl else java.net.URL(java.net.URL(currentUrl), nextUrl).toString()
                    continue
                }
            }
            if (response.isSuccessful) {
                finalResponse = response
                break
            } else {
                Log.w(TAG, "HTTP error ${response.code} for URL: $currentUrl")
                response.close()
                return false
            }
        }

        val resp = finalResponse ?: return false
        val body = resp.body ?: run {
            resp.close()
            return false
        }

        var currentBytes = baseOffset
        resp.use {
            body.byteStream().use { input ->
                FileOutputStream(targetFile).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        currentBytes += read
                        onProgress(currentBytes, grandTotal)
                    }
                }
            }
        }

        return targetFile.exists() && targetFile.length() > 0
    }

    fun cancelDownload() {
        downloadJob?.cancel()
        downloadJob = null
        File(modelDir, "tokenizer.json.tmp").delete()
        File(modelDir, "LFM3-Omni-2B_frame_embed_f32.tflite.tmp").delete()
        refreshState()
    }

    fun deleteModel() {
        downloadJob?.cancel()
        modelFile.delete()
        tokenizerFile.delete()
        val dirFiles = modelDir.listFiles() ?: emptyArray()
        for (f in dirFiles) {
            f.delete()
        }
        refreshState()
    }
}
