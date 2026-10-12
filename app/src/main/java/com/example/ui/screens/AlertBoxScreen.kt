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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import com.example.engine.audio.AlertSoundSynthesizer
import com.example.ui.ViperStudioViewModel
import com.example.ui.components.AlertOverlay
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.ViperCyan
import com.example.ui.theme.ViperGreen
import com.example.ui.theme.ViperSurfaceCard
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AlertBoxScreen(
    viewModel: ViperStudioViewModel,
    modifier: Modifier = Modifier
) {
    val activeAlert by viewModel.activeAlert.collectAsState()
    val alertHistory by viewModel.alertHistory.collectAsState()

    var alertDurationSec by remember { mutableFloatStateOf(4f) }
    var alertPosition by remember { mutableStateOf("CENTER") } // TOP, CENTER, BOTTOM

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0A0E17))
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "CUSTOM ALERT BOX STUDIO",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Black,
                    color = TextPrimary,
                    letterSpacing = 1.sp
                )
                Text(
                    text = "Streamlabs & Super Chat On-Screen Animation Engine",
                    fontSize = 11.sp,
                    color = TextMuted
                )
            }
        }

        // Live Alert Preview Stage
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0C1322)),
            shape = RoundedCornerShape(14.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "LIVE ALERT PREVIEW STAGE",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = ViperGreen
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .border(1.dp, Color(0xFF223048), RoundedCornerShape(10.dp))
                        .background(Color(0xFF070B13)),
                    contentAlignment = when (alertPosition) {
                        "TOP" -> Alignment.TopCenter
                        "BOTTOM" -> Alignment.BottomCenter
                        else -> Alignment.Center
                    }
                ) {
                    if (activeAlert != null) {
                        AlertOverlay(alert = activeAlert, modifier = Modifier.padding(8.dp))
                    } else {
                        Text(
                            text = "Tap any 'TEST ALERT' below to preview animation & synthesized audio chime.",
                            fontSize = 11.sp,
                            color = Color(0xFF334460),
                            modifier = Modifier.padding(16.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }
        }

        // Test Alert Action Buttons
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = ViperSurfaceCard),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "TEST BROADCAST ALERTS",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextSecondary
                )

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TestTriggerButton(
                        label = "Test Subscriber",
                        subtitle = "Fanfare Audio",
                        color = Color(0xFF00E676),
                        icon = Icons.Default.EmojiEvents,
                        onClick = { viewModel.triggerTestAlert(AlertType.SUBSCRIBER) }
                    )

                    TestTriggerButton(
                        label = "Test Super Chat ($20)",
                        subtitle = "Gold Coins Chime",
                        color = Color(0xFFFFD600),
                        icon = Icons.Default.MonetizationOn,
                        onClick = { viewModel.triggerTestAlert(AlertType.SUPER_CHAT) }
                    )

                    TestTriggerButton(
                        label = "Test Donation (₹500)",
                        subtitle = "Cyber Cash Ping",
                        color = Color(0xFF00E5FF),
                        icon = Icons.Default.CardGiftcard,
                        onClick = { viewModel.triggerTestAlert(AlertType.DONATION) }
                    )

                    TestTriggerButton(
                        label = "Test Follower",
                        subtitle = "Synth Bell",
                        color = Color(0xFFB388FF),
                        icon = Icons.Default.Favorite,
                        onClick = { viewModel.triggerTestAlert(AlertType.FOLLOWER) }
                    )
                }
            }
        }

        // Alert Sound Synthesizer Testing Pad
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = ViperSurfaceCard),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Default.VolumeUp, contentDescription = null, tint = ViperCyan, modifier = Modifier.size(16.dp))
                    Text(
                        text = "HARDWARE AUDIO CHIME SYNTHESIZER",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextSecondary
                    )
                }

                Text(
                    text = "High-energy procedural PCM tones synthesized directly via AudioTrack with zero asset lag.",
                    fontSize = 11.sp,
                    color = TextMuted
                )

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SoundPill("Fanfare", onClick = { AlertSoundSynthesizer.playFanfare() }, modifier = Modifier.weight(1f))
                    SoundPill("Super Chat", onClick = { AlertSoundSynthesizer.playSuperChatChime() }, modifier = Modifier.weight(1f))
                    SoundPill("Donation", onClick = { AlertSoundSynthesizer.playDonationPing() }, modifier = Modifier.weight(1f))
                    SoundPill("Bell", onClick = { AlertSoundSynthesizer.playFollowerBell() }, modifier = Modifier.weight(1f))
                }
            }
        }

        // Alert Display Settings (Duration & Position)
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = ViperSurfaceCard),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "ALERT DISPLAY & TIMING",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextSecondary
                )

                // Duration Slider
                Column {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Alert On-Screen Duration", fontSize = 12.sp, color = TextPrimary)
                        Text("${alertDurationSec.toInt()} seconds", fontSize = 12.sp, color = ViperGreen, fontWeight = FontWeight.Bold)
                    }
                    Slider(
                        value = alertDurationSec,
                        onValueChange = { alertDurationSec = it },
                        valueRange = 2f..10f,
                        colors = SliderDefaults.colors(thumbColor = ViperGreen, activeTrackColor = ViperGreen)
                    )
                }

                // Position Selector
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Display Position on Stream Canvas", fontSize = 12.sp, color = TextPrimary)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("TOP", "CENTER", "BOTTOM").forEach { pos ->
                            val isSel = alertPosition == pos
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSel) ViperGreen else Color(0xFF131A28))
                                    .border(1.dp, if (isSel) ViperGreen else Color(0xFF263348), RoundedCornerShape(8.dp))
                                    .clickable { alertPosition = pos }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = pos,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSel) Color.Black else TextPrimary
                                )
                            }
                        }
                    }
                }
            }
        }

        // Alert Event History Log
        if (alertHistory.isNotEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = ViperSurfaceCard),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Default.History, contentDescription = null, tint = TextMuted, modifier = Modifier.size(16.dp))
                        Text(
                            text = "RECENT ALERTS LOG (${alertHistory.size})",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextSecondary
                        )
                    }

                    alertHistory.take(5).forEach { item ->
                        val timeStr = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(item.timestamp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFF101726))
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = item.type.title.substringBefore("!"),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(android.graphics.Color.parseColor(item.colorHex))
                                )
                                Text(text = item.username, fontSize = 11.sp, color = TextPrimary)
                                if (item.amount.isNotEmpty()) {
                                    Text(text = "(${item.amount})", fontSize = 11.sp, color = Color(0xFFFFD600), fontWeight = FontWeight.Bold)
                                }
                            }
                            Text(text = timeStr, fontSize = 10.sp, color = TextMuted)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun TestTriggerButton(
    label: String,
    subtitle: String,
    color: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(color.copy(alpha = 0.12f))
            .border(1.dp, color, RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(imageVector = icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
            Column {
                Text(text = label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = color)
                Text(text = subtitle, fontSize = 9.sp, color = TextMuted)
            }
        }
    }
}

@Composable
private fun SoundPill(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xFF162032))
            .border(1.dp, Color(0xFF26364E), RoundedCornerShape(6.dp))
            .clickable { onClick() }
            .padding(vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text = label, fontSize = 10.sp, color = TextPrimary, fontWeight = FontWeight.SemiBold)
    }
}
