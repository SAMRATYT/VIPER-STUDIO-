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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.AudioCaptureMode
import com.example.data.model.FpsOption
import com.example.data.model.RateControlMode
import com.example.data.model.ResolutionOption
import com.example.data.model.VideoCodecType
import com.example.ui.ViperStudioViewModel
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.ViperCyan
import com.example.ui.theme.ViperGreen
import com.example.ui.theme.ViperSurfaceCard

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    viewModel: ViperStudioViewModel,
    modifier: Modifier = Modifier
) {
    val streamConfig by viewModel.streamConfig.collectAsState()
    val audioConfig by viewModel.audioConfig.collectAsState()
    val hwProfile = viewModel.hardwareProfile

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0A0E17))
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header
        Column {
            Text(
                text = "STUDIO ENCODER & HARDWARE SETTINGS",
                fontSize = 16.sp,
                fontWeight = FontWeight.Black,
                color = TextPrimary,
                letterSpacing = 1.sp
            )
            Text(
                text = "Precision Video Bitrate, Codec, Audio Delay, and Hardware Config",
                fontSize = 11.sp,
                color = TextMuted
            )
        }

        // Section 1: Video Encoding
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = ViperSurfaceCard),
            shape = RoundedCornerShape(14.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Videocam, contentDescription = null, tint = ViperGreen, modifier = Modifier.size(18.dp))
                    Text(
                        text = "VIDEO ENCODER & BITRATE",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = ViperGreen
                    )
                }

                // Stream Orientation (Landscape 16:9 vs Vertical 9:16)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Stream & Recording Orientation", fontSize = 11.sp, color = TextSecondary)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SettingPill(
                            label = "Landscape (16:9 • 1280×720)",
                            isSelected = streamConfig.isLandscape,
                            onClick = { viewModel.setOrientation(true) },
                            modifier = Modifier.weight(1f)
                        )
                        SettingPill(
                            label = "Portrait (9:16 • 720×1280)",
                            isSelected = !streamConfig.isLandscape,
                            onClick = { viewModel.setOrientation(false) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Text(
                        text = if (streamConfig.isLandscape)
                            "Standard 16:9 widescreen layout for YouTube Live and gaming streams."
                        else
                            "Vertical 9:16 layout for YouTube Shorts Live (720×1280 30/60FPS).",
                        fontSize = 10.sp,
                        color = ViperCyan
                    )
                }

                // Resolution
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Target Resolution", fontSize = 11.sp, color = TextSecondary)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        ResolutionOption.values().forEach { res ->
                            val isSel = streamConfig.resolution == res
                            SettingPill(
                                label = res.label,
                                isSelected = isSel,
                                onClick = { viewModel.setResolution(res) }
                            )
                        }
                    }
                }

                // FPS
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Frame Rate (FPS)", fontSize = 11.sp, color = TextSecondary)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FpsOption.values().forEach { fps ->
                            val isSel = streamConfig.fps == fps
                            SettingPill(
                                label = fps.label,
                                isSelected = isSel,
                                onClick = { viewModel.setFps(fps) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                // Bitrate Slider (500 to 12000 Kbps)
                Column {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Video Bitrate", fontSize = 12.sp, color = TextPrimary)
                        Text("${streamConfig.bitrateKbps} Kbps", fontSize = 12.sp, color = ViperGreen, fontWeight = FontWeight.Bold)
                    }
                    Slider(
                        value = streamConfig.bitrateKbps.toFloat(),
                        onValueChange = { viewModel.setBitrate(it.toInt()) },
                        valueRange = 500f..12000f,
                        colors = SliderDefaults.colors(thumbColor = ViperGreen, activeTrackColor = ViperGreen)
                    )
                    Text(
                        text = "Recommended for ${streamConfig.resolution.label.substringBefore(" ")}: ${streamConfig.resolution.recommendedBitrate} Kbps",
                        fontSize = 10.sp,
                        color = TextMuted
                    )
                }

                // Codec Selection (H.264 vs H.265)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Hardware Video Codec", fontSize = 11.sp, color = TextSecondary)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        VideoCodecType.values().forEach { codec ->
                            val isSel = streamConfig.codec == codec
                            SettingPill(
                                label = codec.displayName.substringBefore(" ("),
                                isSelected = isSel,
                                onClick = { viewModel.setCodec(codec) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                // Toggles: Adaptive Bitrate & Low Latency
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Adaptive Bitrate (ABR)", fontSize = 12.sp, color = TextPrimary, fontWeight = FontWeight.SemiBold)
                        Text("Automatically reduces bitrate during network drops", fontSize = 10.sp, color = TextMuted)
                    }
                    Switch(
                        checked = streamConfig.isAdaptiveBitrateEnabled,
                        onCheckedChange = { viewModel.toggleAdaptiveBitrate() },
                        colors = SwitchDefaults.colors(checkedThumbColor = ViperGreen, checkedTrackColor = Color(0xFF00391A))
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Ultra Low Latency Mode", fontSize = 12.sp, color = TextPrimary, fontWeight = FontWeight.SemiBold)
                        Text("Minimizes stream delay for real-time viewer interaction", fontSize = 10.sp, color = TextMuted)
                    }
                    Switch(
                        checked = streamConfig.isLowLatencyEnabled,
                        onCheckedChange = { viewModel.toggleLowLatency() },
                        colors = SwitchDefaults.colors(checkedThumbColor = ViperCyan, checkedTrackColor = Color(0xFF003D4D))
                    )
                }
            }
        }

        // Section 2: Advanced Audio & Sync Delay
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = ViperSurfaceCard),
            shape = RoundedCornerShape(14.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.GraphicEq, contentDescription = null, tint = ViperCyan, modifier = Modifier.size(18.dp))
                    Text(
                        text = "AUDIO SYNC & ENHANCEMENT",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = ViperCyan
                    )
                }

                // Audio Capture Mode Selection
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Audio Capture Source Mode", fontSize = 11.sp, color = TextSecondary)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AudioCaptureMode.values().forEach { mode ->
                            val isSel = audioConfig.audioCaptureMode == mode
                            SettingPill(
                                label = mode.shortLabel,
                                isSelected = isSel,
                                onClick = { viewModel.setAudioCaptureMode(mode) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                // Audio Delay Offset (-500ms to +500ms)
                Column {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Audio Delay Correction", fontSize = 12.sp, color = TextPrimary)
                        Text("${audioConfig.audioDelayMs} ms", fontSize = 12.sp, color = ViperCyan, fontWeight = FontWeight.Bold)
                    }
                    Slider(
                        value = audioConfig.audioDelayMs.toFloat(),
                        onValueChange = { viewModel.setAudioDelay(it.toInt()) },
                        valueRange = -500f..500f,
                        colors = SliderDefaults.colors(thumbColor = ViperCyan, activeTrackColor = ViperCyan)
                    )
                    Text(
                        text = "Use positive offset (+ms) if Bluetooth headset audio lags behind video.",
                        fontSize = 10.sp,
                        color = TextMuted
                    )
                }

                // Background Noise Reduction ON/OFF Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Background Noise Reduction", fontSize = 12.sp, color = TextPrimary, fontWeight = FontWeight.SemiBold)
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(if (audioConfig.isNoiseSuppressionEnabled) ViperGreen.copy(alpha = 0.2f) else Color.Gray.copy(alpha = 0.2f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = if (audioConfig.isNoiseSuppressionEnabled) "ON" else "OFF",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (audioConfig.isNoiseSuppressionEnabled) ViperGreen else TextMuted
                                )
                            }
                        }
                        Text(
                            text = if (audioConfig.isNoiseSuppressionEnabled)
                                "ON: Active hardware NoiseSuppressor on microphone only (internal game audio is untouched)"
                            else
                                "OFF: Raw unprocessed microphone audio with natural tone and zero audio filtering",
                            fontSize = 10.sp,
                            color = TextMuted
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Switch(
                        checked = audioConfig.isNoiseSuppressionEnabled,
                        onCheckedChange = { viewModel.toggleNoiseSuppression() },
                        colors = SwitchDefaults.colors(checkedThumbColor = ViperGreen, checkedTrackColor = Color(0xFF00391A))
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Acoustic Echo Cancellation", fontSize = 12.sp, color = TextPrimary, fontWeight = FontWeight.SemiBold)
                        Text("Prevents game speaker bleed into microphone", fontSize = 10.sp, color = TextMuted)
                    }
                    Switch(
                        checked = audioConfig.isEchoCancellationEnabled,
                        onCheckedChange = { viewModel.toggleEchoCancellation() },
                        colors = SwitchDefaults.colors(checkedThumbColor = ViperGreen, checkedTrackColor = Color(0xFF00391A))
                    )
                }
            }
        }

        // Section 3: Hardware Diagnostics & Device Profile
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F1522)),
            shape = RoundedCornerShape(14.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Memory, contentDescription = null, tint = TextPrimary, modifier = Modifier.size(18.dp))
                    Text(
                        text = "DEVICE DIAGNOSTICS & HARDWARE AUDIT",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }

                DiagRow("Target Device", hwProfile.deviceModel)
                DiagRow("OS Platform", hwProfile.androidVersion)
                DiagRow("Preferred Encoder", hwProfile.preferredVideoCodec)
                DiagRow("Hardware H.264 (AVC)", if (hwProfile.hasHardwareAvc) "AVAILABLE (Accelerated)" else "Software Only")
                DiagRow("Hardware H.265 (HEVC)", if (hwProfile.hasHardwareHevc) "AVAILABLE (Accelerated)" else "Not Detected")
                DiagRow("Internal Audio Capture", if (hwProfile.hasAudioPlaybackCapture) "SUPPORTED (API 29+)" else "RESTRICTED")
                DiagRow("Acoustic Echo Canceler", if (hwProfile.hasAcousticEchoCanceler) "CHIPSET SUPPORTED" else "UNAVAILABLE")
                DiagRow("Noise Suppressor", if (hwProfile.hasNoiseSuppressor) "CHIPSET SUPPORTED" else "UNAVAILABLE")
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun DiagRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, fontSize = 11.sp, color = TextSecondary)
        Text(text = value, fontSize = 11.sp, color = TextPrimary, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun SettingPill(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (isSelected) ViperGreen else Color(0xFF131A28))
            .border(1.dp, if (isSelected) ViperGreen else Color(0xFF263348), RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) Color.Black else TextPrimary
        )
    }
}
