package com.example.sublive.vad

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

sealed class SileroDownloadState {
    data object NotDownloaded : SileroDownloadState()
    data class Downloading(val progress: Float, val bytesDownloaded: Long, val totalBytes: Long) : SileroDownloadState()
    data object Downloaded : SileroDownloadState()
    data object Ready : SileroDownloadState()
    data class Error(val message: String) : SileroDownloadState()
}

/**
 * Manages downloading and persistence of the Silero VAD ONNX model (~643 KB).
 */
class SileroVadManager(private val context: Context) {
    companion object {
        private const val TAG = "SileroVadManager"
        const val VAD_DIR_NAME = "vad_models"
        const val VAD_FILE_NAME = "silero_vad.onnx"

        val VAD_URLS = listOf(
            "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/silero_vad.onnx",
            "https://hf-mirror.com/onnx-community/silero-vad/resolve/main/onnx/model_quantized.onnx",
            "https://huggingface.co/onnx-community/silero-vad/resolve/main/onnx/model_quantized.onnx"
        )

        const val ESTIMATED_BYTES = 643_854L
        private const val USER_AGENT = "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36"
    }

    val baseDir = File(context.filesDir, VAD_DIR_NAME).apply { mkdirs() }
    val vadFile = File(baseDir, VAD_FILE_NAME)

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private val _downloadState = MutableStateFlow<SileroDownloadState>(SileroDownloadState.NotDownloaded)
    val downloadState: StateFlow<SileroDownloadState> = _downloadState.asStateFlow()

    private var downloadJob: Job? = null

    init {
        refreshState()
    }

    fun isModelReady(): Boolean {
        return vadFile.exists() && vadFile.length() > 50_000L
    }

    fun isModelDownloaded(): Boolean = isModelReady()

    fun refreshState() {
        if (isModelReady()) {
            _downloadState.value = SileroDownloadState.Ready
        } else {
            _downloadState.value = SileroDownloadState.NotDownloaded
        }
    }

    fun downloadModel(scope: CoroutineScope) {
        if (downloadJob?.isActive == true) return

        downloadJob = scope.launch(Dispatchers.IO) {
            val tempFile = File(baseDir, "silero_vad.onnx.tmp")
            try {
                _downloadState.value = SileroDownloadState.Downloading(0f, 0L, ESTIMATED_BYTES)
                Log.i(TAG, "Starting Silero VAD download (~643 KB)")

                val success = downloadWithMirrorFallback(VAD_URLS, tempFile, ESTIMATED_BYTES) { bytes, total ->
                    val ratio = (bytes.toFloat() / total.toFloat()).coerceIn(0f, 0.99f)
                    _downloadState.value = SileroDownloadState.Downloading(ratio, bytes, total)
                }

                if (!success || !tempFile.exists() || tempFile.length() < 50_000L) {
                    throw IllegalStateException("Failed to download Silero VAD model from mirrors")
                }

                if (vadFile.exists()) vadFile.delete()
                tempFile.renameTo(vadFile)
                Log.i(TAG, "Silero VAD model ready: ${vadFile.length()} bytes")
                _downloadState.value = SileroDownloadState.Ready
            } catch (e: Exception) {
                if (isActive) {
                    Log.e(TAG, "Silero VAD download failed: ${e.message}", e)
                    tempFile.delete()
                    _downloadState.value = SileroDownloadState.Error("Download failed: ${e.message}")
                }
            } finally {
                downloadJob = null
            }
        }
    }

    private fun downloadWithMirrorFallback(
        urls: List<String>,
        targetFile: File,
        grandTotal: Long,
        onProgress: (bytes: Long, total: Long) -> Unit
    ): Boolean {
        for (url in urls) {
            try {
                Log.d(TAG, "Trying Silero VAD mirror: $url")
                if (tryDownloadFile(url, targetFile, grandTotal, onProgress)) {
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

        var currentBytes = 0L
        resp.use {
            body.byteStream().use { input ->
                FileOutputStream(targetFile).use { output ->
                    val buffer = ByteArray(32 * 1024)
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
        File(baseDir, "silero_vad.onnx.tmp").delete()
        refreshState()
    }

    fun deleteModel() {
        downloadJob?.cancel()
        vadFile.delete()
        refreshState()
    }
}
