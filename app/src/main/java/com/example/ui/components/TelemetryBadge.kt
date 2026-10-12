package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CellTower
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.StreamStats
import com.example.ui.theme.LiveRed
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.ViperGreen
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TelemetryBadgeBar(
    stats: StreamStats,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF0F1522))
            .border(1.dp, Color(0xFF232D42), RoundedCornerShape(10.dp))
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Live Status Indicator
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(if (stats.isStreaming) LiveRed else Color.Gray)
                        .alpha(if (stats.isStreaming) pulseAlpha else 1f)
                )
                Text(
                    text = if (stats.isStreaming) "LIVE BROADCAST" else if (stats.isRecording) "RECORDING" else "STUDIO READY",
                    color = if (stats.isStreaming) LiveRed else if (stats.isRecording) Color(0xFFFF9100) else ViperGreen,
                    fontWeight = FontWeight.Black,
                    fontSize = 11.sp,
                    letterSpacing = 1.sp
                )
            }

            // Uptime Clock
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Timer,
                    contentDescription = null,
                    tint = TextMuted,
                    modifier = Modifier.size(14.dp)
                )
                val hours = stats.uptimeSeconds / 3600
                val minutes = (stats.uptimeSeconds % 3600) / 60
                val seconds = stats.uptimeSeconds % 60
                val timeString = if (hours > 0) {
                    String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds)
                } else {
                    String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
                }
                Text(
                    text = timeString,
                    color = TextPrimary,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Telemetry Chips
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            StatChip(
                icon = Icons.Default.Speed,
                label = "BITRATE",
                value = if (stats.isStreaming) "${stats.currentBitrateKbps} Kbps" else "Target: ${stats.targetBitrateKbps}k",
                color = if (stats.currentBitrateKbps > 2000) Color(0xFF00E676) else Color(0xFFFFD600)
            )

            StatChip(
                icon = null,
                label = "FPS",
                value = if (stats.isStreaming) "${stats.currentFps} / ${stats.targetFps}" else "${stats.targetFps} FPS",
                color = Color(0xFF00E5FF)
            )

            StatChip(
                icon = Icons.Default.CellTower,
                label = "NET",
                value = stats.networkHealth.label.substringBefore(" "),
                color = Color(stats.networkHealth.hexColor)
            )

            if (stats.droppedFrames > 0) {
                StatChip(
                    icon = null,
                    label = "DROPPED",
                    value = "${stats.droppedFrames} (${((stats.droppedFrames.toDouble() / (stats.totalFrames + 1)) * 100).toInt()}%)",
                    color = Color(0xFFFF5252)
                )
            }

            StatChip(
                icon = Icons.Default.Thermostat,
                label = "TEMP",
                value = String.format(Locale.getDefault(), "%.1f°C", stats.deviceTempC),
                color = if (stats.deviceTempC > 42f) Color(0xFFFF5252) else Color(0xFF94A3B8)
            )
        }
    }
}

@Composable
private fun StatChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector?,
    label: String,
    value: String,
    color: Color
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xFF161E2D))
            .padding(horizontal = 6.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(11.dp)
            )
        }
        Text(
            text = "$label:",
            fontSize = 9.sp,
            color = TextMuted,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = value,
            fontSize = 10.sp,
            color = color,
            fontWeight = FontWeight.Bold
        )
    }
}
