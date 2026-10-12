package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.LayerType
import com.example.data.model.SceneLayer
import com.example.ui.ViperStudioViewModel
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.ViperCyan
import com.example.ui.theme.ViperGreen
import com.example.ui.theme.ViperSurfaceCard
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SceneEditorScreen(
    viewModel: ViperStudioViewModel,
    modifier: Modifier = Modifier
) {
    val scenes by viewModel.scenes.collectAsState()
    val activeScene by viewModel.activeScene.collectAsState()
    val selectedLayerId by viewModel.selectedLayerId.collectAsState()

    var showNewSceneDialog by remember { mutableStateOf(false) }
    var newSceneName by remember { mutableStateOf("") }
    var showAddLayerDialog by remember { mutableStateOf(false) }

    val selectedLayer = activeScene?.layers?.firstOrNull { it.id == selectedLayerId }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0A0E17))
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Scene Preset Selector Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "SCENE PRESETS",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = TextSecondary
            )
            Button(
                onClick = { showNewSceneDialog = true },
                colors = ButtonDefaults.buttonColors(containerColor = ViperGreen, contentColor = Color.Black),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.height(34.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("New Scene", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            scenes.forEach { scene ->
                val isSelected = activeScene?.id == scene.id
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isSelected) ViperGreen else Color(0xFF131A28))
                        .border(1.dp, if (isSelected) ViperGreen else Color(0xFF243046), RoundedCornerShape(8.dp))
                        .clickable { viewModel.selectScene(scene) }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = scene.name,
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) Color.Black else TextPrimary
                    )
                }
            }
        }

        // WYSIWYG Interactive Drag Canvas
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0C121E)),
            shape = RoundedCornerShape(14.dp)
        ) {
            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "DRAG & POSITION CANVAS (16:9)",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = ViperGreen
                    )
                    Text(
                        text = "Touch layer to select & drag",
                        fontSize = 10.sp,
                        color = TextMuted
                    )
                }

                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(10.dp))
                        .border(1.dp, Color(0xFF263349), RoundedCornerShape(10.dp))
                        .background(Color(0xFF070B12))
                ) {
                    val canvasWidth = maxWidth
                    val canvasHeight = maxHeight

                    activeScene?.layers?.sortedBy { it.zIndex }?.forEach { layer ->
                        if (layer.isVisible) {
                            val isSelected = layer.id == selectedLayerId

                            val xOffset = (canvasWidth * (layer.xPct / 100f))
                            val yOffset = (canvasHeight * (layer.yPct / 100f))
                            val layerW = (canvasWidth * (layer.widthPct / 100f))
                            val layerH = (canvasHeight * (layer.heightPct / 100f))

                            Box(
                                modifier = Modifier
                                    .offset { IntOffset(xOffset.roundToPx(), yOffset.roundToPx()) }
                                    .size(width = layerW, height = layerH)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(
                                        when (layer.type) {
                                            LayerType.GAME_SCREEN -> Color(0xFF131B2A)
                                            LayerType.CAMERA_PIP -> Color(0xFF00E676).copy(alpha = 0.25f)
                                            LayerType.TEXT_BANNER -> Color(0xFF00E5FF).copy(alpha = 0.3f)
                                            LayerType.ALERT_BOX -> Color(0xFFFFD600).copy(alpha = 0.25f)
                                            else -> Color(0xFFB388FF).copy(alpha = 0.25f)
                                        }
                                    )
                                    .border(
                                        width = if (isSelected) 2.dp else 1.dp,
                                        color = if (isSelected) ViperCyan else Color(0x66FFFFFF),
                                        shape = RoundedCornerShape(6.dp)
                                    )
                                    .clickable { viewModel.selectLayer(layer.id) }
                                    .pointerInput(layer.id, layer.isLocked) {
                                        if (!layer.isLocked) {
                                            detectDragGestures { change, dragAmount ->
                                                change.consume()
                                                val deltaXPct = (dragAmount.x / canvasWidth.toPx()) * 100f
                                                val deltaYPct = (dragAmount.y / canvasHeight.toPx()) * 100f
                                                viewModel.updateLayerPosition(
                                                    layer.id,
                                                    layer.xPct + deltaXPct,
                                                    layer.yPct + deltaYPct
                                                )
                                            }
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = layer.label,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) ViperCyan else Color.White
                                    )
                                    if (layer.textContent.isNotEmpty()) {
                                        Text(
                                            text = layer.textContent.take(18),
                                            fontSize = 8.sp,
                                            color = Color.LightGray
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Layer Controls (Add / Hierarchy)
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
                    Text(
                        text = "LAYER STACK & HIERARCHY",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextSecondary
                    )
                    Button(
                        onClick = { showAddLayerDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = ViperCyan, contentColor = Color.Black),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.height(30.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add Layer", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }

                activeScene?.layers?.sortedByDescending { it.zIndex }?.forEach { layer ->
                    val isSelected = layer.id == selectedLayerId
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) Color(0xFF1B2538) else Color(0xFF101622))
                            .border(1.dp, if (isSelected) ViperCyan else Color(0xFF222E42), RoundedCornerShape(8.dp))
                            .clickable { viewModel.selectLayer(layer.id) }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(
                                imageVector = Icons.Default.Layers,
                                contentDescription = null,
                                tint = if (isSelected) ViperCyan else TextMuted,
                                modifier = Modifier.size(16.dp)
                            )
                            Column {
                                Text(
                                    text = layer.label,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) ViperCyan else TextPrimary
                                )
                                Text(
                                    text = "Pos: (${layer.xPct.toInt()}%, ${layer.yPct.toInt()}%) • Size: ${layer.widthPct.toInt()}%",
                                    fontSize = 9.sp,
                                    color = TextMuted
                                )
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = { viewModel.toggleLayerLock(layer.id) },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = if (layer.isLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                                    contentDescription = "Lock",
                                    tint = if (layer.isLocked) Color(0xFFFFD600) else TextMuted,
                                    modifier = Modifier.size(14.dp)
                                )
                            }

                            IconButton(
                                onClick = { viewModel.toggleLayerVisibility(layer.id) },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = if (layer.isVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = "Visibility",
                                    tint = if (layer.isVisible) ViperGreen else TextMuted,
                                    modifier = Modifier.size(14.dp)
                                )
                            }

                            IconButton(
                                onClick = { viewModel.removeLayer(layer.id) },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Delete",
                                    tint = Color(0xFFFF5252),
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Selected Layer Inspector (Size sliders & properties)
        if (selectedLayer != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = ViperSurfaceCard),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "LAYER INSPECTOR: ${selectedLayer.label.uppercase()}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = ViperCyan
                    )

                    // Width Slider
                    Column {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Width Scaling", fontSize = 11.sp, color = TextPrimary)
                            Text("${selectedLayer.widthPct.toInt()}%", fontSize = 11.sp, color = ViperCyan, fontWeight = FontWeight.Bold)
                        }
                        Slider(
                            value = selectedLayer.widthPct,
                            onValueChange = { viewModel.updateLayerSize(selectedLayer.id, it, selectedLayer.heightPct) },
                            valueRange = 10f..100f,
                            colors = SliderDefaults.colors(thumbColor = ViperCyan, activeTrackColor = ViperCyan)
                        )
                    }

                    // Height Slider
                    Column {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Height Scaling", fontSize = 11.sp, color = TextPrimary)
                            Text("${selectedLayer.heightPct.toInt()}%", fontSize = 11.sp, color = ViperCyan, fontWeight = FontWeight.Bold)
                        }
                        Slider(
                            value = selectedLayer.heightPct,
                            onValueChange = { viewModel.updateLayerSize(selectedLayer.id, selectedLayer.widthPct, it) },
                            valueRange = 5f..100f,
                            colors = SliderDefaults.colors(thumbColor = ViperCyan, activeTrackColor = ViperCyan)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }

    // New Scene Dialog
    if (showNewSceneDialog) {
        AlertDialog(
            onDismissRequest = { showNewSceneDialog = false },
            title = { Text("Create New Scene Preset", color = TextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = newSceneName,
                    onValueChange = { newSceneName = it },
                    label = { Text("Scene Name") },
                    placeholder = { Text("e.g. Tournament Match HUD") },
                    singleLine = true
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newSceneName.isNotBlank()) {
                            viewModel.createNewScene(newSceneName)
                            newSceneName = ""
                            showNewSceneDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ViperGreen, contentColor = Color.Black)
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { showNewSceneDialog = false }) {
                    Text("Cancel", color = TextMuted)
                }
            },
            containerColor = Color(0xFF141C2B)
        )
    }

    // Add Layer Dialog
    if (showAddLayerDialog) {
        AlertDialog(
            onDismissRequest = { showAddLayerDialog = false },
            title = { Text("Select Layer Type to Add", color = TextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    LayerType.values().forEach { type ->
                        Button(
                            onClick = {
                                viewModel.addLayer(type)
                                showAddLayerDialog = false
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1B2436), contentColor = TextPrimary)
                        ) {
                            Text(type.displayName, fontSize = 12.sp)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showAddLayerDialog = false }) {
                    Text("Close", color = TextMuted)
                }
            },
            containerColor = Color(0xFF141C2B)
        )
    }
}
