package com.example.sublive.overlay

/**
 * Mutually exclusive subtitle and translation engine choices.
 */
enum class SubtitleEngineChoice(
    val id: String,
    val title: String,
    val shortLabel: String,
    val description: String,
    val requiresWhisperModel: Boolean,
    val requiresVoskModel: Boolean,
    val requiresSenseVoiceModel: Boolean = false,
    val requiresSileroModel: Boolean = false,
    val requiresLfm3Model: Boolean = false,
    val requiresLfm3TextModel: Boolean = false,
    val usesMlKitTranslation: Boolean
) {
    WHISPER_DIRECT(
        id = "whisper_direct",
        title = "Whisper Direct AI (Audio → English)",
        shortLabel = "Whisper Direct",
        description = "Whisper AI model directly translates spoken speech into English subtitles (Zero ML Kit)",
        requiresWhisperModel = true,
        requiresVoskModel = false,
        requiresSenseVoiceModel = false,
        requiresSileroModel = false,
        requiresLfm3Model = false,
        requiresLfm3TextModel = false,
        usesMlKitTranslation = false
    ),
    WHISPER_MLKIT(
        id = "whisper_mlkit",
        title = "Whisper ASR + Android ML Kit",
        shortLabel = "Whisper + ML Kit",
        description = "Whisper AI transcribes spoken audio, then Android ML Kit translates into target language",
        requiresWhisperModel = true,
        requiresVoskModel = false,
        requiresSenseVoiceModel = false,
        requiresSileroModel = false,
        requiresLfm3Model = false,
        requiresLfm3TextModel = false,
        usesMlKitTranslation = true
    ),
    VOSK_MLKIT(
        id = "vosk_mlkit",
        title = "Vosk ASR + Android ML Kit",
        shortLabel = "Vosk + ML Kit",
        description = "Vosk lightweight offline ASR engine transcribes audio, then Android ML Kit translates into target language",
        requiresWhisperModel = false,
        requiresVoskModel = true,
        requiresSenseVoiceModel = false,
        requiresSileroModel = false,
        requiresLfm3Model = false,
        requiresLfm3TextModel = false,
        usesMlKitTranslation = true
    ),
    SENSEVOICE_MLKIT(
        id = "sensevoice_mlkit",
        title = "SenseVoice (Sherpa-ONNX) + Google ML Kit",
        shortLabel = "SenseVoice + ML Kit",
        description = "SenseVoice multilingual model via Sherpa-ONNX transcribes audio, then Google ML Kit translates into target language",
        requiresWhisperModel = false,
        requiresVoskModel = false,
        requiresSenseVoiceModel = true,
        requiresSileroModel = false,
        requiresLfm3Model = false,
        requiresLfm3TextModel = false,
        usesMlKitTranslation = true
    ),
    SILERO_SENSEVOICE_LFM3(
        id = "silero_sensevoice_lfm3",
        title = "Silero VAD + SenseVoice-Small + LFM3 4-bit",
        shortLabel = "Silero+SenseVoice+LFM3",
        description = "Silero VAD filters noise, SenseVoice-Small ONNX transcribes, and LFM3 4-bit translates into target language",
        requiresWhisperModel = false,
        requiresVoskModel = false,
        requiresSenseVoiceModel = true,
        requiresSileroModel = true,
        requiresLfm3Model = false,
        requiresLfm3TextModel = true,
        usesMlKitTranslation = false
    ),
    LFM3_OMNI_LITERT(
        id = "lfm3_omni_litert",
        title = "LFM3-Omni-2B (LiteRT Standalone)",
        shortLabel = "LFM3-Omni (LiteRT)",
        description = "Liquid AI LFM3-Omni 2B model on Google LiteRT directly translates & generates subtitles end-to-end (Zero ML Kit)",
        requiresWhisperModel = false,
        requiresVoskModel = false,
        requiresSenseVoiceModel = false,
        requiresSileroModel = false,
        requiresLfm3Model = true,
        requiresLfm3TextModel = false,
        usesMlKitTranslation = false
    ),
    WHISPER_ORIGINAL(
        id = "whisper_original",
        title = "Whisper Original (No Translation)",
        shortLabel = "Whisper ASR Only",
        description = "Whisper transcribes spoken audio in its original language without translation",
        requiresWhisperModel = true,
        requiresVoskModel = false,
        requiresSenseVoiceModel = false,
        requiresSileroModel = false,
        requiresLfm3Model = false,
        requiresLfm3TextModel = false,
        usesMlKitTranslation = false
    )
}

data class OverlaySettings(
    val fontSizeSp: Int = 18,
    val backgroundOpacity: Float = 0.85f,
    val isPaused: Boolean = false,
    val isDraggable: Boolean = true,
    val engineChoice: SubtitleEngineChoice = SubtitleEngineChoice.WHISPER_DIRECT,
    val sourceLanguage: String = "Auto Detect",
    val targetLanguage: String = "English",
    val enableTurnTakingWait: Boolean = true,
    val turnTakingWaitMs: Long = 800L,
    val enableLocalAgreement: Boolean = true
) {
    val useBuiltInTranslation: Boolean get() = engineChoice.usesMlKitTranslation
    val whisperTranslateToEnglish: Boolean get() = engineChoice == SubtitleEngineChoice.WHISPER_DIRECT
    val isTranslating: Boolean get() = engineChoice != SubtitleEngineChoice.WHISPER_ORIGINAL
    val isVoskActive: Boolean get() = engineChoice == SubtitleEngineChoice.VOSK_MLKIT
    val isSenseVoiceActive: Boolean get() = engineChoice == SubtitleEngineChoice.SENSEVOICE_MLKIT
    val isLfm3Active: Boolean get() = engineChoice == SubtitleEngineChoice.LFM3_OMNI_LITERT
    val isSileroSenseVoiceLfm3Active: Boolean get() = engineChoice == SubtitleEngineChoice.SILERO_SENSEVOICE_LFM3
}
