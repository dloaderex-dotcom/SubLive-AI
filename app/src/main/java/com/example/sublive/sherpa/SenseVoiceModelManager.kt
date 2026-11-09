package com.example.sublive.sherpa

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

sealed class SenseVoiceDownloadState {
    data object NotDownloaded : SenseVoiceDownloadState()
    data class Downloading(val progress: Float, val bytesDownloaded: Long, val totalBytes: Long) : SenseVoiceDownloadState()
    data object Downloaded : SenseVoiceDownloadState()
    data object Ready : SenseVoiceDownloadState()
    data class Error(val message: String) : SenseVoiceDownloadState()
}

class SenseVoiceModelManager(private val context: Context) {
    companion object {
        private const val TAG = "SenseVoiceModelManager"
        const val MODEL_DIR_NAME = "sherpa_sense_voice_int8"
        const val TOKENS_FILE_NAME = "tokens.txt"
        const val MODEL_FILE_NAME = "model.int8.onnx"

        val TOKEN_URLS = listOf(
            "https://hf-mirror.com/csukuangfj/sherpa-onnx-sense-voice-zh-en-ja-ko-yue-2024-07-17/resolve/main/tokens.txt",
            "https://huggingface.co/csukuangfj/sherpa-onnx-sense-voice-zh-en-ja-ko-yue-2024-07-17/raw/main/tokens.txt",
            "https://huggingface.co/csukuangfj/sherpa-onnx-sense-voice-zh-en-ja-ko-yue-2024-07-17/resolve/main/tokens.txt"
        )

        val MODEL_URLS = listOf(
            "https://hf-mirror.com/csukuangfj/sherpa-onnx-sense-voice-zh-en-ja-ko-yue-2024-07-17/resolve/main/model.int8.onnx",
            "https://huggingface.co/csukuangfj/sherpa-onnx-sense-voice-zh-en-ja-ko-yue-2024-07-17/resolve/main/model.int8.onnx"
        )

        const val ESTIMATED_TOTAL_BYTES = 240_000_000L
        private const val USER_AGENT = "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36"
    }

    private val baseDir = File(context.filesDir, "sherpa_models").apply { mkdirs() }
    val modelDir = File(baseDir, MODEL_DIR_NAME).apply { mkdirs() }
    val tokensFile = File(modelDir, TOKENS_FILE_NAME)
    val modelFile = File(modelDir, MODEL_FILE_NAME)

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(300, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private val _downloadState = MutableStateFlow<SenseVoiceDownloadState>(SenseVoiceDownloadState.NotDownloaded)
    val downloadState: StateFlow<SenseVoiceDownloadState> = _downloadState.asStateFlow()

    private var downloadJob: Job? = null

    init {
        refreshState()
    }

    fun isModelReady(): Boolean {
        return tokensFile.exists() && tokensFile.length() > 500L &&
                modelFile.exists() && modelFile.length() > 30_000_000L
    }

    fun isModelDownloaded(): Boolean = isModelReady()

    fun refreshState() {
        if (isModelReady()) {
            _downloadState.value = SenseVoiceDownloadState.Ready
        } else {
            _downloadState.value = SenseVoiceDownloadState.NotDownloaded
        }
    }

    fun downloadModel(scope: CoroutineScope) {
        if (downloadJob?.isActive == true) return

        downloadJob = scope.launch(Dispatchers.IO) {
            val tempTokens = File(modelDir, "tokens.txt.tmp")
            val tempModel = File(modelDir, "model.int8.onnx.tmp")

            try {
                _downloadState.value = SenseVoiceDownloadState.Downloading(0f, 0L, ESTIMATED_TOTAL_BYTES)
                Log.i(TAG, "Starting SenseVoice download with multi-mirror support")

                // Step 1: Download tokens.txt
                val tokensSuccess = downloadWithMirrorFallback(TOKEN_URLS, tempTokens, 0L, ESTIMATED_TOTAL_BYTES) { bytes, total ->
                    _downloadState.value = SenseVoiceDownloadState.Downloading(
                        progress = (bytes.toFloat() / total.toFloat()).coerceIn(0f, 0.05f),
                        bytesDownloaded = bytes,
                        totalBytes = total
                    )
                }

                if (!tokensSuccess || !tempTokens.exists() || tempTokens.length() < 100L) {
                    throw IllegalStateException("Failed to download tokens.txt from all mirrors")
                }

                // Step 2: Download model.int8.onnx
                var lastProgressTime = 0L
                val tokensSize = tempTokens.length()

                val modelSuccess = downloadWithMirrorFallback(MODEL_URLS, tempModel, tokensSize, ESTIMATED_TOTAL_BYTES) { bytes, total ->
                    val now = System.currentTimeMillis()
                    if (now - lastProgressTime > 150) {
                        lastProgressTime = now
                        val ratio = (bytes.toFloat() / total.toFloat()).coerceIn(0f, 0.99f)
                        _downloadState.value = SenseVoiceDownloadState.Downloading(ratio, bytes, total)
                    }
                }

                if (!modelSuccess || !tempModel.exists() || tempModel.length() < 30_000_000L) {
                    throw IllegalStateException("Failed to download model.int8.onnx from all mirrors")
                }

                // Move temp files to destination
                if (tokensFile.exists()) tokensFile.delete()
                if (modelFile.exists()) modelFile.delete()
                tempTokens.renameTo(tokensFile)
                tempModel.renameTo(modelFile)

                Log.i(TAG, "SenseVoice model files successfully verified: ${modelFile.length()} bytes")
                _downloadState.value = SenseVoiceDownloadState.Ready
            } catch (e: Exception) {
                if (isActive) {
                    Log.e(TAG, "SenseVoice download failed: ${e.message}", e)
                    tempTokens.delete()
                    tempModel.delete()
                    _downloadState.value = SenseVoiceDownloadState.Error("Download failed: ${e.message}")
                }
            } finally {
                downloadJob = null
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
                Log.d(TAG, "Trying SenseVoice mirror: $url")
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

        // Explicitly handle up to 5 HTTP redirects (301, 302, 307, 308) across domains
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
        File(modelDir, "tokens.txt.tmp").delete()
        File(modelDir, "model.int8.onnx.tmp").delete()
        refreshState()
    }

    fun deleteModel() {
        downloadJob?.cancel()
        tokensFile.delete()
        modelFile.delete()
        refreshState()
    }
}
