package com.example.sublive.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.example.sublive.engine.StabilizedSubtitleState

class FloatingOverlayManager(private val context: Context) {

    companion object {
        private const val TAG = "FloatingOverlayManager"
    }

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var overlayView: ComposeView? = null
    private var lifecycleOwner: OverlayLifecycleOwner? = null

    private val subtitleState = mutableStateOf(StabilizedSubtitleState())
    private val overlaySettings = mutableStateOf(OverlaySettings())

    private var wmLayoutParams: WindowManager.LayoutParams? = null

    fun isShowing(): Boolean = overlayView != null

    fun updateSubtitleState(state: StabilizedSubtitleState) {
        subtitleState.value = state
    }

    fun updateSettings(settings: OverlaySettings) {
        overlaySettings.value = settings
    }

    @SuppressLint("ClickableViewAccessibility")
    fun show() {
        if (overlayView != null) return

        if (!Settings.canDrawOverlays(context)) {
            Log.w(TAG, "Cannot show overlay: SYSTEM_ALERT_WINDOW permission not granted")
            return
        }

        try {
            val owner = OverlayLifecycleOwner()
            owner.onCreate()
            owner.onStart()
            lifecycleOwner = owner

            val windowType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                windowType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                y = 120
            }
            wmLayoutParams = params

            val composeView = ComposeView(context).apply {
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
                setViewTreeLifecycleOwner(owner)
                setViewTreeSavedStateRegistryOwner(owner)
                setViewTreeViewModelStoreOwner(owner)

                setContent {
                    FloatingSubtitleBox(
                        subtitleState = subtitleState.value,
                        settings = overlaySettings.value,
                        onDrag = { dx, dy ->
                            this@FloatingOverlayManager.wmLayoutParams?.let { lp ->
                                lp.x += dx.toInt()
                                lp.y += dy.toInt()
                                windowManager.updateViewLayout(this@apply, lp)
                            }
                        },
                        onTogglePause = {
                            overlaySettings.value = overlaySettings.value.copy(
                                isPaused = !overlaySettings.value.isPaused
                            )
                        },
                        onClose = {
                            hide()
                        }
                    )
                }
            }

            windowManager.addView(composeView, params)
            overlayView = composeView
            Log.d(TAG, "Floating overlay view added successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show overlay", e)
        }
    }

    fun hide() {
        overlayView?.let {
            try {
                windowManager.removeView(it)
            } catch (e: Exception) {
                Log.w(TAG, "Exception removing overlay view", e)
            }
            overlayView = null
        }
        lifecycleOwner?.onDestroy()
        lifecycleOwner = null
    }
}

@Composable
fun FloatingSubtitleBox(
    subtitleState: StabilizedSubtitleState,
    settings: OverlaySettings,
    onDrag: (Float, Float) -> Unit,
    onTogglePause: () -> Unit,
    onClose: () -> Unit
) {
    val currentItem = subtitleState.currentPartial
    val lastFinalized = subtitleState.finalizedHistory.lastOrNull()
    val displayItem = currentItem ?: lastFinalized
    val subtitleText = displayItem?.displayText ?: ""

    val bgAlpha = settings.backgroundOpacity.coerceIn(0.2f, 0.98f)
    val bgColor = Color(0xFF0F172A).copy(alpha = bgAlpha)
    val textColor = Color.White
    val secondaryColor = Color(0xFF94A3B8)
    val accentCyan = Color(0xFF38BDF8)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    onDrag(dragAmount.x, dragAmount.y)
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = bgColor),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            modifier = Modifier
                .widthIn(max = 680.dp)
                .fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            ) {
                // Overlay Top Bar with Controls and Drag Indicator
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Live Status Indicator Pulse
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (settings.isPaused) Color(0xFFF59E0B) else Color(0xFF10B981))
                        )
                        Text(
                            text = if (settings.isPaused) "PAUSED" else "LIVE AI",
                            color = if (settings.isPaused) Color(0xFFF59E0B) else accentCyan,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.8.sp
                        )
                        if (settings.enableLocalAgreement && displayItem?.displayFrozenPrefix?.isNotBlank() == true) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = Color(0xFF10B981).copy(alpha = 0.25f)
                            ) {
                                Text(
                                    text = "🔒 STABLE",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF10B981),
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                        }
                        if (displayItem?.turnStatus?.isNotBlank() == true) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = if (displayItem.turnStatus.contains("Wait", ignoreCase = true)) {
                                    Color(0xFFF59E0B).copy(alpha = 0.25f)
                                } else if (displayItem.turnStatus.contains("Speaking", ignoreCase = true)) {
                                    Color(0xFF38BDF8).copy(alpha = 0.25f)
                                } else {
                                    Color(0xFF10B981).copy(alpha = 0.25f)
                                }
                            ) {
                                Text(
                                    text = displayItem.turnStatus,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (displayItem.turnStatus.contains("Wait", ignoreCase = true)) {
                                        Color(0xFFF59E0B)
                                    } else if (displayItem.turnStatus.contains("Speaking", ignoreCase = true)) {
                                        Color(0xFF38BDF8)
                                    } else {
                                        Color(0xFF10B981)
                                    },
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                        } else if (subtitleState.isProcessing) {
                            Text(
                                text = "• Transcribing",
                                color = secondaryColor,
                                fontSize = 10.sp
                            )
                        }
                    }

                    // Drag Indicator
                    Icon(
                        imageVector = Icons.Default.DragHandle,
                        contentDescription = "Drag overlay",
                        tint = secondaryColor.copy(alpha = 0.7f),
                        modifier = Modifier.size(20.dp)
                    )

                    // Controls
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = onTogglePause,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = if (settings.isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                                contentDescription = if (settings.isPaused) "Resume" else "Pause",
                                tint = textColor,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        IconButton(
                            onClick = onClose,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close overlay",
                                tint = secondaryColor,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Subtitle Content
                if (subtitleText.isBlank()) {
                    Text(
                        text = "Listening for audio playback...",
                        color = secondaryColor.copy(alpha = 0.8f),
                        fontSize = (settings.fontSizeSp - 2).sp,
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                } else if (displayItem?.isTranslationActive == true && displayItem.translatedText.isNotBlank()) {
                    val isTranslated = displayItem.translatedText != displayItem.originalText ||
                            settings.engineChoice == SubtitleEngineChoice.WHISPER_DIRECT
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = if (isTranslated) Color(0xFF10B981).copy(alpha = 0.25f) else Color(0xFFF59E0B).copy(alpha = 0.25f)
                            ) {
                                Text(
                                    text = if (isTranslated) "TRANSLATED (→ ${settings.targetLanguage.uppercase()})" else "TRANSLATING (→ ${settings.targetLanguage.uppercase()})...",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isTranslated) Color(0xFF10B981) else Color(0xFFF59E0B),
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                )
                            }
                            if (settings.enableLocalAgreement && displayItem.displayFrozenPrefix.isNotBlank()) {
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = Color(0xFF38BDF8).copy(alpha = 0.2f)
                                ) {
                                    Text(
                                        text = "APPEND-ONLY STABLE",
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = accentCyan,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }

                        // Local Agreement Stability Protocol Annotated String:
                        // Frozen prefix is permanently bold white; dynamic unconfirmed tail is accent cyan italic
                        val annotatedText = buildAnnotatedString {
                            if (settings.enableLocalAgreement && displayItem.displayFrozenPrefix.isNotBlank()) {
                                withStyle(SpanStyle(color = textColor, fontWeight = FontWeight.Bold)) {
                                    append(displayItem.displayFrozenPrefix)
                                }
                                if (displayItem.displayUnconfirmedTail.isNotBlank()) {
                                    append(" ")
                                }
                            }
                            if (displayItem.displayUnconfirmedTail.isNotBlank()) {
                                withStyle(
                                    SpanStyle(
                                        color = if (displayItem.displayFrozenPrefix.isNotBlank()) accentCyan else textColor,
                                        fontWeight = if (displayItem.displayFrozenPrefix.isNotBlank()) FontWeight.Medium else FontWeight.Bold,
                                        fontStyle = if (displayItem.displayFrozenPrefix.isNotBlank()) FontStyle.Italic else FontStyle.Normal
                                    )
                                ) {
                                    append(displayItem.displayUnconfirmedTail)
                                }
                            } else if (displayItem.displayFrozenPrefix.isBlank()) {
                                withStyle(SpanStyle(color = textColor, fontWeight = FontWeight.Bold)) {
                                    append(displayItem.translatedText)
                                }
                            }
                        }

                        Text(
                            text = annotatedText,
                            fontSize = settings.fontSizeSp.sp,
                            lineHeight = (settings.fontSizeSp + 5).sp
                        )

                        if (displayItem.originalText.isNotBlank() && displayItem.originalText != displayItem.translatedText) {
                            Text(
                                text = "Spoken: ${displayItem.originalText}",
                                color = secondaryColor.copy(alpha = 0.85f),
                                fontSize = (settings.fontSizeSp - 3).coerceAtLeast(11).sp,
                                lineHeight = (settings.fontSizeSp).coerceAtLeast(14).sp,
                                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                            )
                        }
                    }
                } else {
                    val annotatedText = buildAnnotatedString {
                        if (settings.enableLocalAgreement && displayItem?.displayFrozenPrefix?.isNotBlank() == true) {
                            withStyle(SpanStyle(color = textColor, fontWeight = FontWeight.Bold)) {
                                append(displayItem.displayFrozenPrefix)
                            }
                            if (displayItem.displayUnconfirmedTail.isNotBlank()) {
                                append(" ")
                            }
                        }
                        if (displayItem?.displayUnconfirmedTail?.isNotBlank() == true) {
                            withStyle(
                                SpanStyle(
                                    color = if (displayItem.displayFrozenPrefix.isNotBlank()) accentCyan else textColor,
                                    fontWeight = if (displayItem.displayFrozenPrefix.isNotBlank()) FontWeight.Medium else FontWeight.SemiBold,
                                    fontStyle = if (displayItem.displayFrozenPrefix.isNotBlank()) FontStyle.Italic else FontStyle.Normal
                                )
                            ) {
                                append(displayItem.displayUnconfirmedTail)
                            }
                        } else if (displayItem?.displayFrozenPrefix?.isBlank() != false) {
                            withStyle(SpanStyle(color = textColor, fontWeight = FontWeight.SemiBold)) {
                                append(subtitleText)
                            }
                        }
                    }

                    Text(
                        text = annotatedText,
                        fontSize = settings.fontSizeSp.sp,
                        lineHeight = (settings.fontSizeSp + 6).sp
                    )
                }
            }
        }
    }
}
