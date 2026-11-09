package com.example.sublive.audio

import android.annotation.SuppressLint
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.max

enum class CaptureMode {
    SYSTEM_PLAYBACK,
    MICROPHONE_FALLBACK
}

enum class CaptureState {
    IDLE,
    INITIALIZING,
    RECORDING,
    SILENCE_DETECTED,
    RESTRICTED_SOURCE,
    ERROR
}

data class AudioCaptureDiagnostics(
    val state: CaptureState = CaptureState.IDLE,
    val mode: CaptureMode = CaptureMode.SYSTEM_PLAYBACK,
    val sampleRate: Int = AudioConfig.SAMPLE_RATE,
    val channels: String = "Mono (16-bit PCM)",
    val samplesCaptured: Long = 0,
    val currentLevel: AudioLevel = AudioLevel(0f, 0f, -96f, false, true),
    val bufferAvailableSamples: Int = 0,
    val droppedSamples: Long = 0,
    val zeroSignalDurationMs: Long = 0,
    val errorMessage: String? = null
)

class AudioCaptureManager(
    val ringBuffer: AudioRingBuffer = AudioRingBuffer()
) {
    companion object {
        private const val TAG = "AudioCaptureManager"
        private const val RESTRICTED_ZERO_THRESHOLD_MS = 5000L // 5s continuous zero signal triggers restricted warning
    }

    private val _diagnostics = MutableStateFlow(AudioCaptureDiagnostics())
    val diagnostics: StateFlow<AudioCaptureDiagnostics> = _diagnostics.asStateFlow()

    private var audioRecord: AudioRecord? = null
    private var captureJob: Job? = null
    private var mediaProjectionCallback: MediaProjection.Callback? = null

    /**
     * Start capturing system audio using MediaProjection.
     */
    @SuppressLint("MissingPermission")
    fun startSystemCapture(mediaProjection: MediaProjection, scope: CoroutineScope) {
        stopCapture()
        _diagnostics.value = _diagnostics.value.copy(
            state = CaptureState.INITIALIZING,
            mode = CaptureMode.SYSTEM_PLAYBACK,
            errorMessage = null
        )

        try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                _diagnostics.value = _diagnostics.value.copy(
                    state = CaptureState.ERROR,
                    errorMessage = "AudioPlaybackCapture requires Android 10 (API 29) or higher."
                )
                return
            }

            val callback = object : MediaProjection.Callback() {
                override fun onStop() {
                    Log.w(TAG, "MediaProjection was stopped by system or user")
                    stopCapture()
                }
            }
            mediaProjectionCallback = callback
            mediaProjection.registerCallback(callback, android.os.Handler(android.os.Looper.getMainLooper()))

            val playbackConfig = AudioPlaybackCaptureConfiguration.Builder(mediaProjection)
                .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                .addMatchingUsage(AudioAttributes.USAGE_GAME)
                .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
                .build()

            val audioFormat = AudioFormat.Builder()
                .setEncoding(AudioConfig.AUDIO_FORMAT)
                .setSampleRate(AudioConfig.SAMPLE_RATE)
                .setChannelMask(AudioConfig.CHANNEL_CONFIG)
                .build()

            val minBufSize = AudioRecord.getMinBufferSize(
                AudioConfig.SAMPLE_RATE,
                AudioConfig.CHANNEL_CONFIG,
                AudioConfig.AUDIO_FORMAT
            )
            val bufferSize = max(minBufSize * 2, AudioConfig.READ_CHUNK_BYTES * 4)

            val record = AudioRecord.Builder()
                .setAudioPlaybackCaptureConfig(playbackConfig)
                .setAudioFormat(audioFormat)
                .setBufferSizeInBytes(bufferSize)
                .build()

            if (record.state != AudioRecord.STATE_INITIALIZED) {
                record.release()
                _diagnostics.value = _diagnostics.value.copy(
                    state = CaptureState.ERROR,
                    errorMessage = "AudioRecord failed to initialize with MediaProjection"
                )
                return
            }

            audioRecord = record
            record.startRecording()
            launchReadLoop(record, scope, CaptureMode.SYSTEM_PLAYBACK)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start system playback capture", e)
            _diagnostics.value = _diagnostics.value.copy(
                state = CaptureState.ERROR,
                errorMessage = e.message ?: "Failed to initialize AudioPlaybackCapture"
            )
        }
    }

    /**
     * Start capturing microphone audio as a fallback.
     */
    @SuppressLint("MissingPermission")
    fun startMicrophoneCapture(scope: CoroutineScope) {
        stopCapture()
        _diagnostics.value = _diagnostics.value.copy(
            state = CaptureState.INITIALIZING,
            mode = CaptureMode.MICROPHONE_FALLBACK,
            errorMessage = null
        )

        try {
            val minBufSize = AudioRecord.getMinBufferSize(
                AudioConfig.SAMPLE_RATE,
                AudioConfig.CHANNEL_CONFIG,
                AudioConfig.AUDIO_FORMAT
            )
            val bufferSize = max(minBufSize * 2, AudioConfig.READ_CHUNK_BYTES * 4)

            // Try candidate audio sources in order: VOICE_RECOGNITION, MIC, DEFAULT
            val candidateSources = intArrayOf(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                MediaRecorder.AudioSource.MIC,
                MediaRecorder.AudioSource.DEFAULT
            )

            var record: AudioRecord? = null
            for (source in candidateSources) {
                try {
                    val candidate = AudioRecord(
                        source,
                        AudioConfig.SAMPLE_RATE,
                        AudioConfig.CHANNEL_CONFIG,
                        AudioConfig.AUDIO_FORMAT,
                        bufferSize
                    )
                    if (candidate.state == AudioRecord.STATE_INITIALIZED) {
                        record = candidate
                        Log.d(TAG, "Initialized microphone AudioRecord with audio source: $source")
                        break
                    } else {
                        candidate.release()
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Candidate source $source failed: ${e.message}")
                }
            }

            if (record == null || record.state != AudioRecord.STATE_INITIALIZED) {
                record?.release()
                _diagnostics.value = _diagnostics.value.copy(
                    state = CaptureState.ERROR,
                    errorMessage = "Microphone AudioRecord failed to initialize on all sources"
                )
                return
            }

            audioRecord = record
            record.startRecording()
            launchReadLoop(record, scope, CaptureMode.MICROPHONE_FALLBACK)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start microphone capture", e)
            _diagnostics.value = _diagnostics.value.copy(
                state = CaptureState.ERROR,
                errorMessage = e.message ?: "Failed to initialize Microphone AudioRecord"
            )
        }
    }

    private fun launchReadLoop(record: AudioRecord, scope: CoroutineScope, mode: CaptureMode) {
        captureJob = scope.launch(Dispatchers.IO) {
            val readBuffer = ShortArray(AudioConfig.READ_CHUNK_SAMPLES)
            var totalSamples: Long = 0
            var zeroSignalStartMs: Long = 0

            _diagnostics.value = _diagnostics.value.copy(
                state = CaptureState.RECORDING,
                mode = mode
            )

            while (isActive && record.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                val readSamples = record.read(readBuffer, 0, readBuffer.size)
                if (readSamples > 0) {
                    ringBuffer.write(readBuffer, 0, readSamples)
                    totalSamples += readSamples

                    val level = AudioLevelMeter.analyze(readBuffer, readSamples)
                    val now = System.currentTimeMillis()

                    var currentState = CaptureState.RECORDING
                    var zeroDuration = 0L

                    if (level.isZeroSignal) {
                        if (zeroSignalStartMs == 0L) zeroSignalStartMs = now
                        zeroDuration = now - zeroSignalStartMs
                        if (mode == CaptureMode.SYSTEM_PLAYBACK && zeroDuration > RESTRICTED_ZERO_THRESHOLD_MS) {
                            currentState = CaptureState.RESTRICTED_SOURCE
                        } else {
                            currentState = CaptureState.SILENCE_DETECTED
                        }
                    } else {
                        zeroSignalStartMs = 0L
                        if (!level.isSpeechLikely) {
                            currentState = CaptureState.SILENCE_DETECTED
                        }
                    }

                    _diagnostics.value = _diagnostics.value.copy(
                        state = currentState,
                        samplesCaptured = totalSamples,
                        currentLevel = level,
                        bufferAvailableSamples = ringBuffer.available(),
                        droppedSamples = ringBuffer.droppedSamplesCount,
                        zeroSignalDurationMs = zeroDuration
                    )
                } else if (readSamples < 0) {
                    Log.e(TAG, "AudioRecord read error: $readSamples")
                    _diagnostics.value = _diagnostics.value.copy(
                        state = CaptureState.ERROR,
                        errorMessage = "AudioRecord read error code: $readSamples"
                    )
                    break
                }
            }
        }
    }

    /**
     * Stop active capture and release audio resources.
     */
    fun stopCapture() {
        captureJob?.cancel()
        captureJob = null

        audioRecord?.let {
            try {
                if (it.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    it.stop()
                }
                it.release()
            } catch (e: Exception) {
                Log.w(TAG, "Exception while releasing AudioRecord", e)
            }
            audioRecord = null
        }

        _diagnostics.value = _diagnostics.value.copy(
            state = CaptureState.IDLE,
            currentLevel = AudioLevel(0f, 0f, -96f, false, true)
        )
    }

    /**
     * Inject synthesized test PCM samples into ring buffer (for emulator validation / pipeline testing).
     */
    fun injectTestSamples(samples: ShortArray) {
        ringBuffer.write(samples)
        val level = AudioLevelMeter.analyze(samples, samples.size)
        _diagnostics.value = _diagnostics.value.copy(
            state = CaptureState.RECORDING,
            samplesCaptured = _diagnostics.value.samplesCaptured + samples.size,
            currentLevel = level,
            bufferAvailableSamples = ringBuffer.available()
        )
    }
}
