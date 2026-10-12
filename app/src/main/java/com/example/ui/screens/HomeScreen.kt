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
import androidx.compose.material.icons.filled.CastConnected
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.ViperStudioViewModel
import com.example.ui.components.DualChannelVUMeter
import com.example.ui.components.TelemetryBadgeBar
import com.example.ui.theme.LiveRed
import com.example.ui.theme.RecordingRed
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.ViperCyan
import com.example.ui.theme.ViperGreen
import com.example.ui.theme.ViperSurfaceCard

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(
    viewModel: ViperStudioViewModel,
    onNavigateToLive: () -> Unit,
    onNavigateToStudio: () -> Unit,
    onNavigateToRecorder: () -> Unit,
    onNavigateToSceneEditor: () -> Unit,
    onNavigateToAlerts: () -> Unit,
    onNavigateToMirror: () -> Unit,
    onStartLiveRequest: () -> Unit = {},
    onStartRecordRequest: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val streamConfig by viewModel.streamConfig.collectAsState()
    val streamStats by viewModel.streamStats.collectAsState()
    val audioConfig by viewModel.audioConfig.collectAsState()
    val micLevel by viewModel.micLevel.collectAsState()
    val gameLevel by viewModel.gameLevel.collectAsState()
    val isRecording by viewModel.isRecording.collectAsState()
    val scenes by viewModel.scenes.collectAsState()
    val activeScene by viewModel.activeScene.collectAsState()
    val recordings by viewModel.recordingsList.collectAsState()
    val hwProfile = viewModel.hardwareProfile

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0A0E17))
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // App Header Banner
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(ViperGreen)
                    )
                    Text(
                        text = "VIPER STUDIO",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Black,
                        color = TextPrimary,
                        letterSpacing = 1.sp
                    )
                }
                Text(
                    text = "Professional Gaming & Live Broadcast Engine",
                    fontSize = 11.sp,
                    color = TextMuted
                )
            }

            // Quick Mic Mute Toggle
            IconButton(
                onClick = { viewModel.toggleMicMute() },
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (audioConfig.isMicMuted) Color(0x33FF1744) else Color(0x2200E676))
                    .testTag("home_quick_mic_toggle")
            ) {
                Icon(
                    imageVector = if (audioConfig.isMicMuted) Icons.Default.MicOff else Icons.Default.Mic,
                    contentDescription = "Toggle Mic",
                    tint = if (audioConfig.isMicMuted) Color(0xFFFF5252) else ViperGreen
                )
            }
        }

        // Stream Error Warning Banner
        if (streamStats.errorMessage != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF2A1015)),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFF3D71)),
                shape = RoundedCornerShape(10.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "BROADCAST ALERT",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFFF5252)
                        )
                        Text(
                            text = streamStats.errorMessage ?: "",
                            fontSize = 12.sp,
                            color = TextPrimary
                        )
                    }
                    IconButton(onClick = { viewModel.clearErrorMessage() }) {
                        Text("✕", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                }
            }
        }

        // Live Telemetry Bar
        TelemetryBadgeBar(stats = streamStats)

        // Hero Action Cards (Broadcast vs Screen Record)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Live Stream Hero Card
            Card(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .clickable { onNavigateToLive() }
                    .border(
                        1.dp,
                        if (streamStats.isStreaming) LiveRed else Color(0xFF233048),
                        RoundedCornerShape(14.dp)
                    ),
                colors = CardDefaults.cardColors(containerColor = ViperSurfaceCard)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (streamStats.isStreaming) Color(0x33FF1744) else Color(0x2200E676)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.LiveTv,
                                contentDescription = null,
                                tint = if (streamStats.isStreaming) LiveRed else ViperGreen,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        if (streamStats.isStreaming) {
                            Text(
                                text = "ON AIR",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black,
                                color = LiveRed
                            )
                        }
                    }

                    Text(
                        text = "Live Stream",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = TextPrimary
                    )

                    Text(
                        text = "${streamConfig.platform.displayName}\n${streamConfig.resolution.label.substringBefore(" ")} • ${streamConfig.fps.value}FPS",
                        fontSize = 11.sp,
                        color = TextSecondary,
                        lineHeight = 15.sp
                    )

                    Button(
                        onClick = {
                            if (streamStats.isStreaming) viewModel.stopBroadcast() else onStartLiveRequest()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("hero_stream_button"),
                        enabled = !streamStats.isConnecting,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (streamStats.isStreaming) LiveRed else ViperGreen,
                            contentColor = Color.Black
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(
                            imageVector = if (streamStats.isStreaming) Icons.Default.Stop else Icons.Default.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (streamStats.isStreaming) "STOP" else if (streamStats.isConnecting) "STARTING..." else "GO LIVE",
                            fontWeight = FontWeight.Black,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            // Screen Recorder Hero Card
            Card(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .clickable { onNavigateToRecorder() }
                    .border(
                        1.dp,
                        if (isRecording) RecordingRed else Color(0xFF233048),
                        RoundedCornerShape(14.dp)
                    ),
                colors = CardDefaults.cardColors(containerColor = ViperSurfaceCard)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isRecording) Color(0x33FF1744) else Color(0x2200E5FF)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.RadioButtonChecked,
                                contentDescription = null,
                                tint = if (isRecording) RecordingRed else ViperCyan,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        if (isRecording) {
                            Text(
                                text = "REC",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black,
                                color = RecordingRed
                            )
                        }
                    }

                    Text(
                        text = "Screen Record",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = TextPrimary
                    )

                    Text(
                        text = "Internal Game Audio\n+ Mic Mixing (MP4)",
                        fontSize = 11.sp,
                        color = TextSecondary,
                        lineHeight = 15.sp
                    )

                    Button(
                        onClick = {
                            if (isRecording) viewModel.stopScreenRecording() else onStartRecordRequest()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("hero_record_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isRecording) RecordingRed else ViperCyan,
                            contentColor = Color.Black
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(
                            imageVector = if (isRecording) Icons.Default.Stop else Icons.Default.FiberManualRecord,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (isRecording) "STOP REC" else "RECORD",
                            fontWeight = FontWeight.Black,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }

        // Live Audio VU Meter Section
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "AUDIO MIXER MONITOR",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = TextSecondary
            )
            DualChannelVUMeter(
                gameLevel = gameLevel,
                micLevel = micLevel,
                isMicMuted = audioConfig.isMicMuted
            )
        }

        // Quick Scene Switcher
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "ACTIVE SCENE PRESET",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextSecondary
                )
                Text(
                    text = "EDIT SCENES →",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = ViperGreen,
                    modifier = Modifier.clickable { onNavigateToSceneEditor() }
                )
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                scenes.forEach { scene ->
                    val isSelected = activeScene?.id == scene.id
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) ViperGreen else Color(0xFF131A28))
                            .border(1.dp, if (isSelected) ViperGreen else Color(0xFF243046), RoundedCornerShape(8.dp))
                            .clickable { viewModel.selectScene(scene) }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = scene.name,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) Color.Black else TextPrimary
                        )
                    }
                }
            }
        }

        // Studio Quick Tool Links
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = onNavigateToStudio,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary)
            ) {
                Icon(Icons.Default.Layers, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Studio HUD", fontSize = 11.sp)
            }

            OutlinedButton(
                onClick = onNavigateToAlerts,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = ViperGreen)
            ) {
                Icon(Icons.Default.NotificationsActive, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Alerts", fontSize = 11.sp)
            }

            OutlinedButton(
                onClick = onNavigateToMirror,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = ViperCyan)
            ) {
                Icon(Icons.Default.CastConnected, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("PC Mirror", fontSize = 11.sp)
            }
        }

        // Device Optimization Card (Moto G96 5G & hardware detection)
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F1624)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Memory,
                        contentDescription = null,
                        tint = ViperGreen,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "DEVICE HARDWARE ACCELERATION PROFILE",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = ViperGreen
                    )
                }

                Text(
                    text = "${hwProfile.deviceModel} • ${hwProfile.androidVersion}",
                    fontSize = 12.sp,
                    color = TextPrimary,
                    fontWeight = FontWeight.SemiBold
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = if (hwProfile.hasHardwareAvc) ViperGreen else Color.Gray,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "H.264 HW: ${if (hwProfile.hasHardwareAvc) "ACTIVE" else "SW"}",
                            fontSize = 11.sp,
                            color = TextSecondary
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = if (hwProfile.hasHardwareHevc) ViperGreen else Color.Gray,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "H.265 HW: ${if (hwProfile.hasHardwareHevc) "SUPPORTED" else "N/A"}",
                            fontSize = 11.sp,
                            color = TextSecondary
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = if (hwProfile.hasAudioPlaybackCapture) ViperGreen else Color.Gray,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Game Audio: ${if (hwProfile.hasAudioPlaybackCapture) "OK" else "MIC ONLY"}",
                            fontSize = 11.sp,
                            color = TextSecondary
                        )
                    }
                }
            }
        }

        // Recent Recordings Peek
        if (recordings.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "RECENT RECORDINGS (${recordings.size})",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextSecondary
                    )
                    Text(
                        text = "VIEW ALL →",
                        fontSize = 11.sp,
                        color = ViperCyan,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable { onNavigateToRecorder() }
                    )
                }

                val recent = recordings.take(2)
                recent.forEach { item ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF131A28)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.VideoLibrary,
                                    contentDescription = null,
                                    tint = ViperCyan,
                                    modifier = Modifier.size(18.dp)
                                )
                                Column {
                                    Text(
                                        text = item.title,
                                        fontSize = 12.sp,
                                        color = TextPrimary,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "${item.durationSeconds}s • ${item.resolution} • ${(item.fileSizeBytes / (1024 * 1024)).toInt()} MB",
                                        fontSize = 10.sp,
                                        color = TextMuted
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}
