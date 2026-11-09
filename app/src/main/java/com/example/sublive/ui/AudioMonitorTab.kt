package com.example.sublive.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.sublive.audio.CaptureMode
import com.example.sublive.audio.CaptureState
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CyanPrimary
import com.example.ui.theme.MintSuccess
import com.example.ui.theme.RoseError
import com.example.ui.theme.Slate400
import com.example.ui.theme.Slate700
import com.example.ui.theme.Slate800
import com.example.ui.theme.Slate900
import com.example.ui.theme.VioletAccent

import com.example.sublive.overlay.SubtitleEngineChoice

@Composable
fun AudioMonitorTab(
    uiState: UiDashboardState,
    onStartService: () -> Unit,
    onStopService: () -> Unit,
    onSelectMode: (CaptureMode) -> Unit,
    onSelectEngineChoice: (SubtitleEngineChoice) -> Unit = {},
    onSourceLanguageChange: (String) -> Unit = {},
    onTargetLanguageChange: (String) -> Unit = {}
) {
    val diag = uiState.diagnostics
    val isRecording = uiState.isServiceRunning
    val subState = uiState.subtitleState
    val currentSub = subState.currentPartial ?: subState.finalizedHistory.lastOrNull()
    val subtitleText = currentSub?.displayText ?: ""

    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. Audio Capture Source (Optimized for Fold 4 narrow screen)
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Slate800),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "AUDIO CAPTURE SOURCE",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Slate400,
                        letterSpacing = 0.8.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SourceOptionCard(
                            title = "System Audio",
                            subtitle = "Playback capture",
                            icon = Icons.Default.VolumeUp,
                            isSelected = uiState.selectedCaptureMode == CaptureMode.SYSTEM_PLAYBACK,
                            modifier = Modifier.weight(1f),
                            onClick = { onSelectMode(CaptureMode.SYSTEM_PLAYBACK) },
                            testTag = "mode_system_playback"
                        )

                        SourceOptionCard(
                            title = "Microphone",
                            subtitle = "Mic / Speaker",
                            icon = Icons.Default.Mic,
                            isSelected = uiState.selectedCaptureMode == CaptureMode.MICROPHONE_FALLBACK,
                            modifier = Modifier.weight(1f),
                            onClick = { onSelectMode(CaptureMode.MICROPHONE_FALLBACK) },
                            testTag = "mode_mic_fallback"
                        )
                    }

                    if (isRecording && uiState.selectedCaptureMode == CaptureMode.SYSTEM_PLAYBACK &&
                        (diag.state == CaptureState.SILENCE_DETECTED || diag.state == CaptureState.RESTRICTED_SOURCE || diag.currentLevel.decibels < -60f)) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(AmberWarning.copy(alpha = 0.15f))
                                .padding(10.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = AmberWarning,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "No media playback detected. Speak into the microphone or play video in YouTube/Chrome.",
                                    color = AmberWarning,
                                    fontSize = 11.sp,
                                    lineHeight = 15.sp
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Button(
                                onClick = { onSelectMode(CaptureMode.MICROPHONE_FALLBACK) },
                                colors = ButtonDefaults.buttonColors(containerColor = AmberWarning),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("switch_to_mic_prompt_button")
                            ) {
                                Icon(imageVector = Icons.Default.Mic, contentDescription = null, tint = Slate900, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Switch to Microphone Mode", color = Slate900, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // 2. Live Subtitle Stream Card
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Slate900),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(if (isRecording) MintSuccess else Slate400)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "LIVE TRANSCRIPTION",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isRecording) MintSuccess else Slate400,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            val engine = uiState.overlaySettings.engineChoice
                            val (badgeColor, badgeText) = when (engine) {
                                SubtitleEngineChoice.WHISPER_DIRECT -> Pair(CyanPrimary, "Whisper Direct (EN)")
                                SubtitleEngineChoice.WHISPER_MLKIT -> Pair(MintSuccess, "Whisper + ML Kit")
                                SubtitleEngineChoice.VOSK_MLKIT -> Pair(VioletAccent, "Vosk + ML Kit")
                                SubtitleEngineChoice.SENSEVOICE_MLKIT -> Pair(AmberWarning, "SenseVoice + ML Kit")
                                SubtitleEngineChoice.SILERO_SENSEVOICE_LFM3 -> Pair(Color(0xFFF43F5E), "Silero + SenseVoice + LFM3")
                                SubtitleEngineChoice.LFM3_OMNI_LITERT -> Pair(Color(0xFF38BDF8), "LFM3-Omni (LiteRT)")
                                SubtitleEngineChoice.WHISPER_ORIGINAL -> Pair(Slate400, "Whisper ASR Only")
                            }

                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = badgeColor.copy(alpha = 0.15f)
                            ) {
                                Text(
                                    text = badgeText,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = badgeColor,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }

                            if (engine != SubtitleEngineChoice.VOSK_MLKIT && engine != SubtitleEngineChoice.SENSEVOICE_MLKIT) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = CyanPrimary.copy(alpha = 0.15f)
                                ) {
                                    Text(
                                        text = "TFLite ${uiState.accelerationBackend.name.take(3)}",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = CyanPrimary,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    val settings = uiState.overlaySettings
                    val isVoskMode = settings.engineChoice == SubtitleEngineChoice.VOSK_MLKIT
                    val isVoskReady = uiState.voskDownloadState is com.example.sublive.vosk.VoskDownloadState.Downloaded ||
                            uiState.voskDownloadState is com.example.sublive.vosk.VoskDownloadState.Ready

                    val isSenseVoiceMode = settings.engineChoice == SubtitleEngineChoice.SENSEVOICE_MLKIT
                    val isSenseVoiceReady = uiState.senseVoiceDownloadState is com.example.sublive.sherpa.SenseVoiceDownloadState.Downloaded ||
                            uiState.senseVoiceDownloadState is com.example.sublive.sherpa.SenseVoiceDownloadState.Ready

                    val isSileroSenseVoiceLfm3Mode = settings.engineChoice == SubtitleEngineChoice.SILERO_SENSEVOICE_LFM3
                    val isSileroSenseVoiceLfm3Ready = isSenseVoiceReady

                    val isLfm3Mode = settings.engineChoice == SubtitleEngineChoice.LFM3_OMNI_LITERT
                    val isLfm3Ready = uiState.lfm3DownloadState is com.example.sublive.lfm.Lfm3DownloadState.Downloaded ||
                            uiState.lfm3DownloadState is com.example.sublive.lfm.Lfm3DownloadState.Ready

                    val activeModelStatus = uiState.modelStates[uiState.activeModel]
                    val isWhisperReady = activeModelStatus?.state is com.example.sublive.model.ModelDownloadState.Downloaded ||
                            activeModelStatus?.state is com.example.sublive.model.ModelDownloadState.Loaded

                    val isCurrentModelReady = when {
                        isVoskMode -> isVoskReady
                        isSenseVoiceMode -> isSenseVoiceReady
                        isSileroSenseVoiceLfm3Mode -> isSileroSenseVoiceLfm3Ready
                        isLfm3Mode -> isLfm3Ready
                        else -> isWhisperReady
                    }

                    // Show active model name or fallback warning banner
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isCurrentModelReady) Slate800 else AmberWarning.copy(alpha = 0.15f))
                            .padding(horizontal = 8.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isCurrentModelReady) Icons.Default.Info else Icons.Default.Warning,
                            contentDescription = null,
                            tint = if (isCurrentModelReady) CyanPrimary else AmberWarning,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isCurrentModelReady) {
                                when {
                                    isVoskMode -> "Model: Vosk Small US-EN (~40MB, Active)"
                                    isSenseVoiceMode -> "Model: SenseVoice Multi-lingual (~240MB, Active)"
                                    isSileroSenseVoiceLfm3Mode -> "Pipeline: Silero VAD + SenseVoice + LFM3 4-bit (Active)"
                                    isLfm3Mode -> "Model: LFM3-Omni-2B LiteRT (~198MB, Active)"
                                    else -> "Model: ${uiState.activeModel.title} (Active)"
                                }
                            } else {
                                when {
                                    isVoskMode -> "No Vosk model loaded! Download in 'Models' tab"
                                    isSenseVoiceMode -> "No SenseVoice model loaded! Download in 'Models' tab"
                                    isSileroSenseVoiceLfm3Mode -> "No SenseVoice model loaded! Download in 'Models' tab"
                                    isLfm3Mode -> "No LFM3-Omni model loaded! Download in 'Models' tab"
                                    else -> "No Whisper model loaded! Download in 'Models' tab"
                                }
                            },
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (isCurrentModelReady) Slate400 else AmberWarning
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Engine Quick Selector Chips
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Slate800)
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "ACTIVE ENGINE CHOICE",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = Slate400
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        LazyRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            items(SubtitleEngineChoice.entries) { choice ->
                                val isSelected = settings.engineChoice == choice
                                val chipColor = when (choice) {
                                    SubtitleEngineChoice.WHISPER_DIRECT -> CyanPrimary
                                    SubtitleEngineChoice.WHISPER_MLKIT -> MintSuccess
                                    SubtitleEngineChoice.VOSK_MLKIT -> VioletAccent
                                    SubtitleEngineChoice.SENSEVOICE_MLKIT -> AmberWarning
                                    SubtitleEngineChoice.SILERO_SENSEVOICE_LFM3 -> Color(0xFFF43F5E)
                                    SubtitleEngineChoice.LFM3_OMNI_LITERT -> Color(0xFF38BDF8)
                                    SubtitleEngineChoice.WHISPER_ORIGINAL -> Slate400
                                }
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (isSelected) chipColor else Slate900,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .clickable { onSelectEngineChoice(choice) }
                                        .testTag("monitor_engine_${choice.id}")
                                ) {
                                    Box(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                                        Text(
                                            text = choice.shortLabel,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isSelected) Slate900 else Color.White,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (settings.engineChoice.usesMlKitTranslation) {
                        Spacer(modifier = Modifier.height(4.dp))
                        LazyRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            items(listOf("English", "Korean", "Spanish", "Japanese", "Chinese", "French", "German")) { lang ->
                                val isSelected = settings.targetLanguage.equals(lang, ignoreCase = true)
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (isSelected) MintSuccess else Slate800,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .clickable { onTargetLanguageChange(lang) }
                                ) {
                                    Box(modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)) {
                                        Text(
                                            text = "→ $lang",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isSelected) Slate900 else Color.White
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    if (subtitleText.isNotBlank()) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            if (currentSub?.isTranslationActive == true && currentSub.translatedText.isNotBlank()) {
                                val isTranslated = currentSub.translatedText != currentSub.originalText ||
                                        uiState.overlaySettings.engineChoice == SubtitleEngineChoice.WHISPER_DIRECT
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = if (isTranslated) MintSuccess.copy(alpha = 0.2f) else AmberWarning.copy(alpha = 0.2f)
                                    ) {
                                        Text(
                                            text = if (isTranslated) "TRANSLATED (${currentSub.targetLanguage.uppercase()})" else "TRANSLATING (${currentSub.targetLanguage.uppercase()})...",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isTranslated) MintSuccess else AmberWarning,
                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                        )
                                    }
                                    if (uiState.overlaySettings.enableLocalAgreement && currentSub.displayFrozenPrefix.isNotBlank()) {
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = CyanPrimary.copy(alpha = 0.2f)
                                        ) {
                                            Text(
                                                text = "🔒 LOCAL AGREEMENT STABLE",
                                                fontSize = 8.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = CyanPrimary,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }

                                val annotatedTranslated = buildAnnotatedString {
                                    if (uiState.overlaySettings.enableLocalAgreement && currentSub.displayFrozenPrefix.isNotBlank()) {
                                        withStyle(SpanStyle(color = Color.White, fontWeight = FontWeight.Bold)) {
                                            append(currentSub.displayFrozenPrefix)
                                        }
                                        if (currentSub.displayUnconfirmedTail.isNotBlank()) {
                                            append(" ")
                                        }
                                    }
                                    if (currentSub.displayUnconfirmedTail.isNotBlank()) {
                                        withStyle(
                                            SpanStyle(
                                                color = if (currentSub.displayFrozenPrefix.isNotBlank()) CyanPrimary else Color.White,
                                                fontWeight = if (currentSub.displayFrozenPrefix.isNotBlank()) FontWeight.Medium else FontWeight.Bold,
                                                fontStyle = if (currentSub.displayFrozenPrefix.isNotBlank()) FontStyle.Italic else FontStyle.Normal
                                            )
                                        ) {
                                            append(currentSub.displayUnconfirmedTail)
                                        }
                                    } else if (currentSub.displayFrozenPrefix.isBlank()) {
                                        withStyle(SpanStyle(color = Color.White, fontWeight = FontWeight.Bold)) {
                                            append(currentSub.translatedText)
                                        }
                                    }
                                }

                                Text(
                                    text = annotatedTranslated,
                                    fontSize = 16.sp,
                                    lineHeight = 22.sp
                                )
                                if (currentSub.originalText.isNotBlank() && currentSub.originalText != currentSub.translatedText) {
                                    Text(
                                        text = "Spoken: ${currentSub.originalText}",
                                        color = Slate400,
                                        fontSize = 12.sp,
                                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                                        lineHeight = 16.sp
                                    )
                                }
                            } else {
                                if (uiState.overlaySettings.enableLocalAgreement && currentSub?.displayFrozenPrefix?.isNotBlank() == true) {
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = CyanPrimary.copy(alpha = 0.2f)
                                    ) {
                                        Text(
                                            text = "🔒 LOCAL AGREEMENT STABLE",
                                            fontSize = 8.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = CyanPrimary,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                        )
                                    }
                                }

                                val annotatedOriginal = buildAnnotatedString {
                                    if (uiState.overlaySettings.enableLocalAgreement && currentSub?.displayFrozenPrefix?.isNotBlank() == true) {
                                        withStyle(SpanStyle(color = Color.White, fontWeight = FontWeight.Bold)) {
                                            append(currentSub.displayFrozenPrefix)
                                        }
                                        if (currentSub.displayUnconfirmedTail.isNotBlank()) {
                                            append(" ")
                                        }
                                    }
                                    if (currentSub?.displayUnconfirmedTail?.isNotBlank() == true) {
                                        withStyle(
                                            SpanStyle(
                                                color = if (currentSub.displayFrozenPrefix.isNotBlank()) CyanPrimary else Color.White,
                                                fontWeight = if (currentSub.displayFrozenPrefix.isNotBlank()) FontWeight.Medium else FontWeight.SemiBold,
                                                fontStyle = if (currentSub.displayFrozenPrefix.isNotBlank()) FontStyle.Italic else FontStyle.Normal
                                            )
                                        ) {
                                            append(currentSub.displayUnconfirmedTail)
                                        }
                                    } else if (currentSub?.displayFrozenPrefix?.isBlank() != false) {
                                        withStyle(SpanStyle(color = Color.White, fontWeight = FontWeight.Bold)) {
                                            append(subtitleText)
                                        }
                                    }
                                }

                                Text(
                                    text = annotatedOriginal,
                                    fontSize = 16.sp,
                                    lineHeight = 21.sp
                                )
                            }
                        }
                    } else if (isRecording) {
                        if (!isCurrentModelReady) {
                            Column {
                                Text(
                                    text = "Capturing audio, but no AI model is active.",
                                    color = AmberWarning,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Go to the 'Models' tab to download Whisper Tiny (42 MB).",
                                    color = Slate400,
                                    fontSize = 11.sp
                                )
                            }
                        } else {
                            Text(
                                text = "Model active. Listening for speech in playback/mic...",
                                color = MintSuccess.copy(alpha = 0.9f),
                                fontSize = 13.sp,
                                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                            )
                        }
                    } else {
                        Text(
                            text = "Start capture to transcribe live speech.",
                            color = Slate400.copy(alpha = 0.7f),
                            fontSize = 13.sp,
                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                        )
                    }
                }
            }
        }

        // 3. Audio Meter & Spectrum Card
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Slate800),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            val statusColor by animateColorAsState(
                                targetValue = when (diag.state) {
                                    CaptureState.RECORDING -> MintSuccess
                                    CaptureState.SILENCE_DETECTED -> Slate400
                                    CaptureState.RESTRICTED_SOURCE -> AmberWarning
                                    CaptureState.ERROR -> RoseError
                                    else -> Slate400
                                },
                                label = "status_color"
                            )
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(statusColor)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = when (diag.state) {
                                    CaptureState.RECORDING -> "RECORDING"
                                    CaptureState.SILENCE_DETECTED -> "SILENCE"
                                    CaptureState.RESTRICTED_SOURCE -> "MUTED"
                                    CaptureState.INITIALIZING -> "INIT..."
                                    CaptureState.ERROR -> "ERROR"
                                    else -> "STANDBY"
                                },
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = statusColor,
                                maxLines = 1
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Slate900
                        ) {
                            Text(
                                text = String.format(java.util.Locale.US, "%.1f dBFS", diag.currentLevel.decibels),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = CyanPrimary,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Audio Level Meter (VU Bar)
                    val animatedRms by animateFloatAsState(
                        targetValue = if (isRecording) diag.currentLevel.rms else 0f,
                        animationSpec = tween(durationMillis = 80),
                        label = "rms_meter"
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "AUDIO ENERGY",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = Slate400
                        )
                        Text(
                            text = "${(animatedRms * 100).toInt()}%",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (animatedRms > 0.05f) MintSuccess else Slate400
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(12.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Slate900)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(animatedRms.coerceIn(0f, 1f))
                                .height(12.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(CyanPrimary, VioletAccent, if (animatedRms > 0.8f) RoseError else MintSuccess)
                                    )
                                )
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Waveform Canvas
                    Text(
                        text = "REAL-TIME SPECTRUM (16 kHz MONO)",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = Slate400
                    )
                    Spacer(modifier = Modifier.height(4.dp))

                    WaveformCanvas(waveform = uiState.liveWaveform)

                    Spacer(modifier = Modifier.height(10.dp))

                    // Technical Diagnostics Grid (Adapted for 340dp screen)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        DiagPill(label = "Format", value = "16kHz PCM", modifier = Modifier.weight(1f))
                        DiagPill(label = "Buffer", value = "${diag.bufferAvailableSamples / 1000}k", modifier = Modifier.weight(1f))
                        DiagPill(label = "Captured", value = "${diag.samplesCaptured / 1000}k", modifier = Modifier.weight(1f))
                    }
                }
            }
        }

        // 4. Primary Capture Action Button
        item {
            if (isRecording) {
                Button(
                    onClick = onStopService,
                    colors = ButtonDefaults.buttonColors(containerColor = RoseError),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("stop_capture_button")
                ) {
                    Icon(imageVector = Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Stop Subtitle Capture", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            } else {
                Button(
                    onClick = onStartService,
                    colors = ButtonDefaults.buttonColors(containerColor = CyanPrimary),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("start_capture_button")
                ) {
                    Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, tint = Slate900, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (uiState.selectedCaptureMode == CaptureMode.SYSTEM_PLAYBACK) {
                            "Start System Audio Capture"
                        } else {
                            "Start Microphone Capture"
                        },
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Slate900
                    )
                }
            }
        }

        // 5. Zero-Playback Pure Capture Notice
        item {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Slate900.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = CyanPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Pure audio capture mode. This app never plays back or generates audio. Captures live sound only.",
                        color = Slate400,
                        fontSize = 11.sp,
                        lineHeight = 15.sp
                    )
                }
            }
        }
    }
}

@Composable
fun SourceOptionCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    testTag: String
) {
    val borderColor = if (isSelected) CyanPrimary else Slate700
    val bgColor = if (isSelected) CyanPrimary.copy(alpha = 0.12f) else Slate900

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .border(1.5.dp, borderColor, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(10.dp)
            .testTag(testTag)
    ) {
        Column {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isSelected) CyanPrimary else Slate400,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = title,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                color = if (isSelected) Color.White else Slate400,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                fontSize = 10.sp,
                color = Slate400,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun DiagPill(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = Slate900,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 5.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = label, fontSize = 9.sp, color = Slate400, maxLines = 1)
            Text(text = value, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1)
        }
    }
}

@Composable
fun WaveformCanvas(waveform: List<Float>) {
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Slate900)
            .padding(horizontal = 6.dp, vertical = 3.dp)
    ) {
        val barCount = waveform.size
        if (barCount == 0) return@Canvas

        val availableWidth = size.width
        val barWidth = (availableWidth / barCount) * 0.65f
        val gap = (availableWidth / barCount) * 0.35f
        val centerY = size.height / 2f

        for (i in 0 until barCount) {
            val amplitude = waveform[i].coerceIn(0.05f, 1.0f)
            val barHeight = (size.height * 0.9f) * amplitude
            val left = i * (barWidth + gap)
            val top = centerY - (barHeight / 2f)

            drawRoundRect(
                brush = Brush.verticalGradient(
                    listOf(CyanPrimary, VioletAccent)
                ),
                topLeft = Offset(left, top),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(3f, 3f)
            )
        }
    }
}
