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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.SwitchAccount
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.model.FpsOption
import com.example.data.model.PlatformType
import com.example.data.model.ResolutionOption
import com.example.ui.ViperStudioViewModel
import com.example.ui.components.AlertOverlay
import com.example.ui.components.CameraPreviewView
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
fun LiveScreen(
    viewModel: ViperStudioViewModel,
    onStartLiveRequest: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val streamConfig by viewModel.streamConfig.collectAsState()
    val streamStats by viewModel.streamStats.collectAsState()
    val isCameraActive by viewModel.isCameraActive.collectAsState()
    val lensFacing by viewModel.cameraLensFacing.collectAsState()
    val activeAlert by viewModel.activeAlert.collectAsState()
    val chatMessages by viewModel.chatMessages.collectAsState()
    val isChatVisible by viewModel.isChatOverlayVisible.collectAsState()
    val liveChatSettings by viewModel.liveChatSettings.collectAsState()
    val youTubeChannelInfo by viewModel.youTubeChannelInfo.collectAsState()
    val isYouTubeSyncing by viewModel.isYouTubeSyncing.collectAsState()
    val authError by viewModel.authError.collectAsState()
    val audioConfig by viewModel.audioConfig.collectAsState()

    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    var isKeyVisible by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0A0E17))
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
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
                            text = "STREAM SETUP ERROR",
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

        // Telemetry Monitor
        TelemetryBadgeBar(stats = streamStats)

        // Live Viewfinder Canvas (Adapts to Landscape 16:9 or Portrait 9:16)
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
                .border(2.dp, if (streamStats.isStreaming) LiveRed else Color(0xFF202A3D), RoundedCornerShape(14.dp))
                .background(Color(0xFF0D1420))
        ) {
            // Simulated Game Stream Screen
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = streamConfig.gameCategory.uppercase(),
                    fontWeight = FontWeight.Black,
                    fontSize = if (streamConfig.isLandscape) 18.sp else 14.sp,
                    color = Color(0xFF334155),
                    letterSpacing = 2.sp
                )
                Text(
                    text = if (streamConfig.isLandscape) "LANDSCAPE 16:9 • ${streamConfig.resolution.label}" else "PORTRAIT 9:16 • 720×1280",
                    fontSize = 11.sp,
                    color = if (streamConfig.isLandscape) Color(0xFF475569) else ViperCyan,
                    fontWeight = FontWeight.SemiBold
                )
            }

            // Embedded Camera PiP Preview
            if (isCameraActive) {
                CameraPreviewView(
                    lensFacing = lensFacing,
                    isCameraActive = isCameraActive,
                    shapeType = "ROUNDED",
                    onSwitchCamera = { viewModel.switchCameraLens() },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(10.dp)
                        .size(width = if (streamConfig.isLandscape) 110.dp else 80.dp, height = if (streamConfig.isLandscape) 75.dp else 60.dp)
                )
            }

            // Live Alert Box Overlay in viewfinder
            AlertOverlay(
                alert = activeAlert,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(8.dp)
            )

            // Live Chat Overlay in viewfinder
            if (isChatVisible) {
                LiveChatOverlay(
                    messages = chatMessages,
                    onSendMessage = { viewModel.sendChatMessage(it) },
                    settings = liveChatSettings,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(8.dp)
                        .fillMaxWidth(if (streamConfig.isLandscape) 0.55f else 0.85f)
                )
            }

            // Floating Quick Overlay Controls Bar
            Row(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                IconButton(
                    onClick = { viewModel.toggleCamera() },
                    modifier = Modifier
                        .size(32.dp)
                        .background(Color(0x99000000), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Cameraswitch,
                        contentDescription = "Camera",
                        tint = if (isCameraActive) ViperGreen else Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }

                IconButton(
                    onClick = { viewModel.toggleChatOverlay() },
                    modifier = Modifier
                        .size(32.dp)
                        .background(Color(0x99000000), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Chat,
                        contentDescription = "Chat",
                        tint = if (isChatVisible) ViperCyan else Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }

                IconButton(
                    onClick = { viewModel.toggleOrientation() },
                    modifier = Modifier
                        .size(32.dp)
                        .background(Color(0x99000000), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.ScreenRotation,
                        contentDescription = "Rotate",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        // Stream Orientation Selector (Landscape 16:9 vs Portrait 9:16)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (streamConfig.isLandscape) ViperGreen else Color(0xFF131A28))
                    .border(1.dp, if (streamConfig.isLandscape) ViperGreen else Color(0xFF263348), RoundedCornerShape(10.dp))
                    .clickable { viewModel.setOrientation(true) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(
                        Icons.Default.ScreenRotation,
                        contentDescription = null,
                        tint = if (streamConfig.isLandscape) Color.Black else ViperGreen,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "LANDSCAPE 16:9",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (streamConfig.isLandscape) Color.Black else TextPrimary
                    )
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (!streamConfig.isLandscape) ViperGreen else Color(0xFF131A28))
                    .border(1.dp, if (!streamConfig.isLandscape) ViperGreen else Color(0xFF263348), RoundedCornerShape(10.dp))
                    .clickable { viewModel.setOrientation(false) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(
                        Icons.Default.ScreenRotation,
                        contentDescription = null,
                        tint = if (!streamConfig.isLandscape) Color.Black else ViperCyan,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "PORTRAIT 9:16",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (!streamConfig.isLandscape) Color.Black else TextPrimary
                    )
                }
            }
        }

        // Audio Source Quick Selector (Internal Audio vs Microphone)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (audioConfig.isInternalAudioEnabled) Color(0xFF132A1C) else Color(0xFF131A28))
                    .border(1.dp, if (audioConfig.isInternalAudioEnabled) ViperGreen else Color(0xFF263348), RoundedCornerShape(10.dp))
                    .clickable { viewModel.toggleInternalAudio() }
                    .padding(vertical = 10.dp, horizontal = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(
                        Icons.Default.GraphicEq,
                        contentDescription = null,
                        tint = if (audioConfig.isInternalAudioEnabled) ViperGreen else TextMuted,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = if (audioConfig.isInternalAudioEnabled) "GAME AUDIO: ON" else "GAME AUDIO: OFF",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (audioConfig.isInternalAudioEnabled) ViperGreen else TextSecondary
                    )
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (audioConfig.isMicAudioEnabled) Color(0xFF0F2B30) else Color(0xFF131A28))
                    .border(1.dp, if (audioConfig.isMicAudioEnabled) ViperCyan else Color(0xFF263348), RoundedCornerShape(10.dp))
                    .clickable { viewModel.toggleMicAudio() }
                    .padding(vertical = 10.dp, horizontal = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(
                        if (audioConfig.isMicAudioEnabled && !audioConfig.isMicMuted) Icons.Default.Mic else Icons.Default.MicOff,
                        contentDescription = null,
                        tint = if (audioConfig.isMicAudioEnabled) ViperCyan else TextMuted,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = if (audioConfig.isMicAudioEnabled) "MIC: ON" else "MIC: OFF",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (audioConfig.isMicAudioEnabled) ViperCyan else TextSecondary
                    )
                }
            }
        }

        // GO LIVE Big Action Button
        Button(
            onClick = {
                if (streamStats.isStreaming) {
                    viewModel.stopBroadcast()
                } else {
                    onStartLiveRequest()
                }
            },
            enabled = !streamStats.isConnecting,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("live_go_live_button"),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (streamStats.isStreaming) LiveRed else ViperGreen,
                contentColor = Color.Black
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(
                imageVector = if (streamStats.isStreaming) Icons.Default.Stop else Icons.Default.PlayArrow,
                contentDescription = null,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (streamStats.isStreaming) "END BROADCAST" else if (streamStats.isConnecting) "STARTING STREAM..." else "GO LIVE NOW",
                fontWeight = FontWeight.Black,
                fontSize = 16.sp,
                letterSpacing = 1.sp
            )
        }

        // YouTube Channel Connection, Google OAuth & Account Selection Card
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
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.AccountCircle, contentDescription = null, tint = Color(0xFFFF0000), modifier = Modifier.size(18.dp))
                        Text(
                            text = "GOOGLE ACCOUNT & YOUTUBE LIVE SYNC",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextSecondary
                        )
                    }

                    if (youTubeChannelInfo.isConnected) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = ViperGreen, modifier = Modifier.size(13.dp))
                            Text(
                                text = if (youTubeChannelInfo.isYouTubeAuthorized) "YOUTUBE LINKED" else "GOOGLE CONNECTED",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = ViperGreen
                            )
                        }
                    }
                }

                // Authentication or Sync Error Banner
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
                                Text("AUTHENTICATION NOTICE", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFF5252))
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
                        text = "Sign in with your Google account to choose your Gmail account, connect YouTube live broadcasts, auto-retrieve stream keys, and sync live chat in real time.",
                        fontSize = 11.sp,
                        color = TextSecondary
                    )

                    // Requirement 1: Visible "Sign in with Google" button
                    // Requirement 2 & 3: Opens Google Account Picker, does not auto-select
                    Button(
                        onClick = { viewModel.signInWithGoogle(context) },
                        enabled = !isYouTubeSyncing,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4285F4), contentColor = Color.White),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("google_sign_in_button")
                    ) {
                        if (isYouTubeSyncing) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Opening Account Picker...", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        } else {
                            Icon(Icons.Default.AccountCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Sign in with Google", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                } else {
                    // Connected Account Profile Display (Requirement 7: Real user information, never fake data)
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
                                contentDescription = "Profile Avatar",
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

                    // Requirement 6: Connect YouTube Permissions only when requested
                    if (!youTubeChannelInfo.isYouTubeAuthorized) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1215)),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x33FF0000)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = "YOUTUBE BROADCAST PERMISSIONS",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFFF5252)
                                )
                                Text(
                                    text = "Authorize VIPER STUDIO to retrieve your real YouTube live stream key and enable automated chat synchronizer.",
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
                                    Icon(Icons.Default.Link, contentDescription = null, modifier = Modifier.size(15.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Connect YouTube Permissions", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    // Auto-Fill Stream Key and Sync buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { viewModel.applyYouTubeStreamKeyToConfig() },
                            colors = ButtonDefaults.buttonColors(containerColor = ViperCyan, contentColor = Color.Black),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Auto-Fill Key", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = { viewModel.syncYouTubeBroadcast() },
                            enabled = !isYouTubeSyncing,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1B2436), contentColor = TextPrimary),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            if (isYouTubeSyncing) {
                                CircularProgressIndicator(modifier = Modifier.size(12.dp), color = ViperGreen, strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(14.dp), tint = ViperGreen)
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Sync Broadcast", fontSize = 11.sp)
                        }
                    }

                    // Requirement 4: "Switch Google Account" and "Disconnect Account" buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { viewModel.switchGoogleAccount(context) },
                            enabled = !isYouTubeSyncing,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = ViperCyan),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("switch_google_account_button")
                        ) {
                            Icon(Icons.Default.SwitchAccount, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Switch Account", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }

                        OutlinedButton(
                            onClick = { viewModel.disconnectAccount(context) },
                            enabled = !isYouTubeSyncing,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFF5252)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("disconnect_google_account_button")
                        ) {
                            Icon(Icons.Default.LinkOff, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Disconnect", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }

        // Platform Selection Tabs
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = ViperSurfaceCard),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "SELECT STREAM PLATFORM",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextSecondary
                )

                TabRow(
                    selectedTabIndex = streamConfig.platform.ordinal,
                    containerColor = Color(0xFF101726),
                    contentColor = ViperGreen,
                    indicator = { tabPositions ->
                        TabRowDefaults.SecondaryIndicator(
                            Modifier.tabIndicatorOffset(tabPositions[streamConfig.platform.ordinal]),
                            color = ViperGreen
                        )
                    }
                ) {
                    PlatformType.values().forEach { platform ->
                        Tab(
                            selected = streamConfig.platform == platform,
                            onClick = { viewModel.setPlatform(platform) },
                            text = {
                                Text(
                                    text = platform.displayName.substringBefore(" "),
                                    fontSize = 11.sp,
                                    fontWeight = if (streamConfig.platform == platform) FontWeight.Bold else FontWeight.Normal,
                                    color = if (streamConfig.platform == platform) ViperGreen else TextMuted
                                )
                            }
                        )
                    }
                }

                // Platform Instructions
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = ViperGreen,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = streamConfig.platform.instructions,
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                }

                // RTMP Server URL Field
                OutlinedTextField(
                    value = streamConfig.rtmpUrl,
                    onValueChange = { viewModel.setRtmpUrl(it) },
                    label = { Text("RTMP / RTMPS Server URL", fontSize = 12.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ViperGreen,
                        unfocusedBorderColor = Color(0xFF263349),
                        focusedContainerColor = Color(0xFF0F1624),
                        unfocusedContainerColor = Color(0xFF0F1624),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    shape = RoundedCornerShape(8.dp)
                )

                // Stream Key Field with Hide/Show & Paste
                OutlinedTextField(
                    value = streamConfig.streamKey,
                    onValueChange = { viewModel.setStreamKey(it) },
                    label = { Text("Stream Key", fontSize = 12.sp) },
                    placeholder = { Text("Paste secret stream key from dashboard...", fontSize = 11.sp, color = TextMuted) },
                    modifier = Modifier.fillMaxWidth().testTag("stream_key_input"),
                    singleLine = true,
                    visualTransformation = if (isKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        Row {
                            IconButton(onClick = { isKeyVisible = !isKeyVisible }) {
                                Icon(
                                    imageVector = if (isKeyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = "Toggle Key",
                                    tint = TextSecondary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            IconButton(
                                onClick = {
                                    val clip = clipboardManager.getText()?.text
                                    if (!clip.isNullOrEmpty()) {
                                        viewModel.setStreamKey(clip)
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentPaste,
                                    contentDescription = "Paste Key",
                                    tint = ViperGreen,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ViperGreen,
                        unfocusedBorderColor = Color(0xFF263349),
                        focusedContainerColor = Color(0xFF0F1624),
                        unfocusedContainerColor = Color(0xFF0F1624),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    shape = RoundedCornerShape(8.dp)
                )
            }
        }

        // Stream Information (Title & Category)
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = ViperSurfaceCard),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "BROADCAST DETAILS",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextSecondary
                )

                OutlinedTextField(
                    value = streamConfig.streamTitle,
                    onValueChange = { viewModel.setStreamTitle(it) },
                    label = { Text("Stream Title", fontSize = 12.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ViperGreen,
                        unfocusedBorderColor = Color(0xFF263349),
                        focusedContainerColor = Color(0xFF0F1624),
                        unfocusedContainerColor = Color(0xFF0F1624),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    shape = RoundedCornerShape(8.dp)
                )

                OutlinedTextField(
                    value = streamConfig.gameCategory,
                    onValueChange = { viewModel.setGameCategory(it) },
                    label = { Text("Game Title / Category", fontSize = 12.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ViperGreen,
                        unfocusedBorderColor = Color(0xFF263349),
                        focusedContainerColor = Color(0xFF0F1624),
                        unfocusedContainerColor = Color(0xFF0F1624),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    shape = RoundedCornerShape(8.dp)
                )
            }
        }

        // Quick Quality Presets
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = ViperSurfaceCard),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "RESOLUTION & FPS PRESETS",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextSecondary
                )

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ResolutionOption.values().forEach { res ->
                        val isSelected = streamConfig.resolution == res
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) ViperGreen else Color(0xFF131A28))
                                .border(1.dp, if (isSelected) ViperGreen else Color(0xFF243046), RoundedCornerShape(8.dp))
                                .clickable { viewModel.setResolution(res) }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = res.label.substringBefore(" "),
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) Color.Black else TextPrimary
                            )
                        }
                    }

                    FpsOption.values().forEach { fps ->
                        val isSelected = streamConfig.fps == fps
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) Color(0xFF00E5FF) else Color(0xFF131A28))
                                .border(1.dp, if (isSelected) Color(0xFF00E5FF) else Color(0xFF243046), RoundedCornerShape(8.dp))
                                .clickable { viewModel.setFps(fps) }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "${fps.value} FPS",
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) Color.Black else TextPrimary
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}
