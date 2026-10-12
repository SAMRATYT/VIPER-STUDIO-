package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.model.AlertType
import com.example.data.model.AudioCaptureMode
import com.example.data.model.LayerType
import com.example.ui.ViperStudioViewModel
import com.example.ui.components.AlertOverlay
import com.example.ui.components.CameraPreviewView
import com.example.ui.components.DualChannelVUMeter
import com.example.ui.components.LiveChatOverlay
import com.example.ui.components.TelemetryBadgeBar
import com.example.ui.theme.LiveRed
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.ViperCyan
import com.example.ui.theme.ViperGreen
import com.example.ui.theme.ViperSurfaceCard

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StudioScreen(
    viewModel: ViperStudioViewModel,
    modifier: Modifier = Modifier
) {
    val activeScene by viewModel.activeScene.collectAsState()
    val streamConfig by viewModel.streamConfig.collectAsState()
    val streamStats by viewModel.streamStats.collectAsState()
    val audioConfig by viewModel.audioConfig.collectAsState()
    val micLevel by viewModel.micLevel.collectAsState()
    val gameLevel by viewModel.gameLevel.collectAsState()
    val isCameraActive by viewModel.isCameraActive.collectAsState()
    val lensFacing by viewModel.cameraLensFacing.collectAsState()
    val activeAlert by viewModel.activeAlert.collectAsState()
    val chatMessages by viewModel.chatMessages.collectAsState()
    val isChatVisible by viewModel.isChatOverlayVisible.collectAsState()
    val liveChatSettings by viewModel.liveChatSettings.collectAsState()

    val context = LocalContext.current
    var cameraShape by remember { mutableStateOf("ROUNDED") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0A0E17))
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Telemetry Monitor
        TelemetryBadgeBar(stats = streamStats)

        // Viewport Header & Orientation Toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (streamConfig.isLandscape) "BROADCAST VIEWPORT (16:9 LANDSCAPE)" else "BROADCAST VIEWPORT (9:16 VERTICAL)",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = if (streamConfig.isLandscape) ViperGreen else ViperCyan
            )
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF131A28))
                    .border(1.dp, Color(0xFF263348), RoundedCornerShape(6.dp))
                    .clickable { viewModel.toggleOrientation() }
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = if (streamConfig.isLandscape) "Rotate to 9:16" else "Rotate to 16:9",
                    fontSize = 10.sp,
                    color = ViperCyan,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        // Studio Viewport (Broadcast Output Monitor with Dynamic Coordinates & Custom Draggable Chat)
        BoxWithConstraints(
            modifier = (if (streamConfig.isLandscape) {
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
            } else {
                Modifier
                    .fillMaxWidth(0.68f)
                    .aspectRatio(9f / 16f)
                    .align(Alignment.CenterHorizontally)
            })
                .clip(RoundedCornerShape(14.dp))
                .border(2.dp, if (streamStats.isStreaming) LiveRed else ViperGreen, RoundedCornerShape(14.dp))
                .background(Color(0xFF0E1422))
        ) {
            val canvasWidth = maxWidth
            val canvasHeight = maxHeight
            val density = LocalDensity.current
            val canvasWidthPx = with(density) { canvasWidth.toPx() }
            val canvasHeightPx = with(density) { canvasHeight.toPx() }

            // Render Scene Layers in Viewport with dynamic positions & sizes
            activeScene?.layers?.sortedBy { it.zIndex }?.forEach { layer ->
                if (layer.isVisible) {
                    val xOffset = (canvasWidth * (layer.xPct / 100f))
                    val yOffset = (canvasHeight * (layer.yPct / 100f))
                    val layerW = (canvasWidth * (layer.widthPct / 100f))
                    val layerH = (canvasHeight * (layer.heightPct / 100f))
                    val layerAlpha = layer.opacity.coerceIn(0.05f, 1.0f)

                    Box(
                        modifier = Modifier
                            .offset { IntOffset(xOffset.roundToPx(), yOffset.roundToPx()) }
                            .size(width = layerW, height = layerH)
                            .alpha(layerAlpha)
                    ) {
                        when (layer.type) {
                            LayerType.GAME_SCREEN -> {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Color(0xFF0C121E)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "GAMEPLAY VIDEO CANVAS\n1080p 60FPS BROADCAST",
                                        fontSize = 11.sp,
                                        color = Color(0xFF28364E),
                                        fontWeight = FontWeight.Bold,
                                        lineHeight = 15.sp
                                    )
                                }
                            }

                            LayerType.CAMERA_PIP -> {
                                if (isCameraActive) {
                                    CameraPreviewView(
                                        lensFacing = lensFacing,
                                        isCameraActive = isCameraActive,
                                        shapeType = cameraShape,
                                        onSwitchCamera = { viewModel.switchCameraLens() },
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            }

                            LayerType.TEXT_BANNER -> {
                                val bannerBg = try {
                                    Color(android.graphics.Color.parseColor(layer.bgColorHex))
                                } catch (_: Exception) {
                                    Color(0xD90A0E17)
                                }
                                val textColor = try {
                                    Color(android.graphics.Color.parseColor(layer.textColorHex))
                                } catch (_: Exception) {
                                    ViperGreen
                                }

                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(bannerBg)
                                        .padding(horizontal = 6.dp, vertical = 2.dp),
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    Text(
                                        text = layer.textContent,
                                        fontSize = 10.sp,
                                        color = textColor,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 2
                                    )
                                }
                            }

                            LayerType.SOCIAL_BADGE -> {
                                val badgeBg = try {
                                    Color(android.graphics.Color.parseColor(layer.bgColorHex))
                                } catch (_: Exception) {
                                    Color(0xB3111722)
                                }
                                val badgeTextColor = try {
                                    Color(android.graphics.Color.parseColor(layer.textColorHex))
                                } catch (_: Exception) {
                                    ViperCyan
                                }

                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(badgeBg)
                                        .padding(horizontal = 6.dp, vertical = 2.dp),
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    Text(
                                        text = layer.textContent,
                                        fontSize = 9.sp,
                                        color = badgeTextColor,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1
                                    )
                                }
                            }

                            LayerType.ALERT_BOX -> {
                                AlertOverlay(
                                    alert = activeAlert,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }

                            LayerType.IMAGE_OVERLAY -> {
                                if (layer.imageUrl.isNotBlank()) {
                                    AsyncImage(
                                        model = ImageRequest.Builder(context)
                                            .data(layer.imageUrl)
                                            .crossfade(true)
                                            .build(),
                                        contentDescription = layer.label,
                                        contentScale = if (layer.widthPct >= 90f) ContentScale.Crop else ContentScale.Fit,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    val bgCol = try {
                                        Color(android.graphics.Color.parseColor(layer.bgColorHex))
                                    } catch (_: Exception) {
                                        Color(0x33B388FF)
                                    }
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(bgCol)
                                            .border(1.dp, ViperGreen.copy(alpha = 0.4f), RoundedCornerShape(6.dp)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = layer.label,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = ViperGreen
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Customizable Draggable Live Chat Viewport Overlay
            if (isChatVisible && liveChatSettings.isOverlayEnabled) {
                val chatX = (canvasWidth * (liveChatSettings.xPct / 100f))
                val chatY = (canvasHeight * (liveChatSettings.yPct / 100f))
                val chatW = (canvasWidth * (liveChatSettings.widthPct / 100f))

                LiveChatOverlay(
                    messages = chatMessages,
                    onSendMessage = { viewModel.sendChatMessage(it) },
                    settings = liveChatSettings,
                    onDragDelta = { dx, dy ->
                        val deltaXPct = if (canvasWidthPx > 0f) (dx / canvasWidthPx) * 100f else 0f
                        val deltaYPct = if (canvasHeightPx > 0f) (dy / canvasHeightPx) * 100f else 0f
                        viewModel.updateLiveChatPosition(
                            liveChatSettings.xPct + deltaXPct,
                            liveChatSettings.yPct + deltaYPct
                        )
                    },
                    onToggleLock = { viewModel.toggleLiveChatLock() },
                    modifier = Modifier
                        .offset { IntOffset(chatX.roundToPx(), chatY.roundToPx()) }
                        .width(chatW)
                )
            }
        }

        // Live Audio VU Meter
        DualChannelVUMeter(
            gameLevel = gameLevel,
            micLevel = micLevel,
            isMicMuted = audioConfig.isMicMuted
        )

        // Studio HUD Quick Layer Toggles
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = ViperSurfaceCard),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "STUDIO QUICK LAYER SWITCHES",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextSecondary
                )

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Facecam PiP Toggle
                    QuickHudPill(
                        label = "Facecam PiP",
                        isActive = isCameraActive,
                        onClick = { viewModel.toggleCamera() }
                    )

                    // Lens switch
                    QuickHudPill(
                        label = "Flip Camera",
                        isActive = false,
                        onClick = { viewModel.switchCameraLens() }
                    )

                    // Shape: Rounded vs Circle
                    QuickHudPill(
                        label = "Shape: $cameraShape",
                        isActive = true,
                        onClick = {
                            cameraShape = when (cameraShape) {
                                "ROUNDED" -> "CIRCLE"
                                "CIRCLE" -> "SQUARE"
                                else -> "ROUNDED"
                            }
                        }
                    )

                    // Live Chat overlay toggle
                    QuickHudPill(
                        label = "Live Chat Overlay",
                        isActive = isChatVisible,
                        onClick = { viewModel.toggleChatOverlay() }
                    )
                }
            }
        }

        // Customizable Draggable Live Chat Overlay Controls Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = ViperSurfaceCard),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Default.Chat, contentDescription = null, tint = ViperCyan, modifier = Modifier.size(16.dp))
                        Text(
                            text = "LIVE CHAT OVERLAY CUSTOMIZATION",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextSecondary
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        IconButton(
                            onClick = { viewModel.toggleLiveChatLock() },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = if (liveChatSettings.isLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                                contentDescription = "Lock Chat",
                                tint = if (liveChatSettings.isLocked) Color(0xFFFFD600) else ViperCyan,
                                modifier = Modifier.size(14.dp)
                            )
                        }

                        IconButton(
                            onClick = { viewModel.updateLiveChatPosition(5f, 45f) },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.RestartAlt,
                                contentDescription = "Reset Position",
                                tint = TextMuted,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }

                // Opacity Slider
                Column {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Chat Background Opacity", fontSize = 11.sp, color = TextPrimary)
                        Text("${(liveChatSettings.backgroundOpacity * 100).toInt()}%", fontSize = 11.sp, color = ViperCyan, fontWeight = FontWeight.Bold)
                    }
                    Slider(
                        value = liveChatSettings.backgroundOpacity,
                        onValueChange = { viewModel.updateLiveChatOpacity(it) },
                        valueRange = 0.2f..1.0f,
                        colors = SliderDefaults.colors(thumbColor = ViperCyan, activeTrackColor = ViperCyan)
                    )
                }

                // Font Size Pills
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Font Size:", fontSize = 11.sp, color = TextPrimary)
                    listOf(10 to "Small", 12 to "Standard", 14 to "Medium", 16 to "Large").forEach { (sz, label) ->
                        val isSel = liveChatSettings.fontSizeSp == sz
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isSel) ViperCyan else Color(0xFF131A28))
                                .border(1.dp, if (isSel) ViperCyan else Color(0xFF263349), RoundedCornerShape(6.dp))
                            .clickable { viewModel.updateLiveChatFontSize(sz) }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = label,
                                fontSize = 10.sp,
                                fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSel) Color.Black else TextPrimary
                            )
                        }
                    }
                }
            }
        }

        // Live Audio Real-Time Sliders (Gain 0% to 200%)
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = ViperSurfaceCard),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "HARDWARE AUDIO MIXER",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextSecondary
                    )
                    IconButton(
                        onClick = { viewModel.toggleMicMute() },
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(if (audioConfig.isMicMuted) Color(0x33FF1744) else Color(0x2200E676))
                    ) {
                        Icon(
                            imageVector = if (audioConfig.isMicMuted) Icons.Default.MicOff else Icons.Default.Mic,
                            contentDescription = "Mute",
                            tint = if (audioConfig.isMicMuted) Color(0xFFFF5252) else ViperGreen,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                // Audio Capture Mode Pills (Internal + Mic, Internal Only, Mic Only)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    AudioCaptureMode.values().forEach { mode ->
                        val isSel = audioConfig.audioCaptureMode == mode
                        QuickHudPill(
                            label = mode.shortLabel,
                            isActive = isSel,
                            onClick = { viewModel.setAudioCaptureMode(mode) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // Game Audio Slider
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Internal Game Audio", fontSize = 12.sp, color = TextPrimary)
                        Text("${(audioConfig.gameAudioVolume * 100).toInt()}%", fontSize = 12.sp, color = ViperGreen, fontWeight = FontWeight.Bold)
                    }
                    Slider(
                        value = audioConfig.gameAudioVolume,
                        onValueChange = { viewModel.setGameVolume(it) },
                        valueRange = 0f..2f,
                        colors = SliderDefaults.colors(
                            thumbColor = ViperGreen,
                            activeTrackColor = ViperGreen,
                            inactiveTrackColor = Color(0xFF202C40)
                        )
                    )
                }

                // Microphone Audio Slider
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Microphone Voice", fontSize = 12.sp, color = TextPrimary)
                        Text(
                            if (audioConfig.isMicMuted) "MUTED" else "${(audioConfig.micAudioVolume * 100).toInt()}%",
                            fontSize = 12.sp,
                            color = if (audioConfig.isMicMuted) Color(0xFFFF5252) else ViperCyan,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Slider(
                        value = audioConfig.micAudioVolume,
                        onValueChange = { viewModel.setMicVolume(it) },
                        valueRange = 0f..2f,
                        colors = SliderDefaults.colors(
                            thumbColor = ViperCyan,
                            activeTrackColor = ViperCyan,
                            inactiveTrackColor = Color(0xFF202C40)
                        )
                    )
                }

                // Audio Enhancement Toggles
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    QuickHudPill(
                        label = "Noise Suppression",
                        isActive = audioConfig.isNoiseSuppressionEnabled,
                        onClick = { viewModel.toggleNoiseSuppression() },
                        modifier = Modifier.weight(1f)
                    )
                    QuickHudPill(
                        label = "Echo Cancellation",
                        isActive = audioConfig.isEchoCancellationEnabled,
                        onClick = { viewModel.toggleEchoCancellation() },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Live Alert Test Trigger Pad
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = ViperSurfaceCard),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "TRIGGER STREAM ALERTS (AUDIO + ANIMATION)",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextSecondary
                )

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AlertTestButton(
                        label = "+ Subscriber",
                        color = Color(0xFF00E676),
                        onClick = { viewModel.triggerTestAlert(AlertType.SUBSCRIBER) }
                    )
                    AlertTestButton(
                        label = "★ Super Chat ($20)",
                        color = Color(0xFFFFD600),
                        onClick = { viewModel.triggerTestAlert(AlertType.SUPER_CHAT) }
                    )
                    AlertTestButton(
                        label = "♥ Donation (₹500)",
                        color = Color(0xFF00E5FF),
                        onClick = { viewModel.triggerTestAlert(AlertType.DONATION) }
                    )
                    AlertTestButton(
                        label = "✔ Follower",
                        color = Color(0xFFB388FF),
                        onClick = { viewModel.triggerTestAlert(AlertType.FOLLOWER) }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun QuickHudPill(
    label: String,
    isActive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (isActive) Color(0x2200E676) else Color(0xFF131A28))
            .border(1.dp, if (isActive) ViperGreen else Color(0xFF263349), RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
            color = if (isActive) ViperGreen else TextPrimary
        )
    }
}

@Composable
private fun AlertTestButton(
    label: String,
    color: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.15f))
            .border(1.dp, color, RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(
            text = label,
            color = color,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
