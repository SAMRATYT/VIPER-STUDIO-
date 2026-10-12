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
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.SwitchAccount
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
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
    val youTubeChannelInfo by viewModel.youTubeChannelInfo.collectAsState()
    val isYouTubeSyncing by viewModel.isYouTubeSyncing.collectAsState()
    val authError by viewModel.authError.collectAsState()
    val liveChatSettings by viewModel.liveChatSettings.collectAsState()
    val hwProfile = viewModel.hardwareProfile
    val context = LocalContext.current

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

        // Section 0: YouTube Channel & OAuth Synchronization
        // Section 0: Google Account & YouTube Live Synchronization
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = ViperSurfaceCard),
            shape = RoundedCornerShape(14.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.AccountCircle, contentDescription = null, tint = Color(0xFFFF0000), modifier = Modifier.size(20.dp))
                        Text(
                            text = "GOOGLE ACCOUNT & YOUTUBE SYNC",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    }
                    if (youTubeChannelInfo.isConnected) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = ViperGreen, modifier = Modifier.size(14.dp))
                            Text(
                                text = if (youTubeChannelInfo.isYouTubeAuthorized) "YOUTUBE LINKED" else "ACCOUNT LINKED",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = ViperGreen
                            )
                        }
                    }
                }

                // Authentication error warning
                if (authError != null) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF33141E)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("AUTHENTICATION MESSAGE", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFF5252))
                                Text(authError ?: "", fontSize = 11.sp, color = TextPrimary)
                            }
                            IconButton(onClick = { viewModel.clearAuthError() }) {
                                Text("✕", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                        }
                    }
                }

                if (!youTubeChannelInfo.isConnected) {
                    Text(
                        text = "Sign in with Google to select your Gmail account. VIPER STUDIO will securely authenticate with Firebase and Google OAuth 2.0 without auto-selecting previous credentials.",
                        fontSize = 11.sp,
                        color = TextSecondary
                    )

                    Button(
                        onClick = { viewModel.signInWithGoogle(context) },
                        enabled = !isYouTubeSyncing,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4285F4), contentColor = Color.White),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (isYouTubeSyncing) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Opening Account Picker...", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        } else {
                            Icon(Icons.Default.AccountCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Sign in with Google", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                } else {
                    // Connected Channel Details Card (Real user account info)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF101726))
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (youTubeChannelInfo.avatarUrl.isNotBlank()) {
                            AsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data(youTubeChannelInfo.avatarUrl)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = "User Avatar",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF26334D)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (youTubeChannelInfo.channelTitle.isNotBlank()) youTubeChannelInfo.channelTitle.take(1).uppercase() else "G",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = ViperCyan
                                )
                            }
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (youTubeChannelInfo.channelTitle.isNotBlank()) youTubeChannelInfo.channelTitle else youTubeChannelInfo.accountEmail,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            if (youTubeChannelInfo.accountEmail.isNotBlank()) {
                                Text(
                                    text = youTubeChannelInfo.accountEmail,
                                    fontSize = 10.sp,
                                    color = ViperCyan
                                )
                            }
                            if (youTubeChannelInfo.subscriberCountText.isNotBlank()) {
                                Text(
                                    text = "${youTubeChannelInfo.channelHandle} • ${youTubeChannelInfo.subscriberCountText}",
                                    fontSize = 10.sp,
                                    color = TextMuted
                                )
                            }
                        }
                    }

                    // YouTube Permissions Request Card
                    if (!youTubeChannelInfo.isYouTubeAuthorized) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1217)),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x33FF0000)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("YOUTUBE LIVE PERMISSIONS", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFF5252))
                                Text(
                                    "Request YouTube channel broadcast permissions to retrieve your stream keys and auto-detect live chats.",
                                    fontSize = 11.sp,
                                    color = TextSecondary
                                )
                                Button(
                                    onClick = { viewModel.connectYouTubePermissions() },
                                    enabled = !isYouTubeSyncing,
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF0000), contentColor = Color.White),
                                    shape = RoundedCornerShape(6.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.Link, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Authorize YouTube Live", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    // Active Broadcast Info
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF0D1422))
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "ACTIVE BROADCAST",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = ViperCyan
                        )
                        Text(
                            text = if (youTubeChannelInfo.activeBroadcastTitle.isNotBlank()) youTubeChannelInfo.activeBroadcastTitle else "Ready for live stream",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary
                        )
                        Text(
                            text = "RTMP Ingest: ${youTubeChannelInfo.rtmpIngestUrl}",
                            fontSize = 9.sp,
                            color = TextMuted
                        )
                    }

                    // Action buttons: Sync, Switch Account, Disconnect
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { viewModel.syncYouTubeBroadcast() },
                            enabled = !isYouTubeSyncing,
                            colors = ButtonDefaults.buttonColors(containerColor = ViperGreen, contentColor = Color.Black),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            if (isYouTubeSyncing) {
                                CircularProgressIndicator(modifier = Modifier.size(14.dp), color = Color.Black, strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(14.dp))
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Sync", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = { viewModel.switchGoogleAccount(context) },
                            enabled = !isYouTubeSyncing,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = ViperCyan),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1.3f)
                        ) {
                            Icon(Icons.Default.SwitchAccount, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Switch Account", fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                        }

                        OutlinedButton(
                            onClick = { viewModel.disconnectAccount(context) },
                            enabled = !isYouTubeSyncing,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFF5252)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.LinkOff, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Disconnect", fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
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
                        text = "VIDEO ENCODING CONFIGURATION",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }

                // Stream Orientation Setting (Landscape 16:9 vs Portrait 9:16)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("STREAM ORIENTATION", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TextMuted)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SettingPill(
                            label = "Landscape (16:9 - 1280×720)",
                            isSelected = streamConfig.isLandscape,
                            onClick = { viewModel.setOrientation(true) },
                            modifier = Modifier.weight(1f)
                        )
                        SettingPill(
                            label = "Portrait (9:16 - 720×1280)",
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

                // Video Codec Type (Hardware H.264 vs H.265)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("VIDEO CODEC", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TextMuted)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        VideoCodecType.values().forEach { codec ->
                            val isSupported = when (codec) {
                                VideoCodecType.H264_AVC -> hwProfile.hasHardwareAvc
                                VideoCodecType.H265_HEVC -> hwProfile.hasHardwareHevc
                            }
                            SettingPill(
                                label = "${codec.displayName} ${if (isSupported) "(HW)" else "(SW)"}",
                                isSelected = streamConfig.codec == codec,
                                onClick = { viewModel.setCodec(codec) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                // Resolution Selection
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("STREAM RESOLUTION", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TextMuted)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        ResolutionOption.values().forEach { res ->
                            SettingPill(
                                label = res.label,
                                isSelected = streamConfig.resolution == res,
                                onClick = { viewModel.setResolution(res) }
                            )
                        }
                    }
                }

                // FPS Selection
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("FRAME RATE (FPS)", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TextMuted)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FpsOption.values().forEach { fps ->
                            SettingPill(
                                label = "${fps.value} FPS ${if (fps.value == 60) "(Pro)" else "(Standard)"}",
                                isSelected = streamConfig.fps == fps,
                                onClick = { viewModel.setFps(fps) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                // Bitrate Slider (1,000 kbps to 12,000 kbps)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("TARGET VIDEO BITRATE", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TextMuted)
                        Text("${streamConfig.bitrateKbps} kbps", fontSize = 11.sp, color = ViperGreen, fontWeight = FontWeight.Bold)
                    }
                    Slider(
                        value = streamConfig.bitrateKbps.toFloat(),
                        onValueChange = { viewModel.setBitrate(it.toInt()) },
                        valueRange = 1000f..12000f,
                        steps = 22,
                        colors = SliderDefaults.colors(thumbColor = ViperGreen, activeTrackColor = ViperGreen)
                    )
                }

                // Rate Control Mode
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("RATE CONTROL MODE", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TextMuted)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        RateControlMode.values().forEach { mode ->
                            SettingPill(
                                label = mode.displayName,
                                isSelected = streamConfig.rateControl == mode,
                                onClick = { viewModel.setRateControl(mode) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }

        // Section 2: Advanced Audio & Latency
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = ViperSurfaceCard),
            shape = RoundedCornerShape(14.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.GraphicEq, contentDescription = null, tint = ViperCyan, modifier = Modifier.size(18.dp))
                    Text(
                        text = "ADVANCED AUDIO & PIPELINE SYNC",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }

                // Independent Audio Source Controls
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Internal Game Audio (BGMI)", fontSize = 12.sp, color = TextPrimary, fontWeight = FontWeight.SemiBold)
                            Text(
                                text = if (audioConfig.isInternalAudioEnabled) "Active: Captures high-definition 48kHz game sound via MediaProjection" else "Disabled: Game audio muted completely from stream & recording",
                                fontSize = 10.sp,
                                color = if (audioConfig.isInternalAudioEnabled) ViperGreen else TextMuted
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Switch(
                            checked = audioConfig.isInternalAudioEnabled,
                            onCheckedChange = { viewModel.toggleInternalAudio() },
                            colors = SwitchDefaults.colors(checkedThumbColor = ViperGreen, checkedTrackColor = Color(0xFF00391A))
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Microphone Audio", fontSize = 12.sp, color = TextPrimary, fontWeight = FontWeight.SemiBold)
                            Text(
                                text = if (audioConfig.isMicAudioEnabled) "Active: Voice audio mixed with soft-clipping protection" else "Disabled: Microphone muted completely. Zero mic bleed into game sound",
                                fontSize = 10.sp,
                                color = if (audioConfig.isMicAudioEnabled) ViperCyan else TextMuted
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Switch(
                            checked = audioConfig.isMicAudioEnabled,
                            onCheckedChange = { viewModel.toggleMicAudio() },
                            colors = SwitchDefaults.colors(checkedThumbColor = ViperCyan, checkedTrackColor = Color(0xFF00363A))
                        )
                    }
                }

                // Audio Delay Adjustment Slider (-500ms to +500ms)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("AUDIO / VIDEO SYNC OFFSET (DELAY)", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TextMuted)
                        Text("${audioConfig.audioDelayMs} ms", fontSize = 11.sp, color = ViperCyan, fontWeight = FontWeight.Bold)
                    }
                    Slider(
                        value = audioConfig.audioDelayMs.toFloat(),
                        onValueChange = { viewModel.setAudioDelay(it.toInt()) },
                        valueRange = -500f..500f,
                        steps = 20,
                        colors = SliderDefaults.colors(thumbColor = ViperCyan, activeTrackColor = ViperCyan)
                    )
                }

                // Background Noise Reduction ON/OFF Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Background Noise Reduction",
                            fontSize = 12.sp,
                            color = TextPrimary,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = if (audioConfig.isNoiseSuppressionEnabled)
                                "ON: NoiseSuppressor active for mic input only. Preserves natural internal BGMI game audio."
                            else
                                "OFF: Natural raw microphone and game audio. No processing applied.",
                            fontSize = 10.sp,
                            color = if (audioConfig.isNoiseSuppressionEnabled) ViperGreen else TextMuted
                        )
                        if (!hwProfile.hasNoiseSuppressor) {
                            Text(
                                text = "Notice: Hardware NoiseSuppressor not detected on this chipset; graceful fallback active.",
                                fontSize = 9.sp,
                                color = Color(0xFFFFD600)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Switch(
                        checked = audioConfig.isNoiseSuppressionEnabled,
                        onCheckedChange = { viewModel.toggleNoiseSuppression() },
                        colors = SwitchDefaults.colors(checkedThumbColor = ViperGreen, checkedTrackColor = Color(0xFF00391A))
                    )
                }

                // Acoustic Echo Cancellation Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Acoustic Echo Cancellation", fontSize = 12.sp, color = TextPrimary, fontWeight = FontWeight.SemiBold)
                        Text("Prevents game speaker bleed into microphone", fontSize = 10.sp, color = TextMuted)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
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
