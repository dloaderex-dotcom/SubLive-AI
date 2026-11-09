package com.example.sublive.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.sublive.audio.AudioCaptureDiagnostics
import com.example.sublive.audio.AudioConfig
import com.example.sublive.audio.CaptureMode
import com.example.sublive.audio.CaptureState
import com.example.sublive.engine.StabilizedSubtitleState
import com.example.sublive.engine.TargetLanguageMode
import com.example.sublive.model.ModelManager
import com.example.sublive.model.ModelStatus
import com.example.sublive.model.ModelTier
import com.example.sublive.overlay.OverlaySettings
import com.example.sublive.service.SubtitleForegroundService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class StudioLogEntry(
    val timestamp: String,
    val message: String,
    val isWarning: Boolean = false,
    val isError: Boolean = false
)

data class UiDashboardState(
    val selectedTab: Int = 0,
    val isServiceRunning: Boolean = false,
    val selectedCaptureMode: CaptureMode = CaptureMode.SYSTEM_PLAYBACK,
    val diagnostics: AudioCaptureDiagnostics = AudioCaptureDiagnostics(),
    val subtitleState: StabilizedSubtitleState = StabilizedSubtitleState(),
    val overlaySettings: OverlaySettings = OverlaySettings(),
    val modelStates: Map<ModelTier, ModelStatus> = emptyMap(),
    val activeModel: ModelTier = ModelTier.TFLITE_TINY,
    val accelerationBackend: com.example.sublive.engine.AccelerationBackend = com.example.sublive.engine.AccelerationBackend.CPU,
    val liveWaveform: List<Float> = List(48) { 0.05f },
    val translationStatusText: String = "Ready",
    val voskDownloadState: com.example.sublive.vosk.VoskDownloadState = com.example.sublive.vosk.VoskDownloadState.NotDownloaded,
    val senseVoiceDownloadState: com.example.sublive.sherpa.SenseVoiceDownloadState = com.example.sublive.sherpa.SenseVoiceDownloadState.NotDownloaded,
    val lfm3DownloadState: com.example.sublive.lfm.Lfm3DownloadState = com.example.sublive.lfm.Lfm3DownloadState.NotDownloaded,
    val sileroDownloadState: com.example.sublive.vad.SileroDownloadState = com.example.sublive.vad.SileroDownloadState.NotDownloaded,
    val lfm3TextDownloadState: com.example.sublive.lfm.Lfm3TextDownloadState = com.example.sublive.lfm.Lfm3TextDownloadState.NotDownloaded,
    val eventLogs: List<StudioLogEntry> = emptyList()
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val modelManager = ModelManager(application)
    val voskModelManager = com.example.sublive.vosk.VoskModelManager(application)
    val senseVoiceModelManager = com.example.sublive.sherpa.SenseVoiceModelManager(application)
    val lfm3ModelManager = com.example.sublive.lfm.Lfm3OmniModelManager(application)
    val sileroVadManager = com.example.sublive.vad.SileroVadManager(application)
    val lfm3TextTranslator = com.example.sublive.lfm.Lfm3TextTranslator(application)
    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())

    private val _uiState = MutableStateFlow(UiDashboardState())
    val uiState: StateFlow<UiDashboardState> = _uiState.asStateFlow()

    init {
        logEvent("SubLive AI initialized. Whisper, Vosk & SenseVoice (Sherpa-ONNX) ready.")

        // Collect model manager states
        viewModelScope.launch {
            modelManager.modelStates.collect { states ->
                _uiState.value = _uiState.value.copy(modelStates = states)
            }
        }
        viewModelScope.launch {
            modelManager.activeModel.collect { active ->
                _uiState.value = _uiState.value.copy(activeModel = active)
            }
        }

        // Collect Vosk model states
        viewModelScope.launch {
            voskModelManager.downloadState.collect { vState ->
                _uiState.value = _uiState.value.copy(voskDownloadState = vState)
            }
        }

        // Collect SenseVoice model states
        viewModelScope.launch {
            senseVoiceModelManager.downloadState.collect { sState ->
                _uiState.value = _uiState.value.copy(senseVoiceDownloadState = sState)
            }
        }

        // Collect LFM3-Omni model states
        viewModelScope.launch {
            lfm3ModelManager.downloadState.collect { lState ->
                _uiState.value = _uiState.value.copy(lfm3DownloadState = lState)
            }
        }

        // Collect Silero VAD states
        viewModelScope.launch {
            sileroVadManager.downloadState.collect { sState ->
                _uiState.value = _uiState.value.copy(sileroDownloadState = sState)
            }
        }

        // Collect LFM3 4-bit translator states
        viewModelScope.launch {
            lfm3TextTranslator.downloadState.collect { tState ->
                _uiState.value = _uiState.value.copy(lfm3TextDownloadState = tState)
            }
        }

        // Collect foreground service states
        viewModelScope.launch {
            SubtitleForegroundService.serviceRunning.collect { running ->
                _uiState.value = _uiState.value.copy(isServiceRunning = running)
                if (running) {
                    logEvent("Foreground service running with real-time overlay")
                }
            }
        }

        viewModelScope.launch {
            SubtitleForegroundService.captureDiagnostics.collect { diag ->
                updateDiagnosticsAndWaveform(diag)
            }
        }

        viewModelScope.launch {
            SubtitleForegroundService.currentSubtitleState.collect { subState ->
                _uiState.value = _uiState.value.copy(subtitleState = subState)
            }
        }

        viewModelScope.launch {
            SubtitleForegroundService.overlaySettings.collect { settings ->
                _uiState.value = _uiState.value.copy(overlaySettings = settings)
            }
        }

        viewModelScope.launch {
            SubtitleForegroundService.translationStatus.collect { status ->
                _uiState.value = _uiState.value.copy(translationStatusText = status)
            }
        }
    }

    private fun updateDiagnosticsAndWaveform(diag: AudioCaptureDiagnostics) {
        val currentWave = _uiState.value.liveWaveform.toMutableList()
        currentWave.removeAt(0)
        val newSample = if (diag.state == CaptureState.RECORDING) {
            (diag.currentLevel.peak).coerceIn(0.05f, 1.0f)
        } else {
            0.05f
        }
        currentWave.add(newSample)

        _uiState.value = _uiState.value.copy(
            diagnostics = diag,
            liveWaveform = currentWave
        )
    }

    fun setTab(index: Int) {
        _uiState.value = _uiState.value.copy(selectedTab = index)
    }

    fun setCaptureMode(mode: CaptureMode) {
        _uiState.value = _uiState.value.copy(selectedCaptureMode = mode)
        logEvent("Audio capture source set to: ${mode.name}")
    }

    fun updateOverlayFontSize(sp: Int) {
        val newSettings = _uiState.value.overlaySettings.copy(fontSizeSp = sp)
        SubtitleForegroundService.updateOverlaySettings(newSettings)
    }

    fun updateOverlayOpacity(opacity: Float) {
        val newSettings = _uiState.value.overlaySettings.copy(backgroundOpacity = opacity)
        SubtitleForegroundService.updateOverlaySettings(newSettings)
    }

    fun toggleOverlayPause() {
        val newSettings = _uiState.value.overlaySettings.copy(
            isPaused = !_uiState.value.overlaySettings.isPaused
        )
        SubtitleForegroundService.updateOverlaySettings(newSettings)
        logEvent("Overlay paused: ${newSettings.isPaused}")
    }

    fun toggleBuiltInTranslation() {
        val current = _uiState.value.overlaySettings.engineChoice
        val nextChoice = if (current == com.example.sublive.overlay.SubtitleEngineChoice.WHISPER_MLKIT) {
            com.example.sublive.overlay.SubtitleEngineChoice.WHISPER_ORIGINAL
        } else {
            com.example.sublive.overlay.SubtitleEngineChoice.WHISPER_MLKIT
        }
        setEngineChoice(nextChoice)
    }

    fun toggleWhisperTranslation() {
        val current = _uiState.value.overlaySettings.engineChoice
        val nextChoice = if (current == com.example.sublive.overlay.SubtitleEngineChoice.WHISPER_DIRECT) {
            com.example.sublive.overlay.SubtitleEngineChoice.WHISPER_ORIGINAL
        } else {
            com.example.sublive.overlay.SubtitleEngineChoice.WHISPER_DIRECT
        }
        setEngineChoice(nextChoice)
    }

    fun setSourceLanguage(language: String) {
        val newSettings = _uiState.value.overlaySettings.copy(
            sourceLanguage = language
        )
        SubtitleForegroundService.updateOverlaySettings(newSettings)
        logEvent("Audio source language set to: $language")
    }

    fun setTargetLanguage(language: String) {
        val newSettings = _uiState.value.overlaySettings.copy(
            targetLanguage = language
        )
        SubtitleForegroundService.updateOverlaySettings(newSettings)
        logEvent("Target translation language set to: $language")
    }

    fun downloadModel(tier: ModelTier) {
        logEvent("Starting download for ${tier.title} (${tier.sizeLabel})")
        modelManager.downloadModel(tier, viewModelScope)
    }

    fun cancelModelDownload() {
        modelManager.cancelDownload()
        logEvent("Model download cancelled")
    }

    fun setAccelerationBackend(backend: com.example.sublive.engine.AccelerationBackend) {
        _uiState.value = _uiState.value.copy(accelerationBackend = backend)
        SubtitleForegroundService.activeInferenceEngine?.setAccelerationBackend(backend)
        logEvent("AI Inference acceleration switched to: ${backend.label}")
    }

    fun selectModel(tier: ModelTier) {
        modelManager.selectModel(tier)
        logEvent("Active inference model switched to: ${tier.title}")
    }

    fun deleteModel(tier: ModelTier) {
        modelManager.deleteModel(tier)
        logEvent("Deleted local model file for: ${tier.title}")
    }

    fun setEngineChoice(choice: com.example.sublive.overlay.SubtitleEngineChoice) {
        val newSettings = _uiState.value.overlaySettings.copy(engineChoice = choice)
        SubtitleForegroundService.updateOverlaySettings(newSettings)
        when (choice) {
            com.example.sublive.overlay.SubtitleEngineChoice.LFM3_OMNI_LITERT -> lfm3ModelManager.refreshState()
            com.example.sublive.overlay.SubtitleEngineChoice.SENSEVOICE_MLKIT -> senseVoiceModelManager.refreshState()
            com.example.sublive.overlay.SubtitleEngineChoice.VOSK_MLKIT -> voskModelManager.refreshState()
            com.example.sublive.overlay.SubtitleEngineChoice.SILERO_SENSEVOICE_LFM3 -> {
                sileroVadManager.refreshState()
                senseVoiceModelManager.refreshState()
                lfm3TextTranslator.refreshState()
            }
            else -> {}
        }
        logEvent("Engine switched to: ${choice.shortLabel}")
    }

    fun downloadVoskModel() {
        logEvent("Starting Vosk offline model download (~40MB)")
        voskModelManager.downloadModel(viewModelScope)
    }

    fun cancelVoskDownload() {
        voskModelManager.cancelDownload()
        logEvent("Vosk model download cancelled")
    }

    fun deleteVoskModel() {
        voskModelManager.deleteModel()
        logEvent("Vosk model deleted from local storage")
    }

    fun isWhisperReady(): Boolean {
        return modelManager.hasDownloadedModel()
    }

    fun isVoskReady(): Boolean {
        return voskModelManager.isModelDownloaded()
    }

    fun downloadSenseVoiceModel() {
        logEvent("Starting SenseVoice (Sherpa-ONNX) download (~240MB)")
        senseVoiceModelManager.downloadModel(viewModelScope)
    }

    fun cancelSenseVoiceDownload() {
        senseVoiceModelManager.cancelDownload()
        logEvent("SenseVoice model download cancelled")
    }

    fun deleteSenseVoiceModel() {
        senseVoiceModelManager.deleteModel()
        logEvent("SenseVoice model deleted from local storage")
    }

    fun isSenseVoiceReady(): Boolean {
        return senseVoiceModelManager.isModelDownloaded()
    }

    fun downloadLfm3Model() {
        logEvent("Starting LFM3-Omni-2B (LiteRT) download (~198MB)")
        lfm3ModelManager.downloadModel(viewModelScope)
    }

    fun cancelLfm3Download() {
        lfm3ModelManager.cancelDownload()
        logEvent("LFM3-Omni model download cancelled")
    }

    fun deleteLfm3Model() {
        lfm3ModelManager.deleteModel()
        logEvent("LFM3-Omni model deleted from local storage")
    }

    fun isLfm3Ready(): Boolean {
        lfm3ModelManager.refreshState()
        return lfm3ModelManager.isModelDownloaded()
    }

    fun downloadSileroVad() {
        logEvent("Starting Silero VAD download (~643KB)")
        sileroVadManager.downloadModel(viewModelScope)
    }

    fun cancelSileroVadDownload() {
        sileroVadManager.cancelDownload()
        logEvent("Silero VAD download cancelled")
    }

    fun deleteSileroVad() {
        sileroVadManager.deleteModel()
        logEvent("Silero VAD deleted from local storage")
    }

    fun isSileroVadReady(): Boolean {
        return sileroVadManager.isModelDownloaded()
    }

    fun downloadLfm3TextModel() {
        logEvent("Starting LFM3 4-bit neural translation weights download")
        lfm3TextTranslator.downloadModel(viewModelScope)
    }

    fun cancelLfm3TextDownload() {
        lfm3TextTranslator.cancelDownload()
        logEvent("LFM3 4-bit model download cancelled")
    }

    fun deleteLfm3TextModel() {
        lfm3TextTranslator.deleteModel()
        logEvent("LFM3 4-bit model deleted from local storage")
    }

    fun isLfm3TextReady(): Boolean {
        return lfm3TextTranslator.isModelDownloaded()
    }

    fun isSileroSenseVoiceLfm3Ready(): Boolean {
        return isSenseVoiceReady()
    }

    fun isModelReady(): Boolean {
        return when (_uiState.value.overlaySettings.engineChoice) {
            com.example.sublive.overlay.SubtitleEngineChoice.VOSK_MLKIT -> isVoskReady()
            com.example.sublive.overlay.SubtitleEngineChoice.SENSEVOICE_MLKIT -> isSenseVoiceReady()
            com.example.sublive.overlay.SubtitleEngineChoice.LFM3_OMNI_LITERT -> isLfm3Ready()
            com.example.sublive.overlay.SubtitleEngineChoice.SILERO_SENSEVOICE_LFM3 -> isSileroSenseVoiceLfm3Ready()
            else -> isWhisperReady()
        }
    }

    fun getActiveOrFirstDownloadedModel(): ModelTier? {
        val currentFile = modelManager.getModelFile(_uiState.value.activeModel)
        if (currentFile != null && currentFile.exists()) {
            return _uiState.value.activeModel
        }
        return modelManager.getFirstDownloadedTier()
    }

    fun updateTurnTakingWait(enabled: Boolean) {
        val updated = _uiState.value.overlaySettings.copy(enableTurnTakingWait = enabled)
        SubtitleForegroundService.updateOverlaySettings(updated)
        _uiState.value = _uiState.value.copy(overlaySettings = updated)
        logEvent("Turn-Taking Wait ${if (enabled) "enabled" else "disabled"}")
    }

    fun updateTurnTakingWaitMs(waitMs: Long) {
        val updated = _uiState.value.overlaySettings.copy(turnTakingWaitMs = waitMs)
        SubtitleForegroundService.updateOverlaySettings(updated)
        _uiState.value = _uiState.value.copy(overlaySettings = updated)
        logEvent("Turn-Taking Wait duration set to ${waitMs}ms")
    }

    fun updateLocalAgreement(enabled: Boolean) {
        val updated = _uiState.value.overlaySettings.copy(enableLocalAgreement = enabled)
        SubtitleForegroundService.updateOverlaySettings(updated)
        _uiState.value = _uiState.value.copy(overlaySettings = updated)
        logEvent("Local Agreement Stability Protocol ${if (enabled) "enabled" else "disabled"}")
    }

    fun logEvent(message: String, isWarning: Boolean = false, isError: Boolean = false) {
        val entry = StudioLogEntry(
            timestamp = timeFormat.format(Date()),
            message = message,
            isWarning = isWarning,
            isError = isError
        )
        val list = _uiState.value.eventLogs.toMutableList()
        list.add(0, entry)
        if (list.size > 50) list.removeAt(list.lastIndex)
        _uiState.value = _uiState.value.copy(eventLogs = list)
    }
}
