package com.example.sublive.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
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
import com.example.sublive.model.ModelDownloadState
import com.example.sublive.model.ModelStatus
import com.example.sublive.model.ModelTier
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
import com.example.sublive.vosk.VoskDownloadState
import com.example.sublive.sherpa.SenseVoiceDownloadState
import com.example.sublive.vad.SileroDownloadState
import com.example.sublive.lfm.Lfm3DownloadState
import com.example.sublive.lfm.Lfm3TextDownloadState

@Composable
fun ModelCenterTab(
    uiState: UiDashboardState,
    onDownloadModel: (ModelTier) -> Unit,
    onCancelDownload: () -> Unit,
    onSelectModel: (ModelTier) -> Unit,
    onDeleteModel: (ModelTier) -> Unit,
    onSelectBackend: (com.example.sublive.engine.AccelerationBackend) -> Unit = {},
    onDownloadVosk: () -> Unit = {},
    onCancelVoskDownload: () -> Unit = {},
    onDeleteVosk: () -> Unit = {},
    onSelectVoskEngine: () -> Unit = {},
    onDownloadSenseVoice: () -> Unit = {},
    onCancelSenseVoiceDownload: () -> Unit = {},
    onDeleteSenseVoice: () -> Unit = {},
    onSelectSenseVoiceEngine: () -> Unit = {},
    onDownloadLfm3: () -> Unit = {},
    onCancelLfm3Download: () -> Unit = {},
    onDeleteLfm3: () -> Unit = {},
    onSelectLfm3Engine: () -> Unit = {},
    onDownloadSileroVad: () -> Unit = {},
    onCancelSileroVad: () -> Unit = {},
    onDeleteSileroVad: () -> Unit = {},
    onDownloadLfm3Text: () -> Unit = {},
    onCancelLfm3Text: () -> Unit = {},
    onDeleteLfm3Text: () -> Unit = {},
    onSelectSileroPipeline: () -> Unit = {}
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Vosk ASR Engine Section Header & Card
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Slate800),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        if (uiState.overlaySettings.engineChoice == SubtitleEngineChoice.VOSK_MLKIT) 1.5.dp else 1.dp,
                        if (uiState.overlaySettings.engineChoice == SubtitleEngineChoice.VOSK_MLKIT) VioletAccent else Slate700,
                        RoundedCornerShape(16.dp)
                    )
                    .testTag("model_card_vosk")
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Vosk Small US-EN (Offline ASR)",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        if (uiState.overlaySettings.engineChoice == SubtitleEngineChoice.VOSK_MLKIT) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = VioletAccent.copy(alpha = 0.25f)
                            ) {
                                Text(
                                    text = "ACTIVE ENGINE",
                                    color = VioletAccent,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Lightweight offline Kaldi speech recognition",
                            fontSize = 11.sp,
                            color = VioletAccent,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        when (val state = uiState.voskDownloadState) {
                            is VoskDownloadState.Ready, is VoskDownloadState.Downloaded -> {
                                Surface(shape = RoundedCornerShape(6.dp), color = MintSuccess.copy(alpha = 0.2f)) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(modifier = Modifier.size(5.dp).clip(CircleShape).background(MintSuccess))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Ready", color = MintSuccess, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                            is VoskDownloadState.Downloading -> {
                                Surface(shape = RoundedCornerShape(6.dp), color = AmberWarning.copy(alpha = 0.2f)) {
                                    Text("Downloading", color = AmberWarning, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                }
                            }
                            else -> {
                                Surface(shape = RoundedCornerShape(6.dp), color = Slate900) {
                                    Text("Not Downloaded", color = Slate400, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Vosk offline ASR engine transcribes audio directly on CPU. Paired with Android ML Kit, it provides completely offline speech recognition and translation.",
                        fontSize = 11.sp,
                        color = Slate400,
                        lineHeight = 15.sp
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Specs Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Slate900)
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        SpecItem(icon = Icons.Default.Storage, label = "Size", value = "40 MB")
                        SpecItem(icon = Icons.Default.Speed, label = "Latency", value = "~150 ms")
                        SpecItem(icon = Icons.Default.Memory, label = "RAM", value = "~100 MB")
                    }

                    // Download Progress Bar if in progress
                    if (uiState.voskDownloadState is VoskDownloadState.Downloading) {
                        val state = uiState.voskDownloadState
                        Spacer(modifier = Modifier.height(8.dp))
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "${(state.bytesDownloaded / (1024 * 1024))} / ${(state.totalBytes / (1024 * 1024))} MB",
                                    fontSize = 10.sp,
                                    color = Slate400
                                )
                                Text(
                                    text = "${(state.progress * 100).toInt()}%",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AmberWarning
                                )
                            }
                            Spacer(modifier = Modifier.height(3.dp))
                            LinearProgressIndicator(
                                progress = { state.progress },
                                modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                                color = AmberWarning,
                                trackColor = Slate900
                            )
                        }
                    }

                    // Error display if failed
                    if (uiState.voskDownloadState is VoskDownloadState.Error) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = (uiState.voskDownloadState as VoskDownloadState.Error).message,
                            color = RoseError,
                            fontSize = 11.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Action Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        when (uiState.voskDownloadState) {
                            is VoskDownloadState.Downloading -> {
                                OutlinedButton(
                                    onClick = onCancelVoskDownload,
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Cancel Download", color = RoseError, fontSize = 12.sp)
                                }
                            }

                            is VoskDownloadState.Downloaded, is VoskDownloadState.Ready -> {
                                if (uiState.overlaySettings.engineChoice != SubtitleEngineChoice.VOSK_MLKIT) {
                                    Button(
                                        onClick = onSelectVoskEngine,
                                        colors = ButtonDefaults.buttonColors(containerColor = VioletAccent),
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                                        modifier = Modifier.weight(1f).testTag("select_vosk_engine")
                                    ) {
                                        Text("Select Vosk + ML Kit", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    }
                                } else {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MintSuccess.copy(alpha = 0.15f),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Box(modifier = Modifier.padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                                            Text("Active Engine (Vosk + ML Kit)", color = MintSuccess, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }

                                IconButton(
                                    onClick = onDeleteVosk,
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete Vosk model", tint = Slate400, modifier = Modifier.size(18.dp))
                                }
                            }

                            else -> {
                                Button(
                                    onClick = onDownloadVosk,
                                    colors = ButtonDefaults.buttonColors(containerColor = VioletAccent),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                                    modifier = Modifier.weight(1f).testTag("download_vosk_model")
                                ) {
                                    Icon(imageVector = Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(15.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Download Vosk (40 MB)", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }
            }
        }

        // SenseVoice (Sherpa-ONNX) Model Card
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Slate800),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        if (uiState.overlaySettings.engineChoice == SubtitleEngineChoice.SENSEVOICE_MLKIT) 1.5.dp else 1.dp,
                        if (uiState.overlaySettings.engineChoice == SubtitleEngineChoice.SENSEVOICE_MLKIT) AmberWarning else Slate700,
                        RoundedCornerShape(16.dp)
                    )
                    .testTag("model_card_sensevoice")
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "SenseVoice (Sherpa-ONNX)",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        if (uiState.overlaySettings.engineChoice == SubtitleEngineChoice.SENSEVOICE_MLKIT) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = AmberWarning.copy(alpha = 0.25f)
                            ) {
                                Text(
                                    text = "ACTIVE ENGINE",
                                    color = AmberWarning,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "FunAudioLLM multilingual int8 quantized model",
                            fontSize = 11.sp,
                            color = AmberWarning,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        when (val state = uiState.senseVoiceDownloadState) {
                            is SenseVoiceDownloadState.Ready, is SenseVoiceDownloadState.Downloaded -> {
                                Surface(shape = RoundedCornerShape(6.dp), color = MintSuccess.copy(alpha = 0.2f)) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(modifier = Modifier.size(5.dp).clip(CircleShape).background(MintSuccess))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Ready", color = MintSuccess, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                            is SenseVoiceDownloadState.Downloading -> {
                                Surface(shape = RoundedCornerShape(6.dp), color = AmberWarning.copy(alpha = 0.2f)) {
                                    Text("Downloading", color = AmberWarning, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                }
                            }
                            else -> {
                                Surface(shape = RoundedCornerShape(6.dp), color = Slate900) {
                                    Text("Not Downloaded", color = Slate400, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "High-accuracy multilingual speech model supporting English, Chinese, Japanese, Korean & Cantonese. Decoded locally using Sherpa-ONNX runtime and translated with Google ML Kit.",
                        fontSize = 11.sp,
                        color = Slate400,
                        lineHeight = 15.sp
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Specs Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Slate900)
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        SpecItem(icon = Icons.Default.Storage, label = "Size", value = "~155 MB")
                        SpecItem(icon = Icons.Default.Speed, label = "Latency", value = "~90 ms")
                        SpecItem(icon = Icons.Default.Memory, label = "RAM", value = "~200 MB")
                    }

                    // Download Progress Bar if in progress
                    if (uiState.senseVoiceDownloadState is SenseVoiceDownloadState.Downloading) {
                        val state = uiState.senseVoiceDownloadState
                        Spacer(modifier = Modifier.height(8.dp))
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "${(state.bytesDownloaded / (1024 * 1024))} / ${(state.totalBytes / (1024 * 1024))} MB",
                                    fontSize = 10.sp,
                                    color = Slate400
                                )
                                Text(
                                    text = "${(state.progress * 100).toInt()}%",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AmberWarning
                                )
                            }
                            Spacer(modifier = Modifier.height(3.dp))
                            LinearProgressIndicator(
                                progress = { state.progress },
                                modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                                color = AmberWarning,
                                trackColor = Slate900
                            )
                        }
                    }

                    // Error display if failed
                    if (uiState.senseVoiceDownloadState is SenseVoiceDownloadState.Error) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = (uiState.senseVoiceDownloadState as SenseVoiceDownloadState.Error).message,
                            color = RoseError,
                            fontSize = 11.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Action Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        when (uiState.senseVoiceDownloadState) {
                            is SenseVoiceDownloadState.Downloading -> {
                                OutlinedButton(
                                    onClick = onCancelSenseVoiceDownload,
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Cancel Download", color = RoseError, fontSize = 12.sp)
                                }
                            }

                            is SenseVoiceDownloadState.Downloaded, is SenseVoiceDownloadState.Ready -> {
                                if (uiState.overlaySettings.engineChoice != SubtitleEngineChoice.SENSEVOICE_MLKIT) {
                                    Button(
                                        onClick = onSelectSenseVoiceEngine,
                                        colors = ButtonDefaults.buttonColors(containerColor = AmberWarning),
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                                        modifier = Modifier.weight(1f).testTag("select_sensevoice_engine")
                                    ) {
                                        Text("Select SenseVoice + ML Kit", color = Slate900, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    }
                                } else {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MintSuccess.copy(alpha = 0.15f),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Box(modifier = Modifier.padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                                            Text("Active Engine (SenseVoice + ML Kit)", color = MintSuccess, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }

                                IconButton(
                                    onClick = onDeleteSenseVoice,
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete SenseVoice model", tint = Slate400, modifier = Modifier.size(18.dp))
                                }
                            }

                            else -> {
                                Button(
                                    onClick = onDownloadSenseVoice,
                                    colors = ButtonDefaults.buttonColors(containerColor = AmberWarning),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                                    modifier = Modifier.weight(1f).testTag("download_sensevoice_model")
                                ) {
                                    Icon(imageVector = Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(15.dp), tint = Slate900)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Download SenseVoice (240 MB)", color = Slate900, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }

        // LFM3-Omni-2B (LiteRT Standalone) Model Card
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Slate800),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        if (uiState.overlaySettings.engineChoice == SubtitleEngineChoice.LFM3_OMNI_LITERT) 1.5.dp else 1.dp,
                        if (uiState.overlaySettings.engineChoice == SubtitleEngineChoice.LFM3_OMNI_LITERT) Color(0xFF38BDF8) else Slate700,
                        RoundedCornerShape(16.dp)
                    )
                    .testTag("model_card_lfm3")
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "LFM3-Omni-2B (LiteRT Standalone)",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        if (uiState.overlaySettings.engineChoice == SubtitleEngineChoice.LFM3_OMNI_LITERT) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color(0xFF38BDF8).copy(alpha = 0.25f)
                            ) {
                                Text(
                                    text = "ACTIVE ENGINE",
                                    color = Color(0xFF38BDF8),
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Liquid AI 2B Multimodal edge weights on Google LiteRT",
                            fontSize = 11.sp,
                            color = Color(0xFF38BDF8),
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        when (val state = uiState.lfm3DownloadState) {
                            is Lfm3DownloadState.Ready, is Lfm3DownloadState.Downloaded -> {
                                Surface(shape = RoundedCornerShape(6.dp), color = MintSuccess.copy(alpha = 0.2f)) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(6.dp)
                                                .clip(CircleShape)
                                                .background(MintSuccess)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("LiteRT Ready", color = MintSuccess, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                            is Lfm3DownloadState.Downloading -> {
                                Text(
                                    text = "${(state.progress * 100).toInt()}%",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF38BDF8)
                                )
                            }
                            else -> {
                                Surface(shape = RoundedCornerShape(6.dp), color = Slate900) {
                                    Text(
                                        text = "~200 MB",
                                        color = Slate400,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Capabilities tags
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Surface(shape = RoundedCornerShape(4.dp), color = Slate900) {
                            Text("Standalone (Zero ML Kit)", color = Color(0xFF38BDF8), fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp))
                        }
                        Surface(shape = RoundedCornerShape(4.dp), color = Slate900) {
                            Text("LiteRT / TFLite", color = Slate400, fontSize = 9.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp))
                        }
                        Surface(shape = RoundedCornerShape(4.dp), color = Slate900) {
                            Text("GPU / XNNPACK", color = MintSuccess, fontSize = 9.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp))
                        }
                    }

                    // Progress bar if downloading
                    if (uiState.lfm3DownloadState is Lfm3DownloadState.Downloading) {
                        val state = uiState.lfm3DownloadState as Lfm3DownloadState.Downloading
                        Spacer(modifier = Modifier.height(8.dp))
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Downloading LFM3-Omni LiteRT model...",
                                    fontSize = 10.sp,
                                    color = Slate400
                                )
                                Text(
                                    text = "${state.bytesDownloaded / (1024 * 1024)}MB / ${state.totalBytes / (1024 * 1024)}MB",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF38BDF8)
                                )
                            }
                            Spacer(modifier = Modifier.height(3.dp))
                            LinearProgressIndicator(
                                progress = { state.progress },
                                modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                                color = Color(0xFF38BDF8),
                                trackColor = Slate900
                            )
                        }
                    }

                    // Error display if failed
                    if (uiState.lfm3DownloadState is Lfm3DownloadState.Error) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = (uiState.lfm3DownloadState as Lfm3DownloadState.Error).message,
                            color = RoseError,
                            fontSize = 11.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Action Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        when (uiState.lfm3DownloadState) {
                            is Lfm3DownloadState.Downloading -> {
                                OutlinedButton(
                                    onClick = onCancelLfm3Download,
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Cancel Download", color = RoseError, fontSize = 12.sp)
                                }
                            }

                            is Lfm3DownloadState.Downloaded, is Lfm3DownloadState.Ready -> {
                                if (uiState.overlaySettings.engineChoice != SubtitleEngineChoice.LFM3_OMNI_LITERT) {
                                    Button(
                                        onClick = onSelectLfm3Engine,
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF38BDF8)),
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                                        modifier = Modifier.weight(1f).testTag("select_lfm3_engine")
                                    ) {
                                        Text("Select LFM3-Omni (LiteRT)", color = Slate900, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    }
                                } else {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MintSuccess.copy(alpha = 0.15f),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Box(modifier = Modifier.padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                                            Text("Active Engine (LFM3-Omni Standalone)", color = MintSuccess, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }

                                IconButton(
                                    onClick = onDeleteLfm3,
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete LFM3 model", tint = Slate400, modifier = Modifier.size(18.dp))
                                }
                            }

                            else -> {
                                Button(
                                    onClick = onDownloadLfm3,
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF38BDF8)),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                                    modifier = Modifier.weight(1f).testTag("download_lfm3_model")
                                ) {
                                    Icon(imageVector = Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(15.dp), tint = Slate900)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Download LFM3-Omni (200 MB)", color = Slate900, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Silero VAD + SenseVoice + LFM3 (4-bit) Pipeline Header Card
        item {
            val isSileroReady = uiState.sileroDownloadState is SileroDownloadState.Ready || uiState.sileroDownloadState is SileroDownloadState.Downloaded
            val isSenseVoiceReady = uiState.senseVoiceDownloadState is SenseVoiceDownloadState.Ready || uiState.senseVoiceDownloadState is SenseVoiceDownloadState.Downloaded
            val isLfm3TextReady = uiState.lfm3TextDownloadState is Lfm3TextDownloadState.Ready || uiState.lfm3TextDownloadState is Lfm3TextDownloadState.Downloaded
            val isPipelineReady = isSileroReady && isSenseVoiceReady && isLfm3TextReady
            val isPipelineActive = uiState.overlaySettings.engineChoice == SubtitleEngineChoice.SILERO_SENSEVOICE_LFM3

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Slate800),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        if (isPipelineActive) 2.dp else 1.dp,
                        if (isPipelineActive) Color(0xFFF43F5E) else Color(0xFFF43F5E).copy(alpha = 0.5f),
                        RoundedCornerShape(16.dp)
                    )
                    .testTag("model_card_silero_pipeline")
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Silero + SenseVoice + LFM3",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        if (isPipelineActive) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color(0xFFF43F5E).copy(alpha = 0.25f)
                            ) {
                                Text(
                                    text = "ACTIVE ENGINE",
                                    color = Color(0xFFF43F5E),
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Composite 3-Tier Pipeline: ONNX VAD + Multilingual ASR + 4-bit Neural Translation",
                        fontSize = 11.sp,
                        color = Color(0xFFF43F5E),
                        fontWeight = FontWeight.Medium
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "High-precision architecture: Silero VAD filters out noise and silence, SenseVoice decodes multi-language speech, and LFM3 4-bit neural LLM translates contextually into your target language.",
                        fontSize = 11.sp,
                        color = Slate400,
                        lineHeight = 15.sp
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // 3-Pillar Component Status Checklist
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Slate900)
                            .padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("1. Silero VAD (ONNX, ~643 KB)", fontSize = 11.sp, color = Color.White)
                            Text(
                                if (isSileroReady) "Ready" else "Missing",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSileroReady) MintSuccess else AmberWarning
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("2. SenseVoice-Small (~240 MB)", fontSize = 11.sp, color = Color.White)
                            Text(
                                if (isSenseVoiceReady) "Ready" else "Missing",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSenseVoiceReady) MintSuccess else AmberWarning
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("3. LFM3-d1-2B 4-bit (~1.15 GB)", fontSize = 11.sp, color = Color.White)
                            Text(
                                if (isLfm3TextReady) "Ready" else "Missing",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isLfm3TextReady) MintSuccess else AmberWarning
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Pipeline Selection Button
                    if (!isPipelineActive) {
                        Button(
                            onClick = onSelectSileroPipeline,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF43F5E)),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("select_silero_pipeline_engine")
                        ) {
                            Text("Select Silero + SenseVoice + LFM3 Pipeline", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    } else {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MintSuccess.copy(alpha = 0.15f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Box(modifier = Modifier.padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                                Text("Active Pipeline (Silero + SenseVoice + LFM3)", color = MintSuccess, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // Silero VAD (ONNX Quantized) Component Card
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Slate800),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Slate700, RoundedCornerShape(16.dp))
                    .testTag("model_card_silero_vad")
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Silero VAD (ONNX Quantized)",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        when (val state = uiState.sileroDownloadState) {
                            is SileroDownloadState.Ready, is SileroDownloadState.Downloaded -> {
                                Surface(shape = RoundedCornerShape(6.dp), color = MintSuccess.copy(alpha = 0.2f)) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(modifier = Modifier.size(5.dp).clip(CircleShape).background(MintSuccess))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Ready", color = MintSuccess, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                            is SileroDownloadState.Downloading -> {
                                Surface(shape = RoundedCornerShape(6.dp), color = AmberWarning.copy(alpha = 0.2f)) {
                                    Text("Downloading", color = AmberWarning, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                }
                            }
                            else -> {
                                Surface(shape = RoundedCornerShape(6.dp), color = Slate900) {
                                    Text("643 KB", color = Slate400, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Ultra-fast neural Voice Activity Detector (~643 KB)",
                        fontSize = 11.sp,
                        color = Color(0xFFF43F5E),
                        fontWeight = FontWeight.Medium
                    )

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Gates incoming 16kHz audio to only pass human speech frames, eliminating background room noise, TV static, and silence before feeding into ASR.",
                        fontSize = 11.sp,
                        color = Slate400,
                        lineHeight = 15.sp
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Specs Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Slate900)
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        SpecItem(icon = Icons.Default.Storage, label = "Size", value = "643 KB")
                        SpecItem(icon = Icons.Default.Speed, label = "Latency", value = "< 5 ms")
                        SpecItem(icon = Icons.Default.Memory, label = "RAM", value = "~10 MB")
                    }

                    if (uiState.sileroDownloadState is SileroDownloadState.Downloading) {
                        val state = uiState.sileroDownloadState
                        Spacer(modifier = Modifier.height(8.dp))
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "${state.bytesDownloaded / 1024} / ${state.totalBytes / 1024} KB",
                                    fontSize = 10.sp,
                                    color = Slate400
                                )
                                Text(
                                    text = "${(state.progress * 100).toInt()}%",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFF43F5E)
                                )
                            }
                            Spacer(modifier = Modifier.height(3.dp))
                            LinearProgressIndicator(
                                progress = { state.progress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(4.dp)
                                    .clip(RoundedCornerShape(2.dp)),
                                color = Color(0xFFF43F5E),
                                trackColor = Slate700
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        when (uiState.sileroDownloadState) {
                            is SileroDownloadState.Downloading -> {
                                OutlinedButton(
                                    onClick = onCancelSileroVad,
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Cancel Download", color = RoseError, fontSize = 12.sp)
                                }
                            }
                            is SileroDownloadState.Downloaded, is SileroDownloadState.Ready -> {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MintSuccess.copy(alpha = 0.15f),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Box(modifier = Modifier.padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                                        Text("Silero VAD Ready", color = MintSuccess, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                                IconButton(
                                    onClick = onDeleteSileroVad,
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete Silero VAD", tint = Slate400, modifier = Modifier.size(18.dp))
                                }
                            }
                            else -> {
                                Button(
                                    onClick = onDownloadSileroVad,
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF43F5E)),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                                    modifier = Modifier.weight(1f).testTag("download_silero_vad_btn")
                                ) {
                                    Icon(imageVector = Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(15.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Download Silero VAD (643 KB)", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }
            }
        }

        // LFM3-d1-2B (4-bit Quantized Neural Translator) Card
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Slate800),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Slate700, RoundedCornerShape(16.dp))
                    .testTag("model_card_lfm3_text_4bit")
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "LFM3-d1-2B (4-bit Quantized)",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        when (val state = uiState.lfm3TextDownloadState) {
                            is Lfm3TextDownloadState.Ready, is Lfm3TextDownloadState.Downloaded -> {
                                Surface(shape = RoundedCornerShape(6.dp), color = MintSuccess.copy(alpha = 0.2f)) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(modifier = Modifier.size(5.dp).clip(CircleShape).background(MintSuccess))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Ready", color = MintSuccess, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                            is Lfm3TextDownloadState.Downloading -> {
                                Surface(shape = RoundedCornerShape(6.dp), color = AmberWarning.copy(alpha = 0.2f)) {
                                    Text("Downloading", color = AmberWarning, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                }
                            }
                            else -> {
                                Surface(shape = RoundedCornerShape(6.dp), color = Slate900) {
                                    Text("~1.15 GB", color = Slate400, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Liquid AI 4-bit edge weights (`.litertlm`)",
                        fontSize = 11.sp,
                        color = Color(0xFFF43F5E),
                        fontWeight = FontWeight.Medium
                    )

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "State-of-the-art 4-bit quantized neural model executing via Google LiteRT. Contextually translates non-English transcripts directly into coherent English subtitles.",
                        fontSize = 11.sp,
                        color = Slate400,
                        lineHeight = 15.sp
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Specs Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Slate900)
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        SpecItem(icon = Icons.Default.Storage, label = "Size", value = "1.15 GB")
                        SpecItem(icon = Icons.Default.Speed, label = "Latency", value = "~180 ms")
                        SpecItem(icon = Icons.Default.Memory, label = "RAM", value = "~1.4 GB")
                    }

                    if (uiState.lfm3TextDownloadState is Lfm3TextDownloadState.Downloading) {
                        val state = uiState.lfm3TextDownloadState
                        Spacer(modifier = Modifier.height(8.dp))
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "${state.bytesDownloaded / (1024 * 1024)} / ${state.totalBytes / (1024 * 1024)} MB",
                                    fontSize = 10.sp,
                                    color = Slate400
                                )
                                Text(
                                    text = "${(state.progress * 100).toInt()}%",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFF43F5E)
                                )
                            }
                            Spacer(modifier = Modifier.height(3.dp))
                            LinearProgressIndicator(
                                progress = { state.progress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(4.dp)
                                    .clip(RoundedCornerShape(2.dp)),
                                color = Color(0xFFF43F5E),
                                trackColor = Slate700
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        when (uiState.lfm3TextDownloadState) {
                            is Lfm3TextDownloadState.Downloading -> {
                                OutlinedButton(
                                    onClick = onCancelLfm3Text,
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Cancel Download", color = RoseError, fontSize = 12.sp)
                                }
                            }
                            is Lfm3TextDownloadState.Downloaded, is Lfm3TextDownloadState.Ready -> {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MintSuccess.copy(alpha = 0.15f),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Box(modifier = Modifier.padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                                        Text("LFM3 4-bit Ready", color = MintSuccess, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                                IconButton(
                                    onClick = onDeleteLfm3Text,
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete LFM3 4-bit model", tint = Slate400, modifier = Modifier.size(18.dp))
                                }
                            }
                            else -> {
                                Button(
                                    onClick = onDownloadLfm3Text,
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF43F5E)),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                                    modifier = Modifier.weight(1f).testTag("download_lfm3_text_btn")
                                ) {
                                    Icon(imageVector = Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(15.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Download LFM3-d1-2B (1.15 GB)", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Model Management Header Card
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
                                text = "ON-DEVICE AI MODELS",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Slate400,
                                letterSpacing = 0.8.sp
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Whisper TFLite",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Slate900
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(imageVector = Icons.Default.Storage, contentDescription = null, tint = CyanPrimary, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Offline", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = CyanPrimary)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "100% offline speech recognition using LiteRT on CPU (XNNPACK) or GPU. No network required once downloaded.",
                        fontSize = 11.sp,
                        color = Slate400,
                        lineHeight = 15.sp
                    )
                }
            }
        }

        // LiteRT Hardware Acceleration Card
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
                        Text(
                            text = "ACCELERATION BACKEND",
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
                                text = "Low Latency",
                                color = CyanPrimary,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // CPU XNNPACK
                        val isCpu = uiState.accelerationBackend == com.example.sublive.engine.AccelerationBackend.CPU
                        OutlinedButton(
                            onClick = { onSelectBackend(com.example.sublive.engine.AccelerationBackend.CPU) },
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = if (isCpu) CyanPrimary.copy(alpha = 0.15f) else Slate900
                            ),
                            border = androidx.compose.foundation.BorderStroke(
                                1.5.dp,
                                if (isCpu) CyanPrimary else Slate700
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f).testTag("backend_cpu")
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("CPU (XNNPACK)", fontWeight = FontWeight.Bold, color = if (isCpu) CyanPrimary else Color.White, fontSize = 11.sp, maxLines = 1)
                                Text("4T ARM NEON", fontSize = 9.sp, color = Slate400, maxLines = 1)
                            }
                        }

                        // GPU Delegate
                        val isGpu = uiState.accelerationBackend == com.example.sublive.engine.AccelerationBackend.GPU
                        OutlinedButton(
                            onClick = { onSelectBackend(com.example.sublive.engine.AccelerationBackend.GPU) },
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = if (isGpu) VioletAccent.copy(alpha = 0.15f) else Slate900
                            ),
                            border = androidx.compose.foundation.BorderStroke(
                                1.5.dp,
                                if (isGpu) VioletAccent else Slate700
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f).testTag("backend_gpu")
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("GPU (OpenCL)", fontWeight = FontWeight.Bold, color = if (isGpu) VioletAccent else Color.White, fontSize = 11.sp, maxLines = 1)
                                Text("Mobile Shader", fontSize = 9.sp, color = Slate400, maxLines = 1)
                            }
                        }
                    }
                }
            }
        }

        // List of Whisper TFLite Models
        items(ModelTier.entries) { tier ->
            val status = uiState.modelStates[tier] ?: ModelStatus(tier = tier, state = ModelDownloadState.NotDownloaded)
            val isSelected = uiState.activeModel == tier

            ModelTierCard(
                tier = tier,
                status = status,
                isSelected = isSelected,
                onDownload = { onDownloadModel(tier) },
                onCancelDownload = onCancelDownload,
                onSelect = { onSelectModel(tier) },
                onDelete = { onDeleteModel(tier) }
            )
        }
    }
}

@Composable
fun ModelTierCard(
    tier: ModelTier,
    status: ModelStatus,
    isSelected: Boolean,
    onDownload: () -> Unit,
    onCancelDownload: () -> Unit,
    onSelect: () -> Unit,
    onDelete: () -> Unit
) {
    val borderColor = if (isSelected) CyanPrimary else Slate700

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Slate800),
        modifier = Modifier
            .fillMaxWidth()
            .border(if (isSelected) 1.5.dp else 1.dp, borderColor, RoundedCornerShape(16.dp))
            .testTag("model_card_${tier.id}")
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Header Row: Title & Active chip
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = tier.title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (isSelected) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = CyanPrimary.copy(alpha = 0.2f)
                    ) {
                        Text(
                            text = "ACTIVE",
                            color = CyanPrimary,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Sub-header Row: Recommendation and Status Tag
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = tier.recommendation,
                    fontSize = 11.sp,
                    color = VioletAccent,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                when (val state = status.state) {
                    is ModelDownloadState.Loaded -> {
                        Surface(shape = RoundedCornerShape(6.dp), color = MintSuccess.copy(alpha = 0.2f)) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(modifier = Modifier.size(5.dp).clip(CircleShape).background(MintSuccess))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Loaded", color = MintSuccess, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    is ModelDownloadState.Downloaded -> {
                        Surface(shape = RoundedCornerShape(6.dp), color = Slate700) {
                            Text("Ready", color = Slate400, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                        }
                    }
                    is ModelDownloadState.Downloading -> {
                        Surface(shape = RoundedCornerShape(6.dp), color = AmberWarning.copy(alpha = 0.2f)) {
                            Text("Downloading", color = AmberWarning, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                        }
                    }
                    else -> {
                        Surface(shape = RoundedCornerShape(6.dp), color = Slate900) {
                            Text("Not Downloaded", color = Slate400, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = tier.description,
                fontSize = 11.sp,
                color = Slate400,
                lineHeight = 15.sp
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Specs Row (Size, Latency, RAM) - optimized for Fold 4 narrow screen
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Slate900)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                SpecItem(icon = Icons.Default.Storage, label = "Size", value = tier.sizeLabel)
                SpecItem(icon = Icons.Default.Speed, label = "Latency", value = tier.latencyLabel)
                SpecItem(icon = Icons.Default.Memory, label = "RAM", value = tier.ramLabel)
            }

            // Download Progress Bar if in progress
            if (status.state is ModelDownloadState.Downloading) {
                val state = status.state
                Spacer(modifier = Modifier.height(8.dp))
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "${(state.bytesDownloaded / (1024 * 1024))} / ${(state.totalBytes / (1024 * 1024))} MB",
                            fontSize = 10.sp,
                            color = Slate400
                        )
                        Text(
                            text = "${(state.progress * 100).toInt()}%",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = AmberWarning
                        )
                    }
                    Spacer(modifier = Modifier.height(3.dp))
                    LinearProgressIndicator(
                        progress = { state.progress },
                        modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                        color = AmberWarning,
                        trackColor = Slate900
                    )
                }
            }

            // Error display if failed
            if (status.state is ModelDownloadState.Error) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = status.state.message,
                    color = RoseError,
                    fontSize = 11.sp
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                when (status.state) {
                    is ModelDownloadState.Downloading -> {
                        OutlinedButton(
                            onClick = onCancelDownload,
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Cancel Download", color = RoseError, fontSize = 12.sp)
                        }
                    }

                    is ModelDownloadState.Downloaded, is ModelDownloadState.Loaded -> {
                        if (!isSelected) {
                            Button(
                                onClick = onSelect,
                                colors = ButtonDefaults.buttonColors(containerColor = CyanPrimary),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                                modifier = Modifier.weight(1f).testTag("select_model_${tier.id}")
                            ) {
                                Text("Select Model", color = Slate900, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        } else {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MintSuccess.copy(alpha = 0.15f),
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(modifier = Modifier.padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                                    Text("Active Inference Engine", color = MintSuccess, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        IconButton(
                            onClick = onDelete,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete model", tint = Slate400, modifier = Modifier.size(18.dp))
                        }
                    }

                    is ModelDownloadState.Error -> {
                        Button(
                            onClick = onDownload,
                            colors = ButtonDefaults.buttonColors(containerColor = AmberWarning),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                            modifier = Modifier.weight(1f).testTag("retry_download_model_${tier.id}")
                        ) {
                            Icon(imageVector = Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(15.dp), tint = Slate900)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Retry (${tier.sizeLabel})", color = Slate900, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    else -> {
                        Button(
                            onClick = onDownload,
                            colors = ButtonDefaults.buttonColors(containerColor = VioletAccent),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                            modifier = Modifier.weight(1f).testTag("download_model_${tier.id}")
                        ) {
                            Icon(imageVector = Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Download (${tier.sizeLabel})", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SpecItem(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(imageVector = icon, contentDescription = null, tint = CyanPrimary, modifier = Modifier.size(13.dp))
        Spacer(modifier = Modifier.width(4.dp))
        Column {
            Text(text = label, fontSize = 8.sp, color = Slate400, maxLines = 1)
            Text(text = value, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1)
        }
    }
}
