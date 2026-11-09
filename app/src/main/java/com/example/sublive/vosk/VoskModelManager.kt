package com.example.sublive.vosk

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
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream

sealed class VoskDownloadState {
    data object NotDownloaded : VoskDownloadState()
    data class Downloading(val progress: Float, val bytesDownloaded: Long, val totalBytes: Long) : VoskDownloadState()
    data object Downloaded : VoskDownloadState()
    data object Ready : VoskDownloadState()
    data class Error(val message: String) : VoskDownloadState()
}

class VoskModelManager(private val context: Context) {
    companion object {
        private const val TAG = "VoskModelManager"
        const val VOSK_MODEL_NAME = "vosk-model-small-en-us-0.15"
        const val VOSK_MODEL_URL = "https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip"
        const val ESTIMATED_BYTES = 40_000_000L
    }

    private val baseDir = File(context.filesDir, "vosk_models").apply { mkdirs() }
    val modelDir = File(baseDir, VOSK_MODEL_NAME)

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private val _downloadState = MutableStateFlow<VoskDownloadState>(VoskDownloadState.NotDownloaded)
    val downloadState: StateFlow<VoskDownloadState> = _downloadState.asStateFlow()

    private var downloadJob: Job? = null

    init {
        refreshState()
    }

    fun isModelReady(): Boolean {
        if (!modelDir.exists() || !modelDir.isDirectory) return false
        val confFile = File(modelDir, "conf/model.conf")
        val amDir = File(modelDir, "am")
        return confFile.exists() || amDir.exists() || (modelDir.listFiles()?.isNotEmpty() == true)
    }

    fun isModelDownloaded(): Boolean = isModelReady()
    fun downloadModel(scope: CoroutineScope) = downloadAndExtractModel(scope)

    fun refreshState() {
        if (isModelReady()) {
            _downloadState.value = VoskDownloadState.Ready
        } else {
            _downloadState.value = VoskDownloadState.NotDownloaded
        }
    }

    fun downloadAndExtractModel(scope: CoroutineScope) {
        if (downloadJob?.isActive == true) return

        downloadJob = scope.launch(Dispatchers.IO) {
            val zipFile = File(baseDir, "$VOSK_MODEL_NAME.zip")
            try {
                _downloadState.value = VoskDownloadState.Downloading(0f, 0L, ESTIMATED_BYTES)
                Log.i(TAG, "Starting Vosk model download from $VOSK_MODEL_URL")

                val request = Request.Builder()
                    .url(VOSK_MODEL_URL)
                    .header("User-Agent", "Mozilla/5.0 SubLiveAI/1.0")
                    .build()

                val response = httpClient.newCall(request).execute()
                if (!response.isSuccessful) {
                    throw IllegalStateException("HTTP error ${response.code}")
                }

                val body = response.body ?: throw IllegalStateException("Empty response body")
                val totalLength = if (body.contentLength() > 0) body.contentLength() else ESTIMATED_BYTES

                body.byteStream().use { input ->
                    FileOutputStream(zipFile).use { output ->
                        val buffer = ByteArray(32 * 1024)
                        var bytesRead: Int
                        var downloadedSoFar = 0L
                        var lastReportTime = 0L

                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            if (!isActive) {
                                zipFile.delete()
                                return@launch
                            }
                            output.write(buffer, 0, bytesRead)
                            downloadedSoFar += bytesRead

                            val now = System.currentTimeMillis()
                            if (now - lastReportTime > 250) {
                                lastReportTime = now
                                val progress = (downloadedSoFar.toFloat() / totalLength.toFloat()).coerceIn(0f, 1f)
                                _downloadState.value = VoskDownloadState.Downloading(progress, downloadedSoFar, totalLength)
                            }
                        }
                    }
                }

                // Extract ZIP file
                Log.i(TAG, "Download finished. Unzipping Vosk model...")
                unzip(zipFile, baseDir)
                zipFile.delete()

                if (isModelReady()) {
                    _downloadState.value = VoskDownloadState.Ready
                    Log.i(TAG, "Vosk model successfully extracted and ready!")
                } else {
                    throw IllegalStateException("Extracted Vosk model structure verification failed")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error downloading or extracting Vosk model", e)
                zipFile.delete()
                _downloadState.value = VoskDownloadState.Error("Vosk download failed: ${e.message?.take(40)}")
            }
        }
    }

    fun cancelDownload() {
        downloadJob?.cancel()
        downloadJob = null
        File(baseDir, "$VOSK_MODEL_NAME.zip").delete()
        refreshState()
    }

    fun deleteModel() {
        downloadJob?.cancel()
        modelDir.deleteRecursively()
        refreshState()
    }

    private fun unzip(zipFile: File, targetDir: File) {
        ZipInputStream(BufferedInputStream(FileInputStream(zipFile))).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val file = File(targetDir, entry.name)
                if (entry.isDirectory) {
                    file.mkdirs()
                } else {
                    file.parentFile?.mkdirs()
                    FileOutputStream(file).use { fos ->
                        zis.copyTo(fos)
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
    }
}
