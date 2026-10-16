package com.example.ui.screens

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.model.LayerType
import com.example.data.model.SceneLayer
import com.example.data.model.ScenePreset
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.ViperCyan
import com.example.ui.theme.ViperGreen
import kotlin.math.roundToInt

/**
 * Interactive Visual Scene Canvas supporting free dragging, pinch-to-zoom,
 * corner/edge resize handles, aspect-ratio preservation, rotation, and live preview.
 */
@Composable
fun SceneCanvasView(
    activeScene: ScenePreset?,
    selectedLayerId: String?,
    isLandscape: Boolean,
    isLivePreviewMode: Boolean,
    onSelectLayer: (String?) -> Unit,
    onUpdatePosition: (layerId: String, newXPct: Float, newYPct: Float) -> Unit,
    onUpdateSize: (layerId: String, newWidthPct: Float, newHeightPct: Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    BoxWithConstraints(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .border(1.5.dp, if (isLivePreviewMode) ViperGreen else Color(0xFF263349), RoundedCornerShape(12.dp))
            .background(Color(0xFF070B12))
            .clickable(enabled = !isLivePreviewMode) { onSelectLayer(null) }
    ) {
        val canvasWidthPx = constraints.maxWidth.toFloat()
        val canvasHeightPx = constraints.maxHeight.toFloat()
        val density = LocalDensity.current

        val layers = activeScene?.layers?.sortedBy { it.zIndex } ?: emptyList()

        layers.forEach { layer ->
            if (layer.isVisible) {
                val isSelected = layer.id == selectedLayerId && !isLivePreviewMode

                val xPct = layer.getX(isLandscape)
                val yPct = layer.getY(isLandscape)
                val wPct = layer.getWidth(isLandscape)
                val hPct = layer.getHeight(isLandscape)

                val leftPx = (canvasWidthPx * (xPct / 100f))
                val topPx = (canvasHeightPx * (yPct / 100f))
                val widthPx = (canvasWidthPx * (wPct / 100f)).coerceAtLeast(20f)
                val heightPx = (canvasHeightPx * (hPct / 100f)).coerceAtLeast(15f)

                val layerAlpha = layer.opacity.coerceIn(0.05f, 1.0f)

                val layerWidthDp = with(density) { widthPx.toDp() }
                val layerHeightDp = with(density) { heightPx.toDp() }

                Box(
                    modifier = Modifier
                        .offset { IntOffset(leftPx.roundToInt(), topPx.roundToInt()) }
                        .size(width = layerWidthDp, height = layerHeightDp)
                        .rotate(layer.rotationDeg)
                        .alpha(layerAlpha)
                ) {
                    // Render Layer Content
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(6.dp))
                            .clickable(enabled = !isLivePreviewMode) { onSelectLayer(layer.id) }
                            .pointerInput(layer.id, layer.isLocked, isLivePreviewMode) {
                                if (!layer.isLocked && !isLivePreviewMode) {
                                    detectDragGestures { change, dragAmount ->
                                        change.consume()
                                        val deltaXPct = (dragAmount.x / canvasWidthPx) * 100f
                                        val deltaYPct = (dragAmount.y / canvasHeightPx) * 100f
                                        onUpdatePosition(
                                            layer.id,
                                            xPct + deltaXPct,
                                            yPct + deltaYPct
                                        )
                                    }
                                }
                            }
                            .pointerInput(layer.id, layer.isLocked, isLivePreviewMode) {
                                if (!layer.isLocked && !isLivePreviewMode) {
                                    detectTransformGestures { _, _, zoom, _ ->
                                        if (zoom != 1.0f) {
                                            val newW = (wPct * zoom).coerceIn(5f, 100f)
                                            val newH = if (layer.maintainAspectRatio) {
                                                (hPct * (newW / wPct)).coerceIn(4f, 100f)
                                            } else {
                                                (hPct * zoom).coerceIn(4f, 100f)
                                            }
                                            onUpdateSize(layer.id, newW, newH)
                                        }
                                    }
                                }
                            }
                    ) {
                        RenderLayerContent(
                            layer = layer,
                            isLandscape = isLandscape,
                            isSelected = isSelected,
                            isLivePreviewMode = isLivePreviewMode,
                            context = context
                        )
                    }

                    // Selection Border and Resize Handles
                    if (isSelected && !layer.isLocked) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .border(2.dp, ViperCyan, RoundedCornerShape(6.dp))
                        )

                        // 4 Corner Handles (Top-Left, Top-Right, Bottom-Left, Bottom-Right)
                        // Top-Left Handle
                        ResizeHandle(
                            modifier = Modifier.align(Alignment.TopStart),
                            onDrag = { dx, dy ->
                                val deltaXPct = (dx / canvasWidthPx) * 100f
                                val deltaYPct = (dy / canvasHeightPx) * 100f
                                val newW = (wPct - deltaXPct).coerceIn(5f, 100f)
                                val newH = if (layer.maintainAspectRatio && wPct > 0) {
                                    (hPct * (newW / wPct)).coerceIn(4f, 100f)
                                } else {
                                    (hPct - deltaYPct).coerceIn(4f, 100f)
                                }
                                onUpdatePosition(layer.id, xPct + deltaXPct, yPct + deltaYPct)
                                onUpdateSize(layer.id, newW, newH)
                            }
                        )

                        // Top-Right Handle
                        ResizeHandle(
                            modifier = Modifier.align(Alignment.TopEnd),
                            onDrag = { dx, dy ->
                                val deltaXPct = (dx / canvasWidthPx) * 100f
                                val deltaYPct = (dy / canvasHeightPx) * 100f
                                val newW = (wPct + deltaXPct).coerceIn(5f, 100f)
                                val newH = if (layer.maintainAspectRatio && wPct > 0) {
                                    (hPct * (newW / wPct)).coerceIn(4f, 100f)
                                } else {
                                    (hPct - deltaYPct).coerceIn(4f, 100f)
                                }
                                onUpdatePosition(layer.id, xPct, yPct + deltaYPct)
                                onUpdateSize(layer.id, newW, newH)
                            }
                        )

                        // Bottom-Left Handle
                        ResizeHandle(
                            modifier = Modifier.align(Alignment.BottomStart),
                            onDrag = { dx, dy ->
                                val deltaXPct = (dx / canvasWidthPx) * 100f
                                val deltaYPct = (dy / canvasHeightPx) * 100f
                                val newW = (wPct - deltaXPct).coerceIn(5f, 100f)
                                val newH = if (layer.maintainAspectRatio && wPct > 0) {
                                    (hPct * (newW / wPct)).coerceIn(4f, 100f)
                                } else {
                                    (hPct + deltaYPct).coerceIn(4f, 100f)
                                }
                                onUpdatePosition(layer.id, xPct + deltaXPct, yPct)
                                onUpdateSize(layer.id, newW, newH)
                            }
                        )

                        // Bottom-Right Handle (Primary Corner Handle)
                        ResizeHandle(
                            modifier = Modifier.align(Alignment.BottomEnd),
                            color = ViperGreen,
                            onDrag = { dx, dy ->
                                val deltaXPct = (dx / canvasWidthPx) * 100f
                                val deltaYPct = (dy / canvasHeightPx) * 100f
                                val newW = (wPct + deltaXPct).coerceIn(5f, 100f)
                                val newH = if (layer.maintainAspectRatio && wPct > 0) {
                                    (hPct * (newW / wPct)).coerceIn(4f, 100f)
                                } else {
                                    (hPct + deltaYPct).coerceIn(4f, 100f)
                                }
                                onUpdateSize(layer.id, newW, newH)
                            }
                        )
                    } else if (isSelected && layer.isLocked) {
                        // Locked outline in amber/yellow
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .border(1.5.dp, Color(0xFFFFD600), RoundedCornerShape(6.dp))
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ResizeHandle(
    modifier: Modifier = Modifier,
    color: Color = ViperCyan,
    onDrag: (dx: Float, dy: Float) -> Unit
) {
    Box(
        modifier = modifier
            .size(24.dp)
            .padding(2.dp)
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    onDrag(dragAmount.x, dragAmount.y)
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .shadow(3.dp, CircleShape)
                .background(color, CircleShape)
                .border(1.5.dp, Color.White, CircleShape)
        )
    }
}

@Composable
private fun RenderLayerContent(
    layer: SceneLayer,
    isLandscape: Boolean,
    isSelected: Boolean,
    isLivePreviewMode: Boolean,
    context: Context
) {
    when (layer.type) {
        LayerType.TEXT_BANNER -> {
            RenderTextLayerUI(layer)
        }
        LayerType.IMAGE_OVERLAY -> {
            RenderImageOrOverlayUI(layer, context)
        }
        LayerType.CAMERA_PIP -> {
            RenderCameraPipUI(layer)
        }
        LayerType.ALERT_BOX -> {
            RenderAlertBoxUI(layer)
        }
        LayerType.SOCIAL_BADGE -> {
            RenderSocialBadgeUI(layer)
        }
        LayerType.GAME_SCREEN -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF0C121E)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (isLandscape) "GAMEPLAY VIDEO CANVAS\n16:9 • 1080p 60FPS" else "VERTICAL GAMEPLAY CANVAS\n9:16 SHORTS",
                    fontSize = 10.sp,
                    color = Color(0xFF28364E),
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    lineHeight = 14.sp
                )
            }
        }
    }
}

@Composable
private fun RenderTextLayerUI(layer: SceneLayer) {
    val bgColor = try { Color(android.graphics.Color.parseColor(layer.bgColorHex)) } catch (_: Exception) { Color(0xB30F1722) }
    val textColor = try { Color(android.graphics.Color.parseColor(layer.textColorHex)) } catch (_: Exception) { Color(0xFF00E676) }
    val shadowColor = try { Color(android.graphics.Color.parseColor(layer.textShadowColorHex)) } catch (_: Exception) { Color.Black }

    val fontFamily = when (layer.textFontFamily.uppercase()) {
        "MONOSPACE" -> FontFamily.Monospace
        "SERIF" -> FontFamily.Serif
        "CURSIVE", "DISPLAY" -> FontFamily.Cursive
        else -> FontFamily.Default
    }
    val fontWeight = if (layer.isTextBold) FontWeight.Bold else FontWeight.Normal

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor, RoundedCornerShape(8.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        val textStyle = TextStyle(
            color = textColor,
            fontSize = layer.textFontSizeSp.sp,
            fontWeight = fontWeight,
            fontFamily = fontFamily,
            textAlign = TextAlign.Center,
            shadow = if (layer.hasTextShadow) Shadow(color = shadowColor, offset = Offset(2f, 2f), blurRadius = layer.textShadowRadius) else null
        )

        // Render outline simulation if enabled
        if (layer.hasTextOutline && layer.textOutlineWidth > 0f) {
            val outlineColor = try { Color(android.graphics.Color.parseColor(layer.textOutlineColorHex)) } catch (_: Exception) { Color.Black }
            Box(contentAlignment = Alignment.Center) {
                // 4 directional offsets for stroke effect
                val strokeOffset = (layer.textOutlineWidth * 0.8f).coerceIn(1f, 4f)
                Text(
                    text = layer.textContent.ifBlank { layer.label },
                    style = textStyle.copy(color = outlineColor, shadow = null),
                    modifier = Modifier.offset((-strokeOffset).dp, 0.dp)
                )
                Text(
                    text = layer.textContent.ifBlank { layer.label },
                    style = textStyle.copy(color = outlineColor, shadow = null),
                    modifier = Modifier.offset(strokeOffset.dp, 0.dp)
                )
                Text(
                    text = layer.textContent.ifBlank { layer.label },
                    style = textStyle.copy(color = outlineColor, shadow = null),
                    modifier = Modifier.offset(0.dp, (-strokeOffset).dp)
                )
                Text(
                    text = layer.textContent.ifBlank { layer.label },
                    style = textStyle.copy(color = outlineColor, shadow = null),
                    modifier = Modifier.offset(0.dp, strokeOffset.dp)
                )
                Text(
                    text = layer.textContent.ifBlank { layer.label },
                    style = textStyle
                )
            }
        } else {
            Text(
                text = layer.textContent.ifBlank { layer.label },
                style = textStyle
            )
        }
    }
}

@Composable
private fun RenderImageOrOverlayUI(layer: SceneLayer, context: Context) {
    val strokeColor = try { Color(android.graphics.Color.parseColor(layer.borderStrokeColorHex)) } catch (_: Exception) { Color(0xFF00E5FF) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .then(
                if (layer.borderStrokeWidthDp > 0f) {
                    Modifier.border(layer.borderStrokeWidthDp.dp, strokeColor, RoundedCornerShape(8.dp))
                } else Modifier
            ),
        contentAlignment = Alignment.Center
    ) {
        if (layer.imageUrl.isNotBlank()) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(layer.imageUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = layer.label,
                contentScale = if (layer.maintainAspectRatio) ContentScale.Fit else ContentScale.FillBounds,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            // Built-in Overlay Frame Presets UI
            when (layer.overlayPresetType) {
                "GAMING_BORDER" -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .border(3.dp, strokeColor, RoundedCornerShape(10.dp))
                    )
                }
                "WEBCAM_FRAME" -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .border(2.5.dp, strokeColor, RoundedCornerShape(8.dp))
                    ) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .offset(8.dp, (-6).dp)
                                .background(strokeColor, RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 1.dp)
                        ) {
                            Text("CAM", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                        }
                    }
                }
                "TOP_BAR" -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xE60A0E17), RoundedCornerShape(6.dp))
                            .border(1.dp, strokeColor, RoundedCornerShape(6.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(layer.label, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = strokeColor)
                    }
                }
                "BOTTOM_TICKER" -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xE60F1722), RoundedCornerShape(6.dp))
                            .border(1.dp, Color(0xFF00E676), RoundedCornerShape(6.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(layer.textContent.ifBlank { "VIPER STUDIO LIVE STREAM" }, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
                else -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0x33B388FF), RoundedCornerShape(6.dp))
                            .border(1.dp, Color(0x66B388FF), RoundedCornerShape(6.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(layer.label, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
private fun RenderCameraPipUI(layer: SceneLayer) {
    val shape = when (layer.pipShape) {
        "CIRCLE" -> CircleShape
        "SQUARE" -> RoundedCornerShape(4.dp)
        else -> RoundedCornerShape(12.dp)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(shape)
            .background(Color(0x4D00E676))
            .border(2.dp, Color(0xFF00E676), shape),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("FACECAM PiP", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Text(layer.pipShape, fontSize = 7.sp, color = Color(0xFF00E676))
        }
    }
}

@Composable
private fun RenderAlertBoxUI(layer: SceneLayer) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0x4DFFD600))
            .border(2.dp, Color(0xFFFFD600), RoundedCornerShape(10.dp))
            .padding(4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text("LIVE ALERT BOX", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFFD600))
    }
}

@Composable
private fun RenderSocialBadgeUI(layer: SceneLayer) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xD90E1624))
            .border(1.5.dp, Color(0xFF00E5FF), RoundedCornerShape(14.dp))
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(layer.textContent.ifBlank { "@VIPER_STREAM" }, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF00E5FF))
    }
}
