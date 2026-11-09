package com.example.sublive.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.border
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import com.example.sublive.overlay.FloatingSubtitleBox
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CyanPrimary
import com.example.ui.theme.MintSuccess
import com.example.ui.theme.Slate400
import com.example.ui.theme.Slate700
import com.example.ui.theme.Slate800
import com.example.ui.theme.Slate900
import com.example.ui.theme.VioletAccent

import com.example.sublive.overlay.SubtitleEngineChoice

@Composable
fun SubtitleOverlayTab(
    uiState: UiDashboardState,
    hasOverlayPermission: Boolean,
    onRequestOverlayPermission: () -> Unit,
    onToggleOverlayService: () -> Unit,
    onFontSizeChange: (Int) -> Unit,
    onOpacityChange: (Float) -> Unit,
    onTogglePauseOverlay: () -> Unit = {},
    onSelectEngineChoice: (SubtitleEngineChoice) -> Unit = {},
    onSourceLanguageChange: (String) -> Unit = {},
    onTargetLanguageChange: (String) -> Unit = {},
    onToggleTurnTakingWait: (Boolean) -> Unit = {},
    onTurnTakingWaitDurationChange: (Long) -> Unit = {},
    onToggleLocalAgreement: (Boolean) -> Unit = {}
) {
    val settings = uiState.overlaySettings
    val subState = uiState.subtitleState

    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Overlay Activation Header
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Slate800),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "FLOATING OVERLAY",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Slate400,
                                letterSpacing = 0.8.sp
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = if (hasOverlayPermission) "Overlay Enabled" else "Permission Needed",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (hasOverlayPermission) MintSuccess else AmberWarning,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        if (!hasOverlayPermission) {
                            Button(
                                onClick = onRequestOverlayPermission,
                                colors = ButtonDefaults.buttonColors(containerColor = AmberWarning),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.testTag("grant_overlay_permission_button")
                            ) {
                                Text("Grant", color = Slate900, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        } else {
                            Button(
                                onClick = onToggleOverlayService,
                                colors = ButtonDefaults.buttonColors(containerColor = CyanPrimary),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.testTag("toggle_overlay_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.OpenInNew,
                                    contentDescription = null,
                                    tint = Slate900,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Toggle", color = Slate900, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }

        // Live Preview of Overlay Box
        item {
            Text(
                text = "OVERLAY LIVE PREVIEW",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Slate400,
                letterSpacing = 0.8.sp,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Slate900,
                modifier = Modifier.fillMaxWidth()
            ) {
                FloatingSubtitleBox(
                    subtitleState = subState,
                    settings = settings,
                    onDrag = { _, _ -> },
                    onTogglePause = onTogglePauseOverlay,
                    onClose = { }
                )
            }
        }

        // Customization Controls Card
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Slate800),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = "APPEARANCE & CONTROLS",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Slate400,
                        letterSpacing = 0.8.sp
                    )

                    // Font Size Slider
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(imageVector = Icons.Default.FormatSize, contentDescription = null, tint = CyanPrimary, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Font Size", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            }
                            Text("${settings.fontSizeSp} sp", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = CyanPrimary)
                        }
                        Slider(
                            value = settings.fontSizeSp.toFloat(),
                            onValueChange = { onFontSizeChange(it.toInt()) },
                            valueRange = 14f..26f,
                            steps = 11,
                            colors = SliderDefaults.colors(
                                thumbColor = CyanPrimary,
                                activeTrackColor = CyanPrimary,
                                inactiveTrackColor = Slate700
                            ),
                            modifier = Modifier.testTag("font_size_slider")
                        )
                    }

                    // Background Opacity Slider
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(imageVector = Icons.Default.Layers, contentDescription = null, tint = VioletAccent, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Opacity", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            }
                            Text("${(settings.backgroundOpacity * 100).toInt()}%", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = VioletAccent)
                        }
                        Slider(
                            value = settings.backgroundOpacity,
                            onValueChange = onOpacityChange,
                            valueRange = 0.3f..0.98f,
                            colors = SliderDefaults.colors(
                                thumbColor = VioletAccent,
                                activeTrackColor = VioletAccent,
                                inactiveTrackColor = Slate700
                            ),
                            modifier = Modifier.testTag("opacity_slider")
                        )
                    }

                    // Mutually Exclusive Translation & Subtitle Engine Selector
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "TRANSLATION & SUBTITLE ENGINE",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Slate400,
                                letterSpacing = 0.8.sp
                            )
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = CyanPrimary.copy(alpha = 0.15f)
                            ) {
                                Text(
                                    text = settings.engineChoice.shortLabel,
                                    color = CyanPrimary,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        // Engine Option 1: Whisper Direct (Audio -> English, Zero ML Kit)
                        EngineOptionCard(
                            title = "Whisper Direct AI (Audio → English)",
                            subtitle = "Direct translation to English using Whisper neural model. Zero ML Kit.",
                            badge = "Pure Whisper",
                            isSelected = settings.engineChoice == SubtitleEngineChoice.WHISPER_DIRECT,
                            accentColor = CyanPrimary,
                            onClick = { onSelectEngineChoice(SubtitleEngineChoice.WHISPER_DIRECT) },
                            testTag = "engine_whisper_direct"
                        )

                        // Engine Option 2: Whisper ASR + Android ML Kit
                        EngineOptionCard(
                            title = "Whisper ASR + Android ML Kit",
                            subtitle = "Whisper transcribes spoken audio, then Android ML Kit translates to target language.",
                            badge = "Whisper + ML Kit",
                            isSelected = settings.engineChoice == SubtitleEngineChoice.WHISPER_MLKIT,
                            accentColor = MintSuccess,
                            onClick = { onSelectEngineChoice(SubtitleEngineChoice.WHISPER_MLKIT) },
                            testTag = "engine_whisper_mlkit"
                        )

                        // Engine Option 3: Vosk ASR + Android ML Kit
                        EngineOptionCard(
                            title = "Vosk ASR + Android ML Kit",
                            subtitle = "Offline Vosk ASR engine transcribes audio, then Android ML Kit translates to target language.",
                            badge = "Vosk + ML Kit",
                            isSelected = settings.engineChoice == SubtitleEngineChoice.VOSK_MLKIT,
                            accentColor = VioletAccent,
                            onClick = { onSelectEngineChoice(SubtitleEngineChoice.VOSK_MLKIT) },
                            testTag = "engine_vosk_mlkit"
                        )

                        // Engine Option 4: SenseVoice (Sherpa-ONNX) + Google ML Kit
                        EngineOptionCard(
                            title = "SenseVoice (Sherpa-ONNX) + Google ML Kit",
                            subtitle = "Multilingual SenseVoice via Sherpa-ONNX transcribes audio, then Google ML Kit translates to target language.",
                            badge = "SenseVoice + ML Kit",
                            isSelected = settings.engineChoice == SubtitleEngineChoice.SENSEVOICE_MLKIT,
                            accentColor = AmberWarning,
                            onClick = { onSelectEngineChoice(SubtitleEngineChoice.SENSEVOICE_MLKIT) },
                            testTag = "engine_sensevoice_mlkit"
                        )

                        // Engine Option 5: Silero VAD + SenseVoice-Small + LFM3 (4-bit)
                        EngineOptionCard(
                            title = "Silero VAD + SenseVoice + LFM3 (4-bit)",
                            subtitle = "Silero VAD filters noise, SenseVoice-Small ONNX transcribes, and Liquid AI LFM3 4-bit translates into target language.",
                            badge = "Silero + SenseVoice + LFM3",
                            isSelected = settings.engineChoice == SubtitleEngineChoice.SILERO_SENSEVOICE_LFM3,
                            accentColor = Color(0xFFF43F5E),
                            onClick = { onSelectEngineChoice(SubtitleEngineChoice.SILERO_SENSEVOICE_LFM3) },
                            testTag = "engine_silero_sensevoice_lfm3"
                        )

                        // Engine Option 6: LFM3-Omni-2B (LiteRT Standalone)
                        EngineOptionCard(
                            title = "LFM3-Omni-2B (LiteRT Standalone)",
                            subtitle = "Liquid AI LFM3-Omni 2B model on Google LiteRT directly translates & generates subtitles end-to-end. Zero ML Kit.",
                            badge = "LFM3 Standalone",
                            isSelected = settings.engineChoice == SubtitleEngineChoice.LFM3_OMNI_LITERT,
                            accentColor = Color(0xFF38BDF8),
                            onClick = { onSelectEngineChoice(SubtitleEngineChoice.LFM3_OMNI_LITERT) },
                            testTag = "engine_lfm3_omni_litert"
                        )

                        // Engine Option 6: Whisper Original (No Translation)
                        EngineOptionCard(
                            title = "Whisper Original (No Translation)",
                            subtitle = "Transcribes speech in original spoken language without translation.",
                            badge = "Transcription Only",
                            isSelected = settings.engineChoice == SubtitleEngineChoice.WHISPER_ORIGINAL,
                            accentColor = Slate400,
                            onClick = { onSelectEngineChoice(SubtitleEngineChoice.WHISPER_ORIGINAL) },
                            testTag = "engine_whisper_original"
                        )
                    }

                    // Turn-Taking Wait Configuration (Specialized for Silero VAD + SenseVoice + LFM3)
                    if (settings.engineChoice == SubtitleEngineChoice.SILERO_SENSEVOICE_LFM3) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFFF43F5E).copy(alpha = 0.08f))
                                .border(1.dp, Color(0xFFF43F5E).copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = "TURN-TAKING WAIT",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFFF43F5E),
                                            letterSpacing = 0.8.sp
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = Color(0xFFF43F5E).copy(alpha = 0.2f)
                                        ) {
                                            Text(
                                                text = if (settings.enableTurnTakingWait) "ACTIVE (${settings.turnTakingWaitMs}ms)" else "OFF",
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFFF43F5E),
                                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "Pause detection threshold before LFM3 translation",
                                        fontSize = 11.sp,
                                        color = Slate400
                                    )
                                }
                                Switch(
                                    checked = settings.enableTurnTakingWait,
                                    onCheckedChange = onToggleTurnTakingWait,
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color(0xFFF43F5E),
                                        checkedTrackColor = Color(0xFFF43F5E).copy(alpha = 0.4f),
                                        uncheckedThumbColor = Slate400,
                                        uncheckedTrackColor = Slate700
                                    ),
                                    modifier = Modifier.testTag("toggle_turn_taking_wait")
                                )
                            }

                            if (settings.enableTurnTakingWait) {
                                Column {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("Endpoint Wait Delay", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                        Text("${settings.turnTakingWaitMs} ms", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFFF43F5E))
                                    }
                                    Slider(
                                        value = settings.turnTakingWaitMs.toFloat(),
                                        onValueChange = { onTurnTakingWaitDurationChange(it.toLong()) },
                                        valueRange = 400f..1600f,
                                        steps = 11,
                                        colors = SliderDefaults.colors(
                                            thumbColor = Color(0xFFF43F5E),
                                            activeTrackColor = Color(0xFFF43F5E),
                                            inactiveTrackColor = Slate700
                                        ),
                                        modifier = Modifier.testTag("turn_taking_wait_slider")
                                    )
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        listOf(500L to "500ms (Fast)", 800L to "800ms (Standard)", 1200L to "1200ms (Relaxed)").forEach { (ms, label) ->
                                            val isSelected = settings.turnTakingWaitMs == ms
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = if (isSelected) Color(0xFFF43F5E) else Slate800,
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .clickable { onTurnTakingWaitDurationChange(ms) }
                                            ) {
                                                Text(
                                                    text = label,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (isSelected) Color.White else Slate400,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                                                )
                                            }
                                        }
                                    }
                                }

                                Text(
                                    text = "Silero VAD monitors human speech continuously. When a pause occurs, SubLive waits ${settings.turnTakingWaitMs}ms to ensure your sentence is complete before dispatching to LFM3 4-bit neural translation, preventing partial cut-offs.",
                                    fontSize = 10.sp,
                                    color = Slate400,
                                    lineHeight = 14.sp
                                )
                            }
                        }
                    }

                    // Source & Target Language Selectors (Visible when translation is active)
                    if (settings.isTranslating && settings.engineChoice != SubtitleEngineChoice.WHISPER_DIRECT) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(Slate900)
                                .padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "SPOKEN AUDIO SOURCE LANGUAGE",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Slate400
                            )
                            LazyRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                items(listOf("Auto Detect", "English", "Korean", "Spanish", "Japanese", "Chinese", "French", "German")) { lang ->
                                    val isSelected = settings.sourceLanguage.equals(lang, ignoreCase = true)
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (isSelected) VioletAccent else Slate800,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable { onSourceLanguageChange(lang) }
                                    ) {
                                        Box(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = lang,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isSelected) Slate900 else Color.White,
                                                maxLines = 1
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(2.dp))

                            Text(
                                text = "TRANSLATE TARGET SUBTITLE LANGUAGE",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Slate400
                            )
                            LazyRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                items(listOf("English", "Korean", "Spanish", "Japanese", "Chinese", "French", "German")) { lang ->
                                    val isSelected = settings.targetLanguage.equals(lang, ignoreCase = true)
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (isSelected) MintSuccess else Slate800,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable { onTargetLanguageChange(lang) }
                                    ) {
                                        Box(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = lang,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isSelected) Slate900 else Color.White,
                                                maxLines = 1
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Local Agreement Stability Protocol Configuration
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(CyanPrimary.copy(alpha = 0.08f))
                            .border(1.dp, CyanPrimary.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "LOCAL AGREEMENT POLICY",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = CyanPrimary,
                                        letterSpacing = 0.8.sp
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = CyanPrimary.copy(alpha = 0.2f)
                                    ) {
                                        Text(
                                            text = if (settings.enableLocalAgreement) "STABILITY PROTOCOL ACTIVE" else "OFF",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = CyanPrimary,
                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Append-only stabilization; freezes words upon mathematical confirmation",
                                    fontSize = 11.sp,
                                    color = Slate400
                                )
                            }
                            Switch(
                                checked = settings.enableLocalAgreement,
                                onCheckedChange = onToggleLocalAgreement,
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = CyanPrimary,
                                    checkedTrackColor = CyanPrimary.copy(alpha = 0.4f),
                                    uncheckedThumbColor = Slate400,
                                    uncheckedTrackColor = Slate700
                                ),
                                modifier = Modifier.testTag("toggle_local_agreement")
                            )
                        }

                        Text(
                            text = "Enforces a strict mathematical Stability Protocol: words matching across consecutive inference windows (Local Agreement N>=2) are permanently frozen into an immutable prefix. Only the trailing words at the very end of the sentence mutate, eliminating subtitle flickering.",
                            fontSize = 10.sp,
                            color = Slate400,
                            lineHeight = 14.sp
                        )
                    }

                    // Quick Pause Overlay Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Pause Live Subtitles", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            Text("Freeze overlay temporarily", fontSize = 11.sp, color = Slate400)
                        }
                        Button(
                            onClick = onTogglePauseOverlay,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (settings.isPaused) AmberWarning else Slate700
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.testTag("pause_overlay_button")
                        ) {
                            Icon(
                                imageVector = if (settings.isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(if (settings.isPaused) "Resume" else "Pause", fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        // Subtitle History
        item {
            Text(
                text = "RECENT TRANSCRIBED SUBTITLES (${subState.finalizedHistory.size})",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Slate400,
                letterSpacing = 0.8.sp,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }

        if (subState.finalizedHistory.isEmpty()) {
            item {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Slate800,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(modifier = Modifier.padding(20.dp), contentAlignment = Alignment.Center) {
                        Text(
                            text = "No transcribed subtitles yet. Start playback or speak into the microphone.",
                            color = Slate400,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        } else {
            items(subState.finalizedHistory.reversed()) { item ->
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Slate800),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = item.displayText,
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun EngineOptionCard(
    title: String,
    subtitle: String,
    badge: String,
    isSelected: Boolean,
    accentColor: Color,
    onClick: () -> Unit,
    testTag: String
) {
    val borderColor = if (isSelected) accentColor else Slate700
    val bgColor = if (isSelected) accentColor.copy(alpha = 0.12f) else Slate900

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = bgColor,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .testTag(testTag),
        border = androidx.compose.foundation.BorderStroke(if (isSelected) 1.5.dp else 1.dp, borderColor)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isSelected) Color.White else Slate400,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.width(6.dp))

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (isSelected) accentColor.copy(alpha = 0.25f) else Slate800
                ) {
                    Text(
                        text = if (isSelected) "ACTIVE" else badge,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isSelected) accentColor else Slate400,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(3.dp))

            Text(
                text = subtitle,
                fontSize = 10.sp,
                color = if (isSelected) Slate400 else Slate400.copy(alpha = 0.8f),
                lineHeight = 14.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
