package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.model.ChatMessage
import com.example.data.model.LiveChatSettings
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.ViperCyan
import com.example.ui.theme.ViperGreen

@Composable
fun LiveChatOverlay(
    messages: List<ChatMessage>,
    onSendMessage: (String) -> Unit,
    modifier: Modifier = Modifier,
    settings: LiveChatSettings = LiveChatSettings(),
    onDragDelta: ((Float, Float) -> Unit)? = null,
    onToggleLock: (() -> Unit)? = null
) {
    val listState = rememberLazyListState()
    var inputMessage by remember { mutableStateOf("") }
    val context = LocalContext.current

    val maxCount = settings.maxMessages.coerceIn(4, 20)
    val displayMessages = messages.takeLast(maxCount)

    LaunchedEffect(displayMessages.size) {
        if (displayMessages.isNotEmpty()) {
            listState.animateScrollToItem(displayMessages.size - 1)
        }
    }

    val bgOpacity = settings.backgroundOpacity.coerceIn(0.15f, 1.0f)
    val baseFontSize = settings.fontSizeSp.sp

    val dragModifier = if (!settings.isLocked && onDragDelta != null) {
        Modifier.pointerInput(Unit) {
            detectDragGestures { change, dragAmount ->
                change.consume()
                onDragDelta(dragAmount.x, dragAmount.y)
            }
        }
    } else {
        Modifier
    }

    Column(
        modifier = modifier
            .then(dragModifier)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF0B101A).copy(alpha = bgOpacity))
            .border(
                width = 1.dp,
                color = if (settings.isLocked) Color(0x33233048) else ViperCyan.copy(alpha = 0.5f),
                shape = RoundedCornerShape(12.dp)
            )
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Overlay Header with Title, Drag Handle & Lock state
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (!settings.isLocked && onDragDelta != null) {
                    Icon(
                        imageVector = Icons.Default.DragIndicator,
                        contentDescription = "Drag Handle",
                        tint = ViperCyan,
                        modifier = Modifier.size(14.dp)
                    )
                }
                Text(
                    text = "● LIVE CHAT",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = ViperGreen
                )
                Text(
                    text = "(${displayMessages.size})",
                    fontSize = 9.sp,
                    color = TextMuted
                )
            }

            if (onToggleLock != null) {
                IconButton(
                    onClick = onToggleLock,
                    modifier = Modifier.size(22.dp)
                ) {
                    Icon(
                        imageVector = if (settings.isLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                        contentDescription = if (settings.isLocked) "Locked" else "Unlocked",
                        tint = if (settings.isLocked) Color(0xFFFFD600) else TextMuted,
                        modifier = Modifier.size(12.dp)
                    )
                }
            }
        }

        // Messages list
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 60.dp, max = 150.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(displayMessages, key = { it.id }) { msg ->
                val badgeColor = try {
                    Color(android.graphics.Color.parseColor(msg.badgeColorHex))
                } catch (_: Exception) {
                    ViperGreen
                }

                if (msg.isSuperChat) {
                    // Super Chat special card
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xE6FFB300))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    AvatarThumbnail(avatarUrl = msg.avatarUrl, name = msg.sender)
                                    Text(
                                        text = msg.sender,
                                        fontSize = baseFontSize,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.Black
                                    )
                                }
                                Text(
                                    text = msg.amount,
                                    fontSize = baseFontSize,
                                    fontWeight = FontWeight.Black,
                                    color = Color.Black
                                )
                            }
                            Text(
                                text = msg.text,
                                fontSize = baseFontSize,
                                color = Color(0xFF261D00)
                            )
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0x55162235))
                            .padding(horizontal = 6.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        AvatarThumbnail(avatarUrl = msg.avatarUrl, name = msg.sender)
                        if (settings.showBadges && msg.badgeText.isNotBlank()) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(badgeColor.copy(alpha = 0.2f))
                                    .border(0.5.dp, badgeColor, RoundedCornerShape(3.dp))
                                    .padding(horizontal = 4.dp, vertical = 1.dp)
                            ) {
                                Text(
                                    text = msg.badgeText,
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = badgeColor
                                )
                            }
                        }
                        Text(
                            text = msg.sender,
                            fontSize = baseFontSize,
                            fontWeight = FontWeight.Bold,
                            color = badgeColor
                        )
                        Text(
                            text = msg.text,
                            fontSize = baseFontSize,
                            color = TextPrimary
                        )
                    }
                }
            }
        }

        // Send Input
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            OutlinedTextField(
                value = inputMessage,
                onValueChange = { inputMessage = it },
                placeholder = { Text("Chat as streamer...", fontSize = 10.sp, color = TextMuted) },
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 36.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = ViperGreen,
                    unfocusedBorderColor = Color(0xFF233048),
                    focusedContainerColor = Color(0xFF101726),
                    unfocusedContainerColor = Color(0xFF101726),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                singleLine = true,
                shape = RoundedCornerShape(6.dp)
            )

            IconButton(
                onClick = {
                    if (inputMessage.isNotBlank()) {
                        onSendMessage(inputMessage)
                        inputMessage = ""
                    }
                },
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(ViperGreen)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Send Chat",
                    tint = Color.Black,
                    modifier = Modifier.size(15.dp)
                )
            }
        }
    }
}

@Composable
fun AvatarThumbnail(avatarUrl: String, name: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    if (avatarUrl.isNotBlank()) {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(avatarUrl)
                .crossfade(true)
                .build(),
            contentDescription = "$name Avatar",
            contentScale = ContentScale.Crop,
            modifier = modifier
                .size(18.dp)
                .clip(CircleShape)
                .background(Color(0xFF1B2538))
        )
    } else {
        Box(
            modifier = modifier
                .size(18.dp)
                .clip(CircleShape)
                .background(Color(0xFF23334D)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = name.take(1).uppercase(),
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = ViperCyan
            )
        }
    }
}
