package com.example.sublive.service

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.sublive.audio.AudioCaptureDiagnostics
import com.example.sublive.audio.AudioCaptureManager
import com.example.sublive.audio.AudioConfig
import com.example.sublive.engine.StabilizedSubtitleState
import com.example.sublive.engine.SubtitleItem
import com.example.sublive.engine.SubtitleStabilizer
import com.example.sublive.engine.WhisperInferenceEngine
import com.example.sublive.model.ModelManager
import com.example.sublive.overlay.FloatingOverlayManager
import com.example.sublive.overlay.OverlaySettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class SubtitleForegroundService : Service() {

    companion object {
        private const val TAG = "SubtitleService"
        const val CHANNEL_ID = "sublive_overlay_channel"
        const val NOTIFICATION_ID = 1001

        const val ACTION_START_SYSTEM_CAPTURE = "ACTION_START_SYSTEM_CAPTURE"
        const val ACTION_START_MIC_CAPTURE = "ACTION_START_MIC_CAPTURE"
        const val ACTION_STOP_SERVICE = "ACTION_STOP_SERVICE"
        const val ACTION_TOGGLE_OVERLAY = "ACTION_TOGGLE_OVERLAY"

        const val EXTRA_RESULT_CODE = "EXTRA_RESULT_CODE"
        const val EXTRA_RESULT_DATA = "EXTRA_RESULT_DATA"

        // Global singleton access for UI binding
        var activeMediaProjection: MediaProjection? = null
        var activeAudioCaptureManager: AudioCaptureManager? = null
            private set
        var activeInferenceEngine: WhisperInferenceEngine? = null
            private set

        private val _serviceRunning = MutableStateFlow(false)
        val serviceRunning: StateFlow<Boolean> = _serviceRunning.asStateFlow()

        private val _currentSubtitleState = MutableStateFlow(StabilizedSubtitleState())
        val currentSubtitleState: StateFlow<StabilizedSubtitleState> = _currentSubtitleState.asStateFlow()

        private val _overlaySettings = MutableStateFlow(OverlaySettings())
        val overlaySettings: StateFlow<OverlaySettings> = _overlaySettings.asStateFlow()

        private val _captureDiagnostics = MutableStateFlow(AudioCaptureDiagnostics())
        val captureDiagnostics: StateFlow<AudioCaptureDiagnostics> = _captureDiagnostics.asStateFlow()

        private val _translationStatus = MutableStateFlow("On-device translation ready")
        val translationStatus: StateFlow<String> = _translationStatus.asStateFlow()

        fun updateOverlaySettings(settings: OverlaySettings) {
            _overlaySettings.value = settings
        }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var processingLoopJob: Job? = null

    private lateinit var audioCaptureManager: AudioCaptureManager
    private lateinit var modelManager: ModelManager
    private lateinit var inferenceEngine: WhisperInferenceEngine
    private lateinit var subtitleStabilizer: SubtitleStabilizer
    private lateinit var overlayManager: FloatingOverlayManager
    private lateinit var translationEngine: com.example.sublive.engine.AndroidTranslationEngine
    private lateinit var voskModelManager: com.example.sublive.vosk.VoskModelManager
    private lateinit var voskEngine: com.example.sublive.vosk.VoskInferenceEngine
    private lateinit var senseVoiceModelManager: com.example.sublive.sherpa.SenseVoiceModelManager
    private lateinit var sherpaEngine: com.example.sublive.sherpa.SherpaInferenceEngine
    private lateinit var lfm3ModelManager: com.example.sublive.lfm.Lfm3OmniModelManager
    private lateinit var lfm3Engine: com.example.sublive.lfm.Lfm3OmniInferenceEngine
    private lateinit var sileroVadManager: com.example.sublive.vad.SileroVadManager
    private lateinit var sileroVadEngine: com.example.sublive.vad.SileroVadEngine
    private lateinit var lfm3TextTranslator: com.example.sublive.lfm.Lfm3TextTranslator

    // Turn-taking state for Silero VAD + SenseVoice-Small + LFM3-d1-2B (4-bit) pipeline
    private var isTurnTakingActive = false
    private var lastSpeechTimestampMs = 0L
    private var turnAccumulatedSpokenText = ""
    private var turnAccumulatedTranslatedText = ""

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Creating SubtitleForegroundService")
        createNotificationChannel()

        audioCaptureManager = AudioCaptureManager()
        activeAudioCaptureManager = audioCaptureManager
        modelManager = ModelManager(applicationContext)
        inferenceEngine = WhisperInferenceEngine(applicationContext, modelManager)
        activeInferenceEngine = inferenceEngine
        subtitleStabilizer = SubtitleStabilizer()
        overlayManager = FloatingOverlayManager(applicationContext)
        translationEngine = com.example.sublive.engine.AndroidTranslationEngine(applicationContext)
        voskModelManager = com.example.sublive.vosk.VoskModelManager(applicationContext)
        voskEngine = com.example.sublive.vosk.VoskInferenceEngine(applicationContext, voskModelManager)
        senseVoiceModelManager = com.example.sublive.sherpa.SenseVoiceModelManager(applicationContext)
        sherpaEngine = com.example.sublive.sherpa.SherpaInferenceEngine(applicationContext, senseVoiceModelManager)
        lfm3ModelManager = com.example.sublive.lfm.Lfm3OmniModelManager(applicationContext)
        lfm3Engine = com.example.sublive.lfm.Lfm3OmniInferenceEngine(applicationContext, lfm3ModelManager)
        sileroVadManager = com.example.sublive.vad.SileroVadManager(applicationContext)
        sileroVadEngine = com.example.sublive.vad.SileroVadEngine(applicationContext, sileroVadManager)
        lfm3TextTranslator = com.example.sublive.lfm.Lfm3TextTranslator(applicationContext)

        // Observe translation status
        serviceScope.launch {
            translationEngine.translationStatus.collect {
                _translationStatus.value = it
            }
        }

        // Observe audio diagnostics
        serviceScope.launch {
            audioCaptureManager.diagnostics.collect { diag ->
                _captureDiagnostics.value = diag
            }
        }

        // Observe subtitle state to update overlay
        serviceScope.launch {
            _currentSubtitleState.collect { state ->
                overlayManager.updateSubtitleState(state)
            }
        }

        // Observe overlay settings and pre-warm translator
        serviceScope.launch {
            _overlaySettings.collect { settings ->
                overlayManager.updateSettings(settings)
                if (settings.isTranslating) {
                    val target = when (settings.targetLanguage.lowercase()) {
                        "korean" -> com.example.sublive.engine.AppTargetLanguage.KOREAN
                        "spanish" -> com.example.sublive.engine.AppTargetLanguage.SPANISH
                        "japanese" -> com.example.sublive.engine.AppTargetLanguage.JAPANESE
                        "chinese" -> com.example.sublive.engine.AppTargetLanguage.CHINESE
                        "french" -> com.example.sublive.engine.AppTargetLanguage.FRENCH
                        "german" -> com.example.sublive.engine.AppTargetLanguage.GERMAN
                        else -> com.example.sublive.engine.AppTargetLanguage.ENGLISH
                    }
                    val source = when (settings.sourceLanguage.lowercase()) {
                        "korean" -> com.google.mlkit.nl.translate.TranslateLanguage.KOREAN
                        "spanish" -> com.google.mlkit.nl.translate.TranslateLanguage.SPANISH
                        "japanese" -> com.google.mlkit.nl.translate.TranslateLanguage.JAPANESE
                        "chinese" -> com.google.mlkit.nl.translate.TranslateLanguage.CHINESE
                        "french" -> com.google.mlkit.nl.translate.TranslateLanguage.FRENCH
                        "german" -> com.google.mlkit.nl.translate.TranslateLanguage.GERMAN
                        else -> if (target == com.example.sublive.engine.AppTargetLanguage.ENGLISH) com.google.mlkit.nl.translate.TranslateLanguage.KOREAN else com.google.mlkit.nl.translate.TranslateLanguage.ENGLISH
                    }
                    translationEngine.prepareTranslator(source, target.mlKitCode)
                }
            }
        }
        // Initial pre-warm for Korean -> English live translation
        serviceScope.launch {
            translationEngine.prepareTranslator(com.google.mlkit.nl.translate.TranslateLanguage.KOREAN, com.google.mlkit.nl.translate.TranslateLanguage.ENGLISH)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        Log.d(TAG, "onStartCommand action: $action")

        when (action) {
            ACTION_START_SYSTEM_CAPTURE -> {
                // In Android 14+, startForeground MUST be called before getMediaProjection()
                startForegroundWithNotification("Capturing system audio & live translating...")
                _serviceRunning.value = true

                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
                val resultData = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(EXTRA_RESULT_DATA)
                }

                var projection = activeMediaProjection
                if (projection == null && resultCode == Activity.RESULT_OK && resultData != null) {
                    try {
                        val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                        projection = mpm.getMediaProjection(resultCode, resultData)
                        activeMediaProjection = projection
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to instantiate MediaProjection on Android 14", e)
                    }
                }

                if (projection != null) {
                    audioCaptureManager.startSystemCapture(projection, serviceScope)
                    startInferenceLoop()
                    overlayManager.show()
                } else {
                    Log.w(TAG, "MediaProjection not available. Falling back to high-sensitivity Microphone mode.")
                    audioCaptureManager.startMicrophoneCapture(serviceScope)
                    startInferenceLoop()
                    overlayManager.show()
                }
            }

            ACTION_START_MIC_CAPTURE -> {
                startForegroundWithNotification("Capturing microphone audio fallback...")
                _serviceRunning.value = true
                audioCaptureManager.startMicrophoneCapture(serviceScope)
                startInferenceLoop()
                overlayManager.show()
            }

            ACTION_STOP_SERVICE -> {
                stopProcessingAndService()
            }

            ACTION_TOGGLE_OVERLAY -> {
                if (overlayManager.isShowing()) {
                    overlayManager.hide()
                } else {
                    overlayManager.show()
                }
            }

            else -> {
                startForegroundWithNotification("SubLive AI service active")
            }
        }

        return START_NOT_STICKY
    }

    private fun startInferenceLoop() {
        processingLoopJob?.cancel()
        processingLoopJob = serviceScope.launch(Dispatchers.Default) {
            val windowBuffer = ShortArray(AudioConfig.WINDOW_SAMPLES)

            while (isActive) {
                // If user paused subtitles via overlay or settings, sleep briefly
                if (_overlaySettings.value.isPaused) {
                    delay(400)
                    continue
                }

                val currentSettings = _overlaySettings.value
                val engineChoice = currentSettings.engineChoice

                // Guard: Check model availability depending on chosen engine
                if (engineChoice.requiresWhisperModel && !modelManager.hasDownloadedModel()) {
                    _currentSubtitleState.value = StabilizedSubtitleState(
                        currentPartial = SubtitleItem(
                            text = "No Whisper model found. Please download Whisper Tiny in the Models tab.",
                            originalText = "No Whisper model found. Please download Whisper Tiny in the Models tab.",
                            isFinalized = false
                        ),
                        finalizedHistory = emptyList(),
                        isProcessing = false
                    )
                    delay(1200)
                    continue
                }

                if (engineChoice.requiresVoskModel && !voskModelManager.isModelReady()) {
                    _currentSubtitleState.value = StabilizedSubtitleState(
                        currentPartial = SubtitleItem(
                            text = "No Vosk model found. Please download Vosk ASR model in the Models tab.",
                            originalText = "No Vosk model found. Please download Vosk ASR model in the Models tab.",
                            isFinalized = false
                        ),
                        finalizedHistory = emptyList(),
                        isProcessing = false
                    )
                    delay(1200)
                    continue
                }

                if (engineChoice.requiresSenseVoiceModel && !senseVoiceModelManager.isModelReady()) {
                    _currentSubtitleState.value = StabilizedSubtitleState(
                        currentPartial = SubtitleItem(
                            text = "No SenseVoice model found. Please download SenseVoice model in the Models tab.",
                            originalText = "No SenseVoice model found. Please download SenseVoice model in the Models tab.",
                            isFinalized = false
                        ),
                        finalizedHistory = emptyList(),
                        isProcessing = false
                    )
                    delay(1200)
                    continue
                }

                if (engineChoice.requiresLfm3Model && !lfm3ModelManager.isModelReady()) {
                    _currentSubtitleState.value = StabilizedSubtitleState(
                        currentPartial = SubtitleItem(
                            text = "No LFM3-Omni model found. Please download LFM3-Omni-2B in the Models tab.",
                            originalText = "No LFM3-Omni model found. Please download LFM3-Omni-2B in the Models tab.",
                            isFinalized = false
                        ),
                        finalizedHistory = emptyList(),
                        isProcessing = false
                    )
                    delay(1200)
                    continue
                }

                val available = audioCaptureManager.ringBuffer.available()

                // Trigger processing when audio window is ready (e.g. 8000 samples = 500ms)
                if (available >= 8000) {
                    val readCount = audioCaptureManager.ringBuffer.readLatest(windowBuffer, AudioConfig.WINDOW_SAMPLES)
                    if (readCount > 0) {
                        _currentSubtitleState.value = _currentSubtitleState.value.copy(isProcessing = true)

                        val targetLangStr = currentSettings.targetLanguage.lowercase()
                        val target = when (targetLangStr) {
                            "korean" -> com.example.sublive.engine.AppTargetLanguage.KOREAN
                            "spanish" -> com.example.sublive.engine.AppTargetLanguage.SPANISH
                            "japanese" -> com.example.sublive.engine.AppTargetLanguage.JAPANESE
                            "chinese" -> com.example.sublive.engine.AppTargetLanguage.CHINESE
                            "french" -> com.example.sublive.engine.AppTargetLanguage.FRENCH
                            "german" -> com.example.sublive.engine.AppTargetLanguage.GERMAN
                            else -> com.example.sublive.engine.AppTargetLanguage.ENGLISH
                        }

                        var recognizedText = ""
                        var translatedText = ""
                        var isSpeechDetected = false
                        var turnStatus = ""
                        val isTranslating = engineChoice != com.example.sublive.overlay.SubtitleEngineChoice.WHISPER_ORIGINAL

                        val targetLangName = when (engineChoice) {
                            com.example.sublive.overlay.SubtitleEngineChoice.WHISPER_DIRECT -> "English"
                            com.example.sublive.overlay.SubtitleEngineChoice.LFM3_OMNI_LITERT -> currentSettings.targetLanguage
                            com.example.sublive.overlay.SubtitleEngineChoice.SILERO_SENSEVOICE_LFM3 -> currentSettings.targetLanguage
                            else -> currentSettings.targetLanguage
                        }

                        when (engineChoice) {
                            com.example.sublive.overlay.SubtitleEngineChoice.WHISPER_DIRECT -> {
                                // 1. Whisper directly translates to English (serving_en) - ZERO Android ML Kit
                                val result = inferenceEngine.processAudioWindow(
                                    samples = windowBuffer,
                                    sampleCount = readCount,
                                    whisperTranslateToEnglish = true
                                )
                                recognizedText = result.recognizedText
                                translatedText = result.recognizedText
                                isSpeechDetected = result.isSpeechDetected
                            }

                            com.example.sublive.overlay.SubtitleEngineChoice.WHISPER_MLKIT -> {
                                // 2. Whisper transcribes in spoken language, then Android ML Kit translates
                                val result = inferenceEngine.processAudioWindow(
                                    samples = windowBuffer,
                                    sampleCount = readCount,
                                    whisperTranslateToEnglish = false
                                )
                                recognizedText = result.recognizedText
                                isSpeechDetected = result.isSpeechDetected
                                if (recognizedText.isNotBlank()) {
                                    val translated = translationEngine.translateText(
                                        text = recognizedText,
                                        targetLang = target,
                                        sourceLanguageSetting = currentSettings.sourceLanguage
                                    )
                                    translatedText = if (translated.isNotBlank()) translated else recognizedText
                                }
                            }

                            com.example.sublive.overlay.SubtitleEngineChoice.VOSK_MLKIT -> {
                                // 3. Vosk offline ASR transcribes, then Android ML Kit translates
                                val voskText = voskEngine.processAudioWindow(windowBuffer, readCount)
                                recognizedText = voskText
                                isSpeechDetected = voskText.isNotBlank()
                                if (recognizedText.isNotBlank()) {
                                    val translated = translationEngine.translateText(
                                        text = recognizedText,
                                        targetLang = target,
                                        sourceLanguageSetting = currentSettings.sourceLanguage
                                    )
                                    translatedText = if (translated.isNotBlank()) translated else recognizedText
                                }
                            }

                            com.example.sublive.overlay.SubtitleEngineChoice.SENSEVOICE_MLKIT -> {
                                // 4. SenseVoice via Sherpa-ONNX transcribes audio, then Android ML Kit translates
                                val sherpaText = sherpaEngine.processAudioWindow(windowBuffer, readCount)
                                recognizedText = sherpaText
                                isSpeechDetected = sherpaText.isNotBlank()
                                if (recognizedText.isNotBlank()) {
                                    val translated = translationEngine.translateText(
                                        text = recognizedText,
                                        targetLang = target,
                                        sourceLanguageSetting = currentSettings.sourceLanguage
                                    )
                                    translatedText = if (translated.isNotBlank()) translated else recognizedText
                                }
                            }

                            com.example.sublive.overlay.SubtitleEngineChoice.SILERO_SENSEVOICE_LFM3 -> {
                                // 5. Silero VAD + SenseVoice-Small ONNX + LFM3 4-bit Neural Translator with Turn-Taking Wait
                                val isVoiceActive = sileroVadEngine.isSpeech(windowBuffer, readCount)
                                val now = System.currentTimeMillis()

                                if (isVoiceActive) {
                                    isSpeechDetected = true
                                    isTurnTakingActive = true
                                    lastSpeechTimestampMs = now

                                    val transcribed = sherpaEngine.processAudioWindow(windowBuffer, readCount)
                                    if (transcribed.isNotBlank()) {
                                        recognizedText = transcribed
                                        turnAccumulatedSpokenText = transcribed

                                        // Translate immediately so the user always gets live real-time translation!
                                        val translated = lfm3TextTranslator.translate(
                                            text = transcribed,
                                            targetLanguage = currentSettings.targetLanguage,
                                            fallbackEngine = translationEngine,
                                            sourceLanguage = currentSettings.sourceLanguage
                                        )
                                        translatedText = if (translated.isNotBlank()) translated else transcribed
                                        turnAccumulatedTranslatedText = translatedText
                                        turnStatus = "🎙️ Speaking..."
                                    } else {
                                        recognizedText = turnAccumulatedSpokenText
                                        translatedText = turnAccumulatedTranslatedText
                                        turnStatus = if (recognizedText.isNotBlank()) "🎙️ Speaking..." else ""
                                    }
                                } else if (currentSettings.enableTurnTakingWait && isTurnTakingActive) {
                                    val silenceDuration = now - lastSpeechTimestampMs
                                    if (silenceDuration < currentSettings.turnTakingWaitMs) {
                                        // TURN-TAKING WAIT IN PROGRESS: Speaker pause / breathing room. Keep live translation!
                                        isSpeechDetected = true
                                        recognizedText = turnAccumulatedSpokenText
                                        translatedText = turnAccumulatedTranslatedText
                                        val remainingMs = currentSettings.turnTakingWaitMs - silenceDuration
                                        turnStatus = "⏳ Turn-Taking Wait (${remainingMs}ms)..."
                                    } else {
                                        // TURN-TAKING WAIT COMPLETED: End of turn reached!
                                        isTurnTakingActive = false
                                        isSpeechDetected = true
                                        turnStatus = "✓ Turn Completed"
                                        recognizedText = turnAccumulatedSpokenText
                                        translatedText = turnAccumulatedTranslatedText

                                        // Final sentence translation pass
                                        if (turnAccumulatedSpokenText.isNotBlank()) {
                                            val finalTranslated = lfm3TextTranslator.translate(
                                                text = turnAccumulatedSpokenText,
                                                targetLanguage = currentSettings.targetLanguage,
                                                fallbackEngine = translationEngine,
                                                sourceLanguage = currentSettings.sourceLanguage
                                            )
                                            if (finalTranslated.isNotBlank()) {
                                                translatedText = finalTranslated
                                                turnAccumulatedTranslatedText = finalTranslated
                                            }
                                        }

                                        turnAccumulatedSpokenText = ""
                                        turnAccumulatedTranslatedText = ""
                                    }
                                } else {
                                    isTurnTakingActive = false
                                    isSpeechDetected = false
                                    recognizedText = ""
                                    translatedText = ""
                                    turnStatus = ""
                                }
                            }

                            com.example.sublive.overlay.SubtitleEngineChoice.LFM3_OMNI_LITERT -> {
                                // 6. Liquid AI LFM3-Omni-2B Standalone LiteRT engine
                                val lfmResult = lfm3Engine.processAudioWindow(windowBuffer, readCount)
                                isSpeechDetected = lfmResult.isSpeechDetected

                                // If LFM3 output contains valid textual tokens, use it; otherwise decode via acoustic engine
                                recognizedText = if (lfmResult.text.isNotBlank() && lfmResult.text.any { it.isLetterOrDigit() }) {
                                    lfmResult.text
                                } else if (isSpeechDetected) {
                                    if (senseVoiceModelManager.isModelReady()) {
                                        sherpaEngine.processAudioWindow(windowBuffer, readCount)
                                    } else {
                                        inferenceEngine.processAudioWindow(windowBuffer, readCount, false).recognizedText
                                    }
                                } else {
                                    ""
                                }

                                if (recognizedText.isNotBlank()) {
                                    val translated = translationEngine.translateText(
                                        text = recognizedText,
                                        targetLang = target,
                                        sourceLanguageSetting = currentSettings.sourceLanguage
                                    )
                                    translatedText = if (translated.isNotBlank()) translated else recognizedText
                                } else {
                                    translatedText = ""
                                }
                            }

                            com.example.sublive.overlay.SubtitleEngineChoice.WHISPER_ORIGINAL -> {
                                // 7. Whisper transcription only - no translation
                                val result = inferenceEngine.processAudioWindow(
                                    samples = windowBuffer,
                                    sampleCount = readCount,
                                    whisperTranslateToEnglish = false
                                )
                                recognizedText = result.recognizedText
                                translatedText = ""
                                isSpeechDetected = result.isSpeechDetected
                            }
                        }

                        val newState = subtitleStabilizer.processWindowResult(
                            recognizedText = recognizedText,
                            translatedText = translatedText,
                            isTranslationActive = isTranslating,
                            targetLanguage = targetLangName,
                            isSpeechDetected = isSpeechDetected,
                            turnStatus = turnStatus,
                            enableLocalAgreement = currentSettings.enableLocalAgreement
                        )

                        _currentSubtitleState.value = newState
                    }
                }

                // Smooth sliding window step (400ms)
                delay(400)
            }
        }
    }

    @SuppressLint("ForegroundServiceType")
    private fun startForegroundWithNotification(statusText: String) {
        val notification = buildNotification(statusText)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val fgsType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            } else {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            }
            startForeground(NOTIFICATION_ID, notification, fgsType)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(statusText: String): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, SubtitleForegroundService::class.java).apply {
            action = ACTION_STOP_SERVICE
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("SubLive AI — Real-time Subtitles")
            .setContentText(statusText)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Real-time Subtitle Overlay",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows live status while capturing audio and displaying subtitles overlay"
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun stopProcessingAndService() {
        processingLoopJob?.cancel()
        processingLoopJob = null
        isTurnTakingActive = false
        turnAccumulatedSpokenText = ""
        turnAccumulatedTranslatedText = ""
        audioCaptureManager.stopCapture()
        overlayManager.hide()
        _serviceRunning.value = false
        lfm3Engine.close()
        sherpaEngine.closeModel()
        sileroVadEngine.release()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopProcessingAndService()
        serviceScope.cancel()
        activeMediaProjection = null
        Log.d(TAG, "SubtitleForegroundService destroyed")
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
