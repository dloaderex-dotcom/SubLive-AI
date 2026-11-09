package com.example

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.sublive.audio.CaptureMode
import com.example.sublive.service.SubtitleForegroundService
import com.example.sublive.ui.AudioMonitorTab
import com.example.sublive.ui.DiagnosticsTab
import com.example.sublive.ui.MainViewModel
import com.example.sublive.ui.ModelCenterTab
import com.example.sublive.ui.SubtitleOverlayTab
import com.example.ui.theme.CyanPrimary
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.Slate400
import com.example.ui.theme.Slate900
import com.example.ui.theme.SurfaceDark

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MyApplicationTheme(darkTheme = true) {
                MainAppScreen(
                    viewModel = viewModel,
                    onStartSystemCapture = { launchMediaProjectionConsent() },
                    onStartMicCapture = { startMicService() },
                    onStopCapture = { stopSubtitleService() },
                    onRequestOverlayPermission = { requestOverlayPermission() }
                )
            }
        }
    }

    private var mediaProjectionLauncherCallback: ((Intent?) -> Unit)? = null

    fun setMediaProjectionLauncher(callback: (Intent?) -> Unit) {
        mediaProjectionLauncherCallback = callback
    }

    private fun launchMediaProjectionConsent() {
        val mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val intent = mediaProjectionManager.createScreenCaptureIntent()
        mediaProjectionLauncherCallback?.invoke(intent)
    }

    fun onMediaProjectionResult(resultCode: Int, data: Intent?) {
        if (resultCode == Activity.RESULT_OK && data != null) {
            val serviceIntent = Intent(this, SubtitleForegroundService::class.java).apply {
                action = SubtitleForegroundService.ACTION_START_SYSTEM_CAPTURE
                putExtra(SubtitleForegroundService.EXTRA_RESULT_CODE, resultCode)
                putExtra(SubtitleForegroundService.EXTRA_RESULT_DATA, data)
            }
            ContextCompat.startForegroundService(this, serviceIntent)
            viewModel.logEvent("MediaProjection granted. Starting System Audio Capture.")
        } else {
            Toast.makeText(this, "Screen capture permission denied", Toast.LENGTH_SHORT).show()
            viewModel.logEvent("MediaProjection consent declined by user", isWarning = true)
        }
    }

    private fun startMicService() {
        val serviceIntent = Intent(this, SubtitleForegroundService::class.java).apply {
            action = SubtitleForegroundService.ACTION_START_MIC_CAPTURE
        }
        ContextCompat.startForegroundService(this, serviceIntent)
        viewModel.logEvent("Starting Microphone Audio Capture.")
    }

    private fun stopSubtitleService() {
        val serviceIntent = Intent(this, SubtitleForegroundService::class.java).apply {
            action = SubtitleForegroundService.ACTION_STOP_SERVICE
        }
        startService(serviceIntent)
        viewModel.logEvent("Subtitle capture stopped.")
    }

    private fun requestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppScreen(
    viewModel: MainViewModel,
    onStartSystemCapture: () -> Unit,
    onStartMicCapture: () -> Unit,
    onStopCapture: () -> Unit,
    onRequestOverlayPermission: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? MainActivity
    val uiState by viewModel.uiState.collectAsState()

    var hasRecordAudioPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        )
    }

    var hasNotificationPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
        )
    }

    val hasOverlayPermission = remember(uiState) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else {
            true
        }
    }

    // Permission Launchers
    val recordAudioLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasRecordAudioPermission = granted
        if (granted) {
            viewModel.logEvent("RECORD_AUDIO permission granted")
        } else {
            viewModel.logEvent("RECORD_AUDIO permission denied", isError = true)
        }
    }

    val notificationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasNotificationPermission = granted
        if (granted) {
            viewModel.logEvent("POST_NOTIFICATIONS permission granted")
        }
    }

    // MediaProjection Consent Launcher
    val mediaProjectionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        activity?.onMediaProjectionResult(result.resultCode, result.data)
    }

    remember(activity) {
        activity?.setMediaProjectionLauncher { intent ->
            if (intent != null) {
                mediaProjectionLauncher.launch(intent)
            }
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = SurfaceDark,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "SubLive AI",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = Color.White
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Slate900,
                    titleContentColor = Color.White
                )
            )
        },
        bottomBar = {
            // Optimized for Fold 4 narrow screen (concise single-word labels)
            NavigationBar(
                containerColor = Slate900,
                contentColor = Slate400
            ) {
                NavigationBarItem(
                    selected = uiState.selectedTab == 0,
                    onClick = { viewModel.setTab(0) },
                    icon = { Icon(imageVector = Icons.Default.GraphicEq, contentDescription = "Studio") },
                    label = { Text("Studio", fontSize = 10.sp, maxLines = 1) },
                    alwaysShowLabel = true,
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Slate900,
                        selectedTextColor = CyanPrimary,
                        indicatorColor = CyanPrimary,
                        unselectedIconColor = Slate400,
                        unselectedTextColor = Slate400
                    ),
                    modifier = Modifier.testTag("tab_studio")
                )

                NavigationBarItem(
                    selected = uiState.selectedTab == 1,
                    onClick = { viewModel.setTab(1) },
                    icon = { Icon(imageVector = Icons.Default.Layers, contentDescription = "Overlay") },
                    label = { Text("Overlay", fontSize = 10.sp, maxLines = 1) },
                    alwaysShowLabel = true,
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Slate900,
                        selectedTextColor = CyanPrimary,
                        indicatorColor = CyanPrimary,
                        unselectedIconColor = Slate400,
                        unselectedTextColor = Slate400
                    ),
                    modifier = Modifier.testTag("tab_overlay")
                )

                NavigationBarItem(
                    selected = uiState.selectedTab == 2,
                    onClick = { viewModel.setTab(2) },
                    icon = { Icon(imageVector = Icons.Default.Psychology, contentDescription = "Models") },
                    label = { Text("Models", fontSize = 10.sp, maxLines = 1) },
                    alwaysShowLabel = true,
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Slate900,
                        selectedTextColor = CyanPrimary,
                        indicatorColor = CyanPrimary,
                        unselectedIconColor = Slate400,
                        unselectedTextColor = Slate400
                    ),
                    modifier = Modifier.testTag("tab_models")
                )

                NavigationBarItem(
                    selected = uiState.selectedTab == 3,
                    onClick = { viewModel.setTab(3) },
                    icon = { Icon(imageVector = Icons.Default.Analytics, contentDescription = "Diag") },
                    label = { Text("Diag", fontSize = 10.sp, maxLines = 1) },
                    alwaysShowLabel = true,
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Slate900,
                        selectedTextColor = CyanPrimary,
                        indicatorColor = CyanPrimary,
                        unselectedIconColor = Slate400,
                        unselectedTextColor = Slate400
                    ),
                    modifier = Modifier.testTag("tab_diagnostics")
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (uiState.selectedTab) {
                0 -> {
                    AudioMonitorTab(
                        uiState = uiState,
                        onStartService = {
                            val currentEngine = uiState.overlaySettings.engineChoice
                            val isReady = when (currentEngine) {
                                com.example.sublive.overlay.SubtitleEngineChoice.VOSK_MLKIT -> viewModel.isVoskReady()
                                com.example.sublive.overlay.SubtitleEngineChoice.SENSEVOICE_MLKIT -> viewModel.isSenseVoiceReady()
                                com.example.sublive.overlay.SubtitleEngineChoice.LFM3_OMNI_LITERT -> viewModel.isLfm3Ready()
                                com.example.sublive.overlay.SubtitleEngineChoice.SILERO_SENSEVOICE_LFM3 -> viewModel.isSileroSenseVoiceLfm3Ready()
                                else -> viewModel.isWhisperReady()
                            }

                            if (!isReady) {
                                val modelMsg = when (currentEngine) {
                                    com.example.sublive.overlay.SubtitleEngineChoice.VOSK_MLKIT ->
                                        "No Vosk model found! Please download the Vosk model in the Models tab."
                                    com.example.sublive.overlay.SubtitleEngineChoice.SENSEVOICE_MLKIT ->
                                        "No SenseVoice model found! Please download SenseVoice model in the Models tab."
                                    com.example.sublive.overlay.SubtitleEngineChoice.LFM3_OMNI_LITERT ->
                                        "No LFM3-Omni model found! Please download LFM3-Omni-2B in the Models tab."
                                    com.example.sublive.overlay.SubtitleEngineChoice.SILERO_SENSEVOICE_LFM3 -> {
                                        if (!viewModel.isSenseVoiceReady()) "SenseVoice model is missing! Please download SenseVoice in Models tab."
                                        else if (!viewModel.isSileroVadReady()) "Silero VAD model is missing! Please download Silero VAD in Models tab."
                                        else "LFM3 4-bit model is missing! Please download LFM3-d1-2B in Models tab."
                                    }
                                    else ->
                                        "No Whisper model found! Please download Whisper Tiny in the Models tab."
                                }
                                Toast.makeText(context, modelMsg, Toast.LENGTH_LONG).show()
                                viewModel.logEvent("Cannot start: $modelMsg Redirecting to Models tab.", isWarning = true)
                                viewModel.setTab(2) // Switch to Models tab
                                return@AudioMonitorTab
                            }

                            // Auto-select downloaded model if active whisper model is not on disk
                            if (currentEngine != com.example.sublive.overlay.SubtitleEngineChoice.VOSK_MLKIT &&
                                currentEngine != com.example.sublive.overlay.SubtitleEngineChoice.SENSEVOICE_MLKIT &&
                                currentEngine != com.example.sublive.overlay.SubtitleEngineChoice.LFM3_OMNI_LITERT &&
                                currentEngine != com.example.sublive.overlay.SubtitleEngineChoice.SILERO_SENSEVOICE_LFM3) {
                                val readyTier = viewModel.getActiveOrFirstDownloadedModel()
                                if (readyTier != null && readyTier != uiState.activeModel) {
                                    viewModel.selectModel(readyTier)
                                    viewModel.logEvent("Auto-selected downloaded model: ${readyTier.title}")
                                }
                            }

                            if (!hasRecordAudioPermission) {
                                recordAudioLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                return@AudioMonitorTab
                            }
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotificationPermission) {
                                notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                return@AudioMonitorTab
                            }

                            if (uiState.selectedCaptureMode == CaptureMode.SYSTEM_PLAYBACK) {
                                onStartSystemCapture()
                            } else {
                                onStartMicCapture()
                            }
                        },
                        onStopService = onStopCapture,
                        onSelectMode = { mode ->
                            viewModel.setCaptureMode(mode)
                            if (uiState.isServiceRunning) {
                                if (mode == CaptureMode.MICROPHONE_FALLBACK) {
                                    onStartMicCapture()
                                } else {
                                    onStartSystemCapture()
                                }
                            }
                        },
                        onSelectEngineChoice = { viewModel.setEngineChoice(it) },
                        onSourceLanguageChange = { viewModel.setSourceLanguage(it) },
                        onTargetLanguageChange = { viewModel.setTargetLanguage(it) }
                    )
                }

                1 -> {
                    SubtitleOverlayTab(
                        uiState = uiState,
                        hasOverlayPermission = hasOverlayPermission,
                        onRequestOverlayPermission = onRequestOverlayPermission,
                        onToggleOverlayService = {
                            val intent = Intent(context, SubtitleForegroundService::class.java).apply {
                                action = SubtitleForegroundService.ACTION_TOGGLE_OVERLAY
                            }
                            context.startService(intent)
                        },
                        onFontSizeChange = { viewModel.updateOverlayFontSize(it) },
                        onOpacityChange = { viewModel.updateOverlayOpacity(it) },
                        onTogglePauseOverlay = { viewModel.toggleOverlayPause() },
                        onSelectEngineChoice = { viewModel.setEngineChoice(it) },
                        onSourceLanguageChange = { viewModel.setSourceLanguage(it) },
                        onTargetLanguageChange = { viewModel.setTargetLanguage(it) },
                        onToggleTurnTakingWait = { viewModel.updateTurnTakingWait(it) },
                        onTurnTakingWaitDurationChange = { viewModel.updateTurnTakingWaitMs(it) },
                        onToggleLocalAgreement = { viewModel.updateLocalAgreement(it) }
                    )
                }

                2 -> {
                    ModelCenterTab(
                        uiState = uiState,
                        onDownloadModel = { viewModel.downloadModel(it) },
                        onCancelDownload = { viewModel.cancelModelDownload() },
                        onSelectModel = { viewModel.selectModel(it) },
                        onDeleteModel = { viewModel.deleteModel(it) },
                        onSelectBackend = { viewModel.setAccelerationBackend(it) },
                        onDownloadVosk = { viewModel.downloadVoskModel() },
                        onCancelVoskDownload = { viewModel.cancelVoskDownload() },
                        onDeleteVosk = { viewModel.deleteVoskModel() },
                        onSelectVoskEngine = { viewModel.setEngineChoice(com.example.sublive.overlay.SubtitleEngineChoice.VOSK_MLKIT) },
                        onDownloadSenseVoice = { viewModel.downloadSenseVoiceModel() },
                        onCancelSenseVoiceDownload = { viewModel.cancelSenseVoiceDownload() },
                        onDeleteSenseVoice = { viewModel.deleteSenseVoiceModel() },
                        onSelectSenseVoiceEngine = { viewModel.setEngineChoice(com.example.sublive.overlay.SubtitleEngineChoice.SENSEVOICE_MLKIT) },
                        onDownloadLfm3 = { viewModel.downloadLfm3Model() },
                        onCancelLfm3Download = { viewModel.cancelLfm3Download() },
                        onDeleteLfm3 = { viewModel.deleteLfm3Model() },
                        onSelectLfm3Engine = { viewModel.setEngineChoice(com.example.sublive.overlay.SubtitleEngineChoice.LFM3_OMNI_LITERT) },
                        onDownloadSileroVad = { viewModel.downloadSileroVad() },
                        onCancelSileroVad = { viewModel.cancelSileroVadDownload() },
                        onDeleteSileroVad = { viewModel.deleteSileroVad() },
                        onDownloadLfm3Text = { viewModel.downloadLfm3TextModel() },
                        onCancelLfm3Text = { viewModel.cancelLfm3TextDownload() },
                        onDeleteLfm3Text = { viewModel.deleteLfm3TextModel() },
                        onSelectSileroPipeline = { viewModel.setEngineChoice(com.example.sublive.overlay.SubtitleEngineChoice.SILERO_SENSEVOICE_LFM3) }
                    )
                }

                3 -> {
                    DiagnosticsTab(
                        uiState = uiState,
                        hasRecordAudioPermission = hasRecordAudioPermission,
                        hasOverlayPermission = hasOverlayPermission,
                        hasNotificationPermission = hasNotificationPermission,
                        onRequestRecordAudio = { recordAudioLauncher.launch(Manifest.permission.RECORD_AUDIO) },
                        onRequestOverlay = onRequestOverlayPermission,
                        onRequestNotification = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        }
                    )
                }
            }
        }
    }
}
