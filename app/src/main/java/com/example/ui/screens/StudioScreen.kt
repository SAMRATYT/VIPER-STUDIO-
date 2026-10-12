package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
                    text = if (streamConfig.isLandscape) "Switch to 9:16" else "Switch to 16:9",
                    fontSize = 10.sp,
                    color = ViperCyan,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        // Studio Viewport (Broadcast Output Monitor)
        Box(
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
            // Render Scene Layers in Viewport
            activeScene?.layers?.sortedBy { it.zIndex }?.forEach { layer ->
                if (layer.isVisible) {
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
                                    fontSize = 12.sp,
                                    color = Color(0xFF28364E),
                                    fontWeight = FontWeight.Bold,
                                    lineHeight = 16.sp
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
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(8.dp)
                                        .size(width = 110.dp, height = 75.dp)
                                )
                            }
                        }

                        LayerType.TEXT_BANNER -> {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .fillMaxWidth()
                                    .background(Color(0xD90A0E17))
                                    .padding(vertical = 4.dp, horizontal = 8.dp)
                            ) {
                                Text(
                                    text = layer.textContent,
                                    fontSize = 10.sp,
                                    color = ViperGreen,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                            }
                        }

                        LayerType.SOCIAL_BADGE -> {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .padding(8.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color(0xB3111722))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = layer.textContent,
                                    fontSize = 9.sp,
                                    color = ViperCyan,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        LayerType.ALERT_BOX -> {
                            AlertOverlay(
                                alert = activeAlert,
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .padding(8.dp)
                            )
                        }

                        else -> {}
                    }
                }
            }

            // Live Chat Viewport Overlay
            if (isChatVisible) {
                LiveChatOverlay(
                    messages = chatMessages,
                    onSendMessage = { viewModel.sendChatMessage(it) },
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(8.dp)
                        .fillMaxWidth(0.55f)
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
