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
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    val clipboardManager = LocalClipboardManager.current

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

        // Live Viewfinder Canvas
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
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
                    fontSize = 18.sp,
                    color = Color(0xFF334155),
                    letterSpacing = 2.sp
                )
                Text(
                    text = if (streamConfig.isLandscape) "LANDSCAPE 16:9 • ${streamConfig.resolution.label}" else "PORTRAIT • ${streamConfig.resolution.label}",
                    fontSize = 11.sp,
                    color = Color(0xFF475569)
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
                        .size(width = 110.dp, height = 75.dp)
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
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(8.dp)
                        .fillMaxWidth(0.55f)
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
                        contentDescription = "Toggle Cam",
                        tint = if (isCameraActive) ViperGreen else Color.Gray,
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
                        contentDescription = "Toggle Chat",
                        tint = if (isChatVisible) ViperGreen else Color.Gray,
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
