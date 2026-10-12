package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SdCard
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.Videocam
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.example.data.model.FpsOption
import com.example.data.model.RecordingItem
import com.example.data.model.ResolutionOption
import com.example.ui.ViperStudioViewModel
import com.example.ui.theme.RecordingRed
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.ViperCyan
import com.example.ui.theme.ViperGreen
import com.example.ui.theme.ViperSurfaceCard
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RecorderScreen(
    viewModel: ViperStudioViewModel,
    onStartRecordRequest: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val isRecording by viewModel.isRecording.collectAsState()
    val isPaused by viewModel.isRecordingPaused.collectAsState()
    val durationSec by viewModel.recordingDurationSec.collectAsState()
    val estimatedSizeBytes by viewModel.estimatedRecordingSizeBytes.collectAsState()
    val availableStorageGb by viewModel.availableStorageGb.collectAsState()
    val lastSavedRecording by viewModel.lastSavedRecording.collectAsState()
    val streamConfig by viewModel.streamConfig.collectAsState()
    val recordings by viewModel.recordingsList.collectAsState()
    val context = LocalContext.current

    val infiniteTransition = rememberInfiniteTransition(label = "rec_pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(600),
            repeatMode = RepeatMode.Reverse
        ),
        label = "rec_alpha"
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0A0E17))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Saved Confirmation Banner (Movies/VIPER STUDIO)
        AnimatedVisibility(
            visible = lastSavedRecording != null,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            lastSavedRecording?.let { savedItem ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0F2B1E)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, ViperGreen),
                    shape = RoundedCornerShape(12.dp)
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
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = ViperGreen, modifier = Modifier.size(20.dp))
                                Text(
                                    text = "RECORDING SAVED TO MOVIES/VIPER STUDIO",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Black,
                                    color = ViperGreen,
                                    letterSpacing = 1.sp
                                )
                            }
                            IconButton(
                                onClick = { viewModel.dismissSavedRecordingBanner() },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Close", tint = TextMuted, modifier = Modifier.size(16.dp))
                            }
                        }

                        Text(
                            text = savedItem.filePath,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${savedItem.durationSeconds}s • ${String.format(Locale.getDefault(), "%.1f", savedItem.fileSizeBytes / (1024.0 * 1024.0))} MB • ${savedItem.resolution}",
                                fontSize = 11.sp,
                                color = TextSecondary
                            )

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = {
                                        playRecordingFile(context, savedItem.filePath)
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = ViperGreen, contentColor = Color.Black),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.height(34.dp)
                                ) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("PLAY", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                }

                                OutlinedButton(
                                    onClick = {
                                        shareRecordingFile(context, savedItem.filePath)
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.height(34.dp)
                                ) {
                                    Icon(Icons.Default.Share, contentDescription = null, tint = ViperCyan, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("SHARE", color = ViperCyan, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Hero Recording Control Box
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = ViperSurfaceCard),
            shape = RoundedCornerShape(14.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Status Pill
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(if (isRecording) RecordingRed else Color.Gray)
                            .alpha(if (isRecording && !isPaused) pulseAlpha else 1f)
                    )
                    Text(
                        text = if (isRecording) (if (isPaused) "RECORDING PAUSED" else "RECORDING ACTIVE (MP4)") else "SCREEN RECORDER STANDBY",
                        fontWeight = FontWeight.Black,
                        fontSize = 12.sp,
                        color = if (isRecording) RecordingRed else ViperCyan,
                        letterSpacing = 1.sp
                    )
                }

                // Digital Timer Clock
                val hours = durationSec / 3600
                val mins = (durationSec % 3600) / 60
                val secs = durationSec % 60
                val timeFormatted = if (hours > 0) {
                    String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, mins, secs)
                } else {
                    String.format(Locale.getDefault(), "%02d:%02d", mins, secs)
                }

                Text(
                    text = timeFormatted,
                    fontSize = 42.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )

                // Storage and Size Indicators
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(Icons.Default.SdCard, contentDescription = null, tint = ViperCyan, modifier = Modifier.size(14.dp))
                        Text(
                            text = "${String.format(Locale.getDefault(), "%.1f", availableStorageGb)} GB Free",
                            fontSize = 11.sp,
                            color = TextSecondary
                        )
                    }

                    if (isRecording) {
                        Text(
                            text = "•",
                            fontSize = 12.sp,
                            color = TextMuted
                        )
                        Text(
                            text = "Size: ${String.format(Locale.getDefault(), "%.1f", estimatedSizeBytes / (1024.0 * 1024.0))} MB",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = ViperGreen
                        )
                    }

                    Text(
                        text = "•",
                        fontSize = 12.sp,
                        color = TextMuted
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(Icons.Default.Folder, contentDescription = null, tint = TextMuted, modifier = Modifier.size(14.dp))
                        Text(
                            text = "Movies/VIPER STUDIO",
                            fontSize = 11.sp,
                            color = TextMuted
                        )
                    }
                }

                // Main Recorder Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (!isRecording) {
                        Button(
                            onClick = onStartRecordRequest,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("start_recording_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = RecordingRed, contentColor = Color.White),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.FiberManualRecord, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("START RECORDING", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    } else {
                        // Pause / Resume
                        Button(
                            onClick = {
                                if (isPaused) viewModel.resumeScreenRecording() else viewModel.pauseScreenRecording()
                            },
                            modifier = Modifier.weight(1f).height(48.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E283A), contentColor = TextPrimary),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(if (isPaused) "RESUME" else "PAUSE", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }

                        // Stop & Save
                        Button(
                            onClick = { viewModel.stopScreenRecording() },
                            modifier = Modifier.weight(1f).height(48.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = RecordingRed, contentColor = Color.White),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Stop, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("STOP & SAVE", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        // Recording Settings Pills (Resolution & FPS)
        if (!isRecording) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = ViperSurfaceCard),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "RECORDING RESOLUTION & FPS",
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
                                    .background(if (isSelected) ViperCyan else Color(0xFF131A28))
                                    .border(1.dp, if (isSelected) ViperCyan else Color(0xFF243046), RoundedCornerShape(8.dp))
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
                                    .background(if (isSelected) ViperGreen else Color(0xFF131A28))
                                    .border(1.dp, if (isSelected) ViperGreen else Color(0xFF243046), RoundedCornerShape(8.dp))
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
        }

        // Saved Recordings List Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "SAVED MP4 RECORDINGS (${recordings.size})",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = TextSecondary
            )
        }

        if (recordings.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF0F1522)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Videocam,
                        contentDescription = null,
                        tint = Color(0xFF2D3C55),
                        modifier = Modifier.size(48.dp)
                    )
                    Text(
                        text = "No recordings saved yet",
                        color = TextSecondary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "Tap 'START RECORDING' to capture your mobile gameplay with internal audio to Movies/VIPER STUDIO.",
                        color = TextMuted,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(horizontal = 24.dp),
                        lineHeight = 15.sp
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(recordings, key = { it.id }) { item ->
                    RecordingCardItem(
                        item = item,
                        onPlay = { playRecordingFile(context, item.filePath) },
                        onShare = { shareRecordingFile(context, item.filePath) },
                        onDelete = { viewModel.deleteRecording(item) }
                    )
                }
            }
        }
    }
}

private fun playRecordingFile(context: android.content.Context, filePath: String) {
    try {
        val file = File(filePath)
        val uri = if (file.exists()) {
            try {
                FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
            } catch (_: Exception) {
                Uri.fromFile(file)
            }
        } else {
            Uri.parse(filePath)
        }
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "video/mp4")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        Toast.makeText(context, "Could not open video player: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

private fun shareRecordingFile(context: android.content.Context, filePath: String) {
    try {
        val file = File(filePath)
        val uri = try {
            FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        } catch (_: Exception) {
            Uri.fromFile(file)
        }
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "video/mp4"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(shareIntent, "Share MP4 Recording"))
    } catch (e: Exception) {
        Toast.makeText(context, "Could not share video: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

@Composable
private fun RecordingCardItem(
    item: RecordingItem,
    onPlay: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onPlay() },
        colors = CardDefaults.cardColors(containerColor = ViperSurfaceCard),
        shape = RoundedCornerShape(10.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF162030))
                        .clickable { onPlay() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Play",
                        tint = ViperGreen,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Column {
                    Text(
                        text = item.title,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    val dateFormatted = SimpleDateFormat("MMM dd, yyyy • HH:mm", Locale.getDefault()).format(Date(item.timestamp))
                    Text(
                        text = "$dateFormatted • ${item.resolution} • ${item.durationSeconds}s",
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                    Text(
                        text = "${String.format(Locale.getDefault(), "%.1f", item.fileSizeBytes / (1024.0 * 1024.0))} MB • Movies/VIPER STUDIO",
                        fontSize = 10.sp,
                        color = TextMuted
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(onClick = onPlay, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.PlayArrow, contentDescription = "Play", tint = ViperGreen, modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = onShare, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Share, contentDescription = "Share", tint = ViperCyan, modifier = Modifier.size(16.dp))
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFFFF5252), modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}
