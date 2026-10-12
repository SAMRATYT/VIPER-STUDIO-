package com.example.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextSecondary

@Composable
fun DualChannelVUMeter(
    gameLevel: Float,
    micLevel: Float,
    isMicMuted: Boolean,
    isInternalAudioEnabled: Boolean = true,
    isMicAudioEnabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF0D131F))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Game Audio Channel
        ChannelBar(
            label = "GAME (BGMI)",
            level = if (!isInternalAudioEnabled) 0f else gameLevel,
            isMuted = !isInternalAudioEnabled,
            mutedText = "OFF",
            modifier = Modifier.weight(1f)
        )

        // Mic Channel
        ChannelBar(
            label = "MIC",
            level = if (isMicMuted || !isMicAudioEnabled) 0f else micLevel,
            isMuted = isMicMuted || !isMicAudioEnabled,
            mutedText = if (!isMicAudioEnabled) "OFF" else "MUTED",
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun ChannelBar(
    label: String,
    level: Float,
    isMuted: Boolean,
    mutedText: String = "MUTED",
    modifier: Modifier = Modifier
) {
    val animatedLevel by animateFloatAsState(
        targetValue = level.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 60),
        label = "vu_level"
    )

    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = if (isMuted) Color(0xFFFF5252) else TextSecondary
            )
            Text(
                text = if (isMuted) mutedText else "${(animatedLevel * 100).toInt()}%",
                fontSize = 10.sp,
                color = if (isMuted) Color(0xFFFF5252) else TextMuted
            )
        }

        Box(
            modifier = Modifier
                .padding(top = 4.dp)
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color(0xFF1E283A))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(if (isMuted) 0f else animatedLevel)
                    .clip(RoundedCornerShape(4.dp))
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                Color(0xFF00E676),
                                Color(0xFFFFD600),
                                Color(0xFFFF1744)
                            )
                        )
                    )
            )
        }
    }
}
