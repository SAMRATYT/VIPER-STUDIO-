package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AlignHorizontalCenter
import androidx.compose.material.icons.filled.AlignHorizontalLeft
import androidx.compose.material.icons.filled.AlignHorizontalRight
import androidx.compose.material.icons.filled.AlignVerticalBottom
import androidx.compose.material.icons.filled.AlignVerticalCenter
import androidx.compose.material.icons.filled.AlignVerticalTop
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FlipToBack
import androidx.compose.material.icons.filled.FlipToFront
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
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
    val streamConfig by viewModel.streamConfig.collectAsState()
    val scenes by viewModel.scenes.collectAsState()
    val activeScene by viewModel.activeScene.collectAsState()
    val selectedLayerId by viewModel.selectedLayerId.collectAsState()

    // Dialog states
    var showNewSceneDialog by remember { mutableStateOf(false) }
    var newSceneName by remember { mutableStateOf("") }
    var showDuplicateSceneDialog by remember { mutableStateOf(false) }
    var duplicateSceneName by remember { mutableStateOf("") }
    var showDeleteSceneDialog by remember { mutableStateOf(false) }

    var showAddTextDialog by remember { mutableStateOf(false) }
    var showAddOverlayDialog by remember { mutableStateOf(false) }
    var showDeleteLayerDialog by remember { mutableStateOf(false) }

    // Canvas Preview & Orientation Mode
    var isLivePreviewMode by remember { mutableStateOf(false) }
    var editorOrientationLandscape by remember { mutableStateOf(streamConfig.isLandscape) }

    val context = LocalContext.current
    val selectedLayer = activeScene?.layers?.firstOrNull { it.id == selectedLayerId }

    // Gallery Photo Picker for Custom Images
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.addCustomImageLayer(uri, "Custom Image")
        }
    }

    // Gallery Photo Picker for Custom Overlay Graphics / Frames
    val overlayPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.addCustomImageLayer(uri, "Stream Overlay")
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0A0E17))
            .verticalScroll(rememberScrollState())
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Preset Header & Actions
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "SCENE PRESETS",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = ViperCyan
                )
                Text(
                    text = "Auto-saved in real-time to internal storage",
                    fontSize = 10.sp,
                    color = TextMuted
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Button(
                    onClick = { showNewSceneDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = ViperGreen, contentColor = Color.Black),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("New Preset", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }

                if (activeScene != null) {
                    OutlinedButton(
                        onClick = {
                            duplicateSceneName = "${activeScene?.name} (Copy)"
                            showDuplicateSceneDialog = true
                        },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(13.dp))
                    }

                    if (scenes.size > 1) {
                        OutlinedButton(
                            onClick = { showDeleteSceneDialog = true },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFF5252)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(13.dp))
                        }
                    }
                }
            }
        }

        // Preset Chips
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            scenes.forEach { scene ->
                val isActive = scene.id == activeScene?.id
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isActive) ViperGreen else Color(0xFF131A28))
                        .border(1.dp, if (isActive) ViperGreen else Color(0xFF263348), RoundedCornerShape(8.dp))
                        .clickable { viewModel.selectScene(scene) }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (scene.isDefault) {
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = "Default Scene",
                                tint = if (isActive) Color.Black else Color(0xFFFFD600),
                                modifier = Modifier.size(12.dp)
                            )
                        }
                        Text(
                            text = scene.name,
                            fontSize = 11.sp,
                            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                            color = if (isActive) Color.Black else TextPrimary
                        )
                    }
                }
            }
        }

        // Canvas Mode & Orientation Bar
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = ViperSurfaceCard),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Orientation Switcher (16:9 Landscape vs 9:16 Portrait)
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF0F1522))
                        .padding(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (editorOrientationLandscape) ViperCyan else Color.Transparent)
                            .clickable { editorOrientationLandscape = true }
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = "16:9 Landscape",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (editorOrientationLandscape) Color.Black else TextSecondary
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (!editorOrientationLandscape) ViperCyan else Color.Transparent)
                            .clickable { editorOrientationLandscape = false }
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = "9:16 Shorts/Portrait",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (!editorOrientationLandscape) Color.Black else TextSecondary
                        )
                    }
                }

                // Live Preview Mode Toggle
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isLivePreviewMode) Color(0x3300E676) else Color(0xFF0F1522))
                        .border(1.dp, if (isLivePreviewMode) ViperGreen else Color(0xFF263348), RoundedCornerShape(8.dp))
                        .clickable { isLivePreviewMode = !isLivePreviewMode }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = if (isLivePreviewMode) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                        contentDescription = null,
                        tint = if (isLivePreviewMode) ViperGreen else TextMuted,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = if (isLivePreviewMode) "LIVE VIEW ON" else "PREVIEW",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isLivePreviewMode) ViperGreen else TextSecondary
                    )
                }
            }
        }

        // Add Layer Quick Bar
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = ViperSurfaceCard),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "ADD NEW ELEMENTS",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextSecondary
                )

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // 1. Add Image from Gallery
                    Button(
                        onClick = {
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1B273A), contentColor = ViperGreen),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add Image", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }

                    // 2. Add Text
                    Button(
                        onClick = { showAddTextDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1B273A), contentColor = ViperCyan),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Icon(Icons.Default.TextFields, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add Text", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }

                    // 3. Add Stream Overlay / Frame
                    Button(
                        onClick = { showAddOverlayDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1B273A), contentColor = Color(0xFFFFD600)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Icon(Icons.Default.ViewInAr, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add Overlay / Frame", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }

                    // 4. Add Facecam PiP
                    Button(
                        onClick = { viewModel.addLayer(LayerType.CAMERA_PIP) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1B273A), contentColor = Color(0xFFB388FF)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Icon(Icons.Default.Videocam, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Facecam PiP", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }

                    // 5. Add Live Alert Box
                    Button(
                        onClick = { viewModel.addLayer(LayerType.ALERT_BOX) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1B273A), contentColor = Color(0xFFFF9100)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Icon(Icons.Default.Star, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Alert Box", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Interactive Visual Canvas
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = ViperSurfaceCard),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (editorOrientationLandscape) "CANVAS (16:9 LANDSCAPE)" else "CANVAS (9:16 SHORTS)",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = ViperCyan
                    )

                    Text(
                        text = if (isLivePreviewMode) "Clean Live Stream Output" else "Pinch to zoom • Drag handles to resize",
                        fontSize = 9.sp,
                        color = TextMuted
                    )
                }

                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    val canvasModifier = if (editorOrientationLandscape) {
                        Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f)
                    } else {
                        Modifier
                            .fillMaxWidth(0.65f)
                            .aspectRatio(9f / 16f)
                    }

                    SceneCanvasView(
                        activeScene = activeScene,
                        selectedLayerId = selectedLayerId,
                        isLandscape = editorOrientationLandscape,
                        isLivePreviewMode = isLivePreviewMode,
                        onSelectLayer = { viewModel.selectLayer(it) },
                        onUpdatePosition = { id, x, y ->
                            viewModel.updateLayerPosition(id, x, y, editorOrientationLandscape)
                        },
                        onUpdateSize = { id, w, h ->
                            viewModel.updateLayerSize(id, w, h, editorOrientationLandscape)
                        },
                        modifier = canvasModifier
                    )
                }
            }
        }

        // Selected Layer Inspector Panel
        if (selectedLayer != null) {
            SelectedLayerInspectorCard(
                layer = selectedLayer,
                isLandscape = editorOrientationLandscape,
                viewModel = viewModel,
                onDelete = { showDeleteLayerDialog = true },
                onReplaceImage = {
                    photoPickerLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                }
            )
        }

        // Layer Stack & Hierarchy Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = ViperSurfaceCard),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "LAYER HIERARCHY & ORDER",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextSecondary
                )

                val sortedLayers = activeScene?.layers?.sortedByDescending { it.zIndex } ?: emptyList()
                if (sortedLayers.isEmpty()) {
                    Text("No layers added to this preset yet.", fontSize = 11.sp, color = TextMuted)
                } else {
                    sortedLayers.forEach { layer ->
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
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = when (layer.type) {
                                        LayerType.IMAGE_OVERLAY -> Icons.Default.Image
                                        LayerType.TEXT_BANNER -> Icons.Default.TextFields
                                        LayerType.CAMERA_PIP -> Icons.Default.Videocam
                                        else -> Icons.Default.Layers
                                    },
                                    contentDescription = null,
                                    tint = if (isSelected) ViperCyan else TextMuted,
                                    modifier = Modifier.size(16.dp)
                                )
                                Column {
                                    Text(
                                        text = layer.label,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (isSelected) ViperCyan else TextPrimary
                                    )
                                    Text(
                                        text = "z:${layer.zIndex} • ${if (editorOrientationLandscape) "L: ${layer.xPct.roundToInt()}%, ${layer.yPct.roundToInt()}%" else "P: ${layer.xPctPortrait.roundToInt()}%, ${layer.yPctPortrait.roundToInt()}%"}",
                                        fontSize = 9.sp,
                                        color = TextMuted
                                    )
                                }
                            }

                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                IconButton(
                                    onClick = { viewModel.moveLayerUp(layer.id) },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(Icons.Default.ArrowUpward, contentDescription = "Move Up", tint = TextSecondary, modifier = Modifier.size(14.dp))
                                }
                                IconButton(
                                    onClick = { viewModel.moveLayerDown(layer.id) },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(Icons.Default.ArrowDownward, contentDescription = "Move Down", tint = TextSecondary, modifier = Modifier.size(14.dp))
                                }
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
                            }
                        }
                    }
                }
            }
        }
    }

    // Dialog: Create New Preset
    if (showNewSceneDialog) {
        AlertDialog(
            onDismissRequest = { showNewSceneDialog = false },
            title = { Text("Create New Scene Preset", color = TextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = newSceneName,
                    onValueChange = { newSceneName = it },
                    label = { Text("Preset Name") },
                    placeholder = { Text("e.g., BGMI Tournament Overlay") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ViperGreen,
                        unfocusedBorderColor = Color(0xFF263348),
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    )
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newSceneName.isNotBlank()) {
                            viewModel.createNewScene(newSceneName.trim())
                            newSceneName = ""
                            showNewSceneDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ViperGreen, contentColor = Color.Black)
                ) {
                    Text("Create Preset")
                }
            },
            dismissButton = {
                TextButton(onClick = { showNewSceneDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = ViperSurfaceCard
        )
    }

    // Dialog: Duplicate Preset
    if (showDuplicateSceneDialog) {
        AlertDialog(
            onDismissRequest = { showDuplicateSceneDialog = false },
            title = { Text("Duplicate Scene Preset", color = TextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = duplicateSceneName,
                    onValueChange = { duplicateSceneName = it },
                    label = { Text("New Preset Name") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ViperCyan,
                        unfocusedBorderColor = Color(0xFF263348),
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    )
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val activeId = activeScene?.id
                        if (activeId != null && duplicateSceneName.isNotBlank()) {
                            viewModel.duplicateScenePreset(activeId, duplicateSceneName.trim())
                            showDuplicateSceneDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ViperCyan, contentColor = Color.Black)
                ) {
                    Text("Duplicate")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDuplicateSceneDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = ViperSurfaceCard
        )
    }

    // Dialog: Delete Scene Preset
    if (showDeleteSceneDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteSceneDialog = false },
            title = { Text("Delete Preset?", color = Color(0xFFFF5252), fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to delete '${activeScene?.name}'? This cannot be undone.", color = TextSecondary) },
            confirmButton = {
                Button(
                    onClick = {
                        activeScene?.id?.let { viewModel.deleteScenePreset(it) }
                        showDeleteSceneDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5252), contentColor = Color.White)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteSceneDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = ViperSurfaceCard
        )
    }

    // Dialog: Delete Layer Confirmation
    if (showDeleteLayerDialog && selectedLayer != null) {
        AlertDialog(
            onDismissRequest = { showDeleteLayerDialog = false },
            title = { Text("Delete Layer?", color = Color(0xFFFF5252), fontWeight = FontWeight.Bold) },
            text = { Text("Remove '${selectedLayer.label}' from this scene?", color = TextSecondary) },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.removeLayer(selectedLayer.id)
                        showDeleteLayerDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5252), contentColor = Color.White)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteLayerDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = ViperSurfaceCard
        )
    }

    // Dialog: Add Custom Text
    if (showAddTextDialog) {
        AddTextLayerDialog(
            onDismiss = { showAddTextDialog = false },
            onConfirm = { text, font, bold, colorHex ->
                viewModel.addCustomTextLayer(text, font, bold, colorHex)
                showAddTextDialog = false
            }
        )
    }

    // Dialog: Add Stream Overlay / Frame
    if (showAddOverlayDialog) {
        AddOverlayFrameDialog(
            onDismiss = { showAddOverlayDialog = false },
            onPickFromGallery = {
                showAddOverlayDialog = false
                overlayPickerLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            },
            onSelectPreset = { presetType, label, strokeHex ->
                viewModel.addCustomOverlayFrame(presetType, label, strokeHex)
                showAddOverlayDialog = false
            }
        )
    }
}

/**
 * Inspector Card with Tabs for Position/Size, Typography styling, and Layer ordering.
 */
@Composable
private fun SelectedLayerInspectorCard(
    layer: SceneLayer,
    isLandscape: Boolean,
    viewModel: ViperStudioViewModel,
    onDelete: () -> Unit,
    onReplaceImage: () -> Unit
) {
    var inspectorTab by remember { mutableStateOf(0) }

    val curX = layer.getX(isLandscape)
    val curY = layer.getY(isLandscape)
    val curW = layer.getWidth(isLandscape)
    val curH = layer.getHeight(isLandscape)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = ViperSurfaceCard),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // Header with Layer Name & Quick Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "SELECTED: ${layer.label.uppercase()}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = ViperCyan
                    )
                    Text(
                        text = "Editing for ${if (isLandscape) "16:9 Landscape" else "9:16 Shorts/Portrait"}",
                        fontSize = 9.sp,
                        color = TextMuted
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(
                        onClick = { viewModel.duplicateLayer(layer.id) },
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Duplicate", tint = ViperCyan, modifier = Modifier.size(16.dp))
                    }
                    IconButton(
                        onClick = { viewModel.toggleLayerLock(layer.id) },
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(
                            imageVector = if (layer.isLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                            contentDescription = "Lock",
                            tint = if (layer.isLocked) Color(0xFFFFD600) else TextMuted,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFFFF5252), modifier = Modifier.size(16.dp))
                    }
                }
            }

            // Tab Selector
            val tabs = when (layer.type) {
                LayerType.TEXT_BANNER -> listOf("Position & Size", "Text Typography", "Layer Order")
                LayerType.IMAGE_OVERLAY -> listOf("Position & Size", "Overlay Style", "Layer Order")
                else -> listOf("Position & Size", "Settings", "Layer Order")
            }

            TabRow(
                selectedTabIndex = inspectorTab,
                containerColor = Color(0xFF101724),
                contentColor = ViperCyan,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions[inspectorTab]),
                        color = ViperCyan
                    )
                }
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = inspectorTab == index,
                        onClick = { inspectorTab = index },
                        text = { Text(title, fontSize = 10.sp, fontWeight = FontWeight.Bold) }
                    )
                }
            }

            when (inspectorTab) {
                0 -> {
                    // TAB 0: Position, Sizing & Alignment
                    PositionAndSizeControls(
                        layer = layer,
                        isLandscape = isLandscape,
                        curX = curX,
                        curY = curY,
                        curW = curW,
                        curH = curH,
                        viewModel = viewModel
                    )
                }
                1 -> {
                    // TAB 1: Typography or Overlay Styling
                    if (layer.type == LayerType.TEXT_BANNER) {
                        TextTypographyControls(layer = layer, viewModel = viewModel)
                    } else if (layer.type == LayerType.IMAGE_OVERLAY) {
                        OverlayStylingControls(layer = layer, viewModel = viewModel, onReplaceImage = onReplaceImage)
                    } else {
                        GenericLayerControls(layer = layer, viewModel = viewModel)
                    }
                }
                2 -> {
                    // TAB 2: Layer Order & Z-Index
                    LayerOrderControls(layer = layer, viewModel = viewModel)
                }
            }
        }
    }
}

@Composable
private fun PositionAndSizeControls(
    layer: SceneLayer,
    isLandscape: Boolean,
    curX: Float,
    curY: Float,
    curW: Float,
    curH: Float,
    viewModel: ViperStudioViewModel
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Alignment Grid
        Text("QUICK ALIGNMENT", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = { viewModel.alignLayer(layer.id, "LEFT", isLandscape) }) {
                Icon(Icons.Default.AlignHorizontalLeft, contentDescription = "Left", tint = ViperCyan)
            }
            IconButton(onClick = { viewModel.alignLayer(layer.id, "CENTER_H", isLandscape) }) {
                Icon(Icons.Default.AlignHorizontalCenter, contentDescription = "Center H", tint = ViperCyan)
            }
            IconButton(onClick = { viewModel.alignLayer(layer.id, "RIGHT", isLandscape) }) {
                Icon(Icons.Default.AlignHorizontalRight, contentDescription = "Right", tint = ViperCyan)
            }
            IconButton(onClick = { viewModel.alignLayer(layer.id, "TOP", isLandscape) }) {
                Icon(Icons.Default.AlignVerticalTop, contentDescription = "Top", tint = ViperCyan)
            }
            IconButton(onClick = { viewModel.alignLayer(layer.id, "CENTER_V", isLandscape) }) {
                Icon(Icons.Default.AlignVerticalCenter, contentDescription = "Center V", tint = ViperCyan)
            }
            IconButton(onClick = { viewModel.alignLayer(layer.id, "BOTTOM", isLandscape) }) {
                Icon(Icons.Default.AlignVerticalBottom, contentDescription = "Bottom", tint = ViperCyan)
            }
        }

        Divider(color = Color(0xFF1E293B))

        // Position X & Y Sliders
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("X Position: ${curX.roundToInt()}%", fontSize = 11.sp, color = TextPrimary)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Button(
                    onClick = { viewModel.updateLayerPosition(layer.id, curX - 1f, curY, isLandscape) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF182335)),
                    modifier = Modifier.size(30.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
                ) { Text("-", fontSize = 14.sp) }
                Button(
                    onClick = { viewModel.updateLayerPosition(layer.id, curX + 1f, curY, isLandscape) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF182335)),
                    modifier = Modifier.size(30.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
                ) { Text("+", fontSize = 14.sp) }
            }
        }
        Slider(
            value = curX,
            onValueChange = { viewModel.updateLayerPosition(layer.id, it, curY, isLandscape) },
            valueRange = 0f..95f,
            colors = SliderDefaults.colors(thumbColor = ViperCyan, activeTrackColor = ViperCyan)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Y Position: ${curY.roundToInt()}%", fontSize = 11.sp, color = TextPrimary)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Button(
                    onClick = { viewModel.updateLayerPosition(layer.id, curX, curY - 1f, isLandscape) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF182335)),
                    modifier = Modifier.size(30.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
                ) { Text("-", fontSize = 14.sp) }
                Button(
                    onClick = { viewModel.updateLayerPosition(layer.id, curX, curY + 1f, isLandscape) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF182335)),
                    modifier = Modifier.size(30.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
                ) { Text("+", fontSize = 14.sp) }
            }
        }
        Slider(
            value = curY,
            onValueChange = { viewModel.updateLayerPosition(layer.id, curX, it, isLandscape) },
            valueRange = 0f..95f,
            colors = SliderDefaults.colors(thumbColor = ViperCyan, activeTrackColor = ViperCyan)
        )

        Divider(color = Color(0xFF1E293B))

        // Maintain Aspect Ratio Toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Lock Aspect Ratio", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                Text("Maintain proportions during resizing", fontSize = 9.sp, color = TextMuted)
            }
            Switch(
                checked = layer.maintainAspectRatio,
                onCheckedChange = { viewModel.updateLayerMaintainAspectRatio(layer.id, it) },
                colors = SwitchDefaults.colors(checkedThumbColor = ViperGreen, checkedTrackColor = Color(0x6600E676))
            )
        }

        // Width & Height Sliders
        Text("Width: ${curW.roundToInt()}%", fontSize = 11.sp, color = TextPrimary)
        Slider(
            value = curW,
            onValueChange = { newW ->
                val newH = if (layer.maintainAspectRatio && curW > 0) (curH * (newW / curW)).coerceIn(4f, 100f) else curH
                viewModel.updateLayerSize(layer.id, newW, newH, isLandscape)
            },
            valueRange = 5f..100f,
            colors = SliderDefaults.colors(thumbColor = ViperGreen, activeTrackColor = ViperGreen)
        )

        Text("Height: ${curH.roundToInt()}%", fontSize = 11.sp, color = TextPrimary)
        Slider(
            value = curH,
            onValueChange = { newH ->
                val newW = if (layer.maintainAspectRatio && curH > 0) (curW * (newH / curH)).coerceIn(5f, 100f) else curW
                viewModel.updateLayerSize(layer.id, newW, newH, isLandscape)
            },
            valueRange = 4f..100f,
            colors = SliderDefaults.colors(thumbColor = ViperGreen, activeTrackColor = ViperGreen)
        )

        Divider(color = Color(0xFF1E293B))

        // Rotation Controls
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Rotation: ${layer.rotationDeg.roundToInt()}°", fontSize = 11.sp, color = TextPrimary)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                OutlinedButton(
                    onClick = { viewModel.updateLayerRotation(layer.id, 0f) },
                    modifier = Modifier.height(28.dp)
                ) { Text("0° Reset", fontSize = 10.sp) }
                OutlinedButton(
                    onClick = { viewModel.updateLayerRotation(layer.id, (layer.rotationDeg + 90f) % 360f) },
                    modifier = Modifier.height(28.dp)
                ) { Text("+90°", fontSize = 10.sp) }
            }
        }
        Slider(
            value = layer.rotationDeg,
            onValueChange = { viewModel.updateLayerRotation(layer.id, it) },
            valueRange = -180f..180f,
            colors = SliderDefaults.colors(thumbColor = Color(0xFFFFD600), activeTrackColor = Color(0xFFFFD600))
        )
    }
}

@Composable
private fun TextTypographyControls(
    layer: SceneLayer,
    viewModel: ViperStudioViewModel
) {
    var textContent by remember(layer.id, layer.textContent) { mutableStateOf(layer.textContent) }
    var font by remember(layer.id, layer.textFontFamily) { mutableStateOf(layer.textFontFamily) }
    var fontSize by remember(layer.id, layer.textFontSizeSp) { mutableFloatStateOf(layer.textFontSizeSp) }
    var isBold by remember(layer.id, layer.isTextBold) { mutableStateOf(layer.isTextBold) }
    var hasOutline by remember(layer.id, layer.hasTextOutline) { mutableStateOf(layer.hasTextOutline) }
    var outlineColor by remember(layer.id, layer.textOutlineColorHex) { mutableStateOf(layer.textOutlineColorHex) }
    var outlineWidth by remember(layer.id, layer.textOutlineWidth) { mutableFloatStateOf(layer.textOutlineWidth) }
    var hasShadow by remember(layer.id, layer.hasTextShadow) { mutableStateOf(layer.hasTextShadow) }
    var shadowColor by remember(layer.id, layer.textShadowColorHex) { mutableStateOf(layer.textShadowColorHex) }
    var shadowRadius by remember(layer.id, layer.textShadowRadius) { mutableFloatStateOf(layer.textShadowRadius) }
    var textColor by remember(layer.id, layer.textColorHex) { mutableStateOf(layer.textColorHex) }

    fun sync() {
        viewModel.updateLayerTextStyling(
            layerId = layer.id,
            text = textContent,
            colorHex = textColor,
            fontFamily = font,
            fontSizeSp = fontSize,
            isBold = isBold,
            hasOutline = hasOutline,
            outlineColorHex = outlineColor,
            outlineWidth = outlineWidth,
            hasShadow = hasShadow,
            shadowColorHex = shadowColor,
            shadowRadius = shadowRadius
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Text Input
        OutlinedTextField(
            value = textContent,
            onValueChange = {
                textContent = it
                sync()
            },
            label = { Text("Text Content") },
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = ViperGreen,
                unfocusedBorderColor = Color(0xFF263348),
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary
            )
        )

        // Font Family Selector
        Text("Font Family", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf("DEFAULT" to "Roboto", "MONOSPACE" to "Mono", "SERIF" to "Serif", "CURSIVE" to "Display").forEach { (key, label) ->
                val isSelected = font.equals(key, ignoreCase = true)
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (isSelected) ViperCyan else Color(0xFF131A28))
                        .border(1.dp, if (isSelected) ViperCyan else Color(0xFF263348), RoundedCornerShape(6.dp))
                        .clickable {
                            font = key
                            sync()
                        }
                        .padding(horizontal = 8.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = label,
                        fontSize = 10.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) Color.Black else TextPrimary
                    )
                }
            }
        }

        // Font Size & Bold
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Font Size: ${fontSize.roundToInt()}sp", fontSize = 11.sp, color = TextPrimary)
            IconButton(
                onClick = {
                    isBold = !isBold
                    sync()
                },
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (isBold) ViperGreen else Color(0xFF131A28))
            ) {
                Icon(
                    imageVector = Icons.Default.FormatBold,
                    contentDescription = "Bold",
                    tint = if (isBold) Color.Black else TextPrimary
                )
            }
        }
        Slider(
            value = fontSize,
            onValueChange = {
                fontSize = it
                sync()
            },
            valueRange = 10f..48f,
            colors = SliderDefaults.colors(thumbColor = ViperGreen, activeTrackColor = ViperGreen)
        )

        // Text Color Palette
        Text("Text Color", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("#00E676", "#00E5FF", "#FFFFFF", "#FFD600", "#FF1744", "#B388FF").forEach { hex ->
                val isSelected = textColor.equals(hex, ignoreCase = true)
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(Color(android.graphics.Color.parseColor(hex)))
                        .border(if (isSelected) 2.dp else 1.dp, if (isSelected) Color.White else Color.Transparent, CircleShape)
                        .clickable {
                            textColor = hex
                            sync()
                        }
                )
            }
        }

        Divider(color = Color(0xFF1E293B))

        // Text Outline Switch & Width
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Text Outline", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            Switch(
                checked = hasOutline,
                onCheckedChange = {
                    hasOutline = it
                    sync()
                },
                colors = SwitchDefaults.colors(checkedThumbColor = ViperCyan, checkedTrackColor = Color(0x6600E5FF))
            )
        }
        if (hasOutline) {
            Text("Outline Thickness: ${outlineWidth.roundToInt()}px", fontSize = 10.sp, color = TextMuted)
            Slider(
                value = outlineWidth,
                onValueChange = {
                    outlineWidth = it
                    sync()
                },
                valueRange = 1f..6f,
                colors = SliderDefaults.colors(thumbColor = ViperCyan, activeTrackColor = ViperCyan)
            )
        }

        // Text Shadow Switch
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Text Shadow", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            Switch(
                checked = hasShadow,
                onCheckedChange = {
                    hasShadow = it
                    sync()
                },
                colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFFFFD600), checkedTrackColor = Color(0x66FFD600))
            )
        }

        // Overall Opacity Slider
        Text("Layer Opacity: ${(layer.opacity * 100).roundToInt()}%", fontSize = 11.sp, color = TextPrimary)
        Slider(
            value = layer.opacity,
            onValueChange = { viewModel.updateLayerOpacity(layer.id, it) },
            valueRange = 0.05f..1.0f,
            colors = SliderDefaults.colors(thumbColor = ViperCyan, activeTrackColor = ViperCyan)
        )
    }
}

@Composable
private fun OverlayStylingControls(
    layer: SceneLayer,
    viewModel: ViperStudioViewModel,
    onReplaceImage: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (layer.imageUrl.isNotBlank()) {
            Text("Custom Graphic Image", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = ViperCyan)
            Button(
                onClick = onReplaceImage,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1B273A), contentColor = ViperGreen),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Replace Image from Gallery")
            }
        }

        // Border Stroke Controls
        Text("Overlay Border Stroke: ${layer.borderStrokeWidthDp.roundToInt()}dp", fontSize = 11.sp, color = TextPrimary)
        Slider(
            value = layer.borderStrokeWidthDp,
            onValueChange = {
                viewModel.updateLayerOverlayStyling(layer.id, layer.borderStrokeColorHex, it)
            },
            valueRange = 0f..8f,
            colors = SliderDefaults.colors(thumbColor = ViperCyan, activeTrackColor = ViperCyan)
        )

        // Stroke Color
        Text("Stroke Color", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("#00E5FF", "#00E676", "#FFD600", "#FF1744", "#B388FF", "#FFFFFF").forEach { hex ->
                val isSelected = layer.borderStrokeColorHex.equals(hex, ignoreCase = true)
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(Color(android.graphics.Color.parseColor(hex)))
                        .border(if (isSelected) 2.dp else 1.dp, if (isSelected) Color.White else Color.Transparent, CircleShape)
                        .clickable {
                            viewModel.updateLayerOverlayStyling(layer.id, hex, layer.borderStrokeWidthDp)
                        }
                )
            }
        }

        // Opacity Slider
        Text("Opacity: ${(layer.opacity * 100).roundToInt()}%", fontSize = 11.sp, color = TextPrimary)
        Slider(
            value = layer.opacity,
            onValueChange = { viewModel.updateLayerOpacity(layer.id, it) },
            valueRange = 0.05f..1.0f,
            colors = SliderDefaults.colors(thumbColor = ViperCyan, activeTrackColor = ViperCyan)
        )
    }
}

@Composable
private fun GenericLayerControls(
    layer: SceneLayer,
    viewModel: ViperStudioViewModel
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Opacity: ${(layer.opacity * 100).roundToInt()}%", fontSize = 11.sp, color = TextPrimary)
        Slider(
            value = layer.opacity,
            onValueChange = { viewModel.updateLayerOpacity(layer.id, it) },
            valueRange = 0.05f..1.0f,
            colors = SliderDefaults.colors(thumbColor = ViperCyan, activeTrackColor = ViperCyan)
        )
    }
}

@Composable
private fun LayerOrderControls(
    layer: SceneLayer,
    viewModel: ViperStudioViewModel
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("LAYER DEPTH & Z-INDEX (Current: ${layer.zIndex})", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextSecondary)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { viewModel.bringLayerToFront(layer.id) },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1B273A), contentColor = ViperCyan),
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.FlipToFront, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Bring Front", fontSize = 10.sp)
            }

            Button(
                onClick = { viewModel.sendLayerToBack(layer.id) },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1B273A), contentColor = TextSecondary),
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.FlipToBack, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Send Back", fontSize = 10.sp)
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { viewModel.moveLayerUp(layer.id) },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF131A28), contentColor = TextPrimary),
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.ArrowUpward, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Move Up (+1)", fontSize = 10.sp)
            }

            Button(
                onClick = { viewModel.moveLayerDown(layer.id) },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF131A28), contentColor = TextPrimary),
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.ArrowDownward, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Move Down (-1)", fontSize = 10.sp)
            }
        }
    }
}

/**
 * Dialog to add custom text layer with initial styling.
 */
@Composable
private fun AddTextLayerDialog(
    onDismiss: () -> Unit,
    onConfirm: (text: String, font: String, bold: Boolean, colorHex: String) -> Unit
) {
    var text by remember { mutableStateOf("LIVE STREAM") }
    var font by remember { mutableStateOf("DEFAULT") }
    var bold by remember { mutableStateOf(true) }
    var colorHex by remember { mutableStateOf("#00E676") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Custom Text Overlay", color = TextPrimary, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Text Content") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ViperGreen,
                        unfocusedBorderColor = Color(0xFF263348),
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    )
                )

                // Font Family
                Text("Font Family", fontSize = 10.sp, color = TextSecondary)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("DEFAULT" to "Roboto", "MONOSPACE" to "Mono", "SERIF" to "Serif", "CURSIVE" to "Display").forEach { (key, label) ->
                        val isSelected = font == key
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isSelected) ViperCyan else Color(0xFF131A28))
                                .clickable { font = key }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(label, fontSize = 10.sp, color = if (isSelected) Color.Black else TextPrimary)
                        }
                    }
                }

                // Color Selection
                Text("Text Color", fontSize = 10.sp, color = TextSecondary)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("#00E676", "#00E5FF", "#FFFFFF", "#FFD600", "#FF1744").forEach { hex ->
                        val isSelected = colorHex == hex
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(Color(android.graphics.Color.parseColor(hex)))
                                .border(if (isSelected) 2.dp else 1.dp, if (isSelected) Color.White else Color.Transparent, CircleShape)
                                .clickable { colorHex = hex }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (text.isNotBlank()) onConfirm(text.trim(), font, bold, colorHex)
                },
                colors = ButtonDefaults.buttonColors(containerColor = ViperGreen, contentColor = Color.Black)
            ) {
                Text("Add Text")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = TextSecondary) }
        },
        containerColor = ViperSurfaceCard
    )
}

/**
 * Dialog to add stream frame overlay (Gallery import or built-in presets).
 */
@Composable
private fun AddOverlayFrameDialog(
    onDismiss: () -> Unit,
    onPickFromGallery: () -> Unit,
    onSelectPreset: (presetType: String, label: String, strokeHex: String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Stream Overlay / Frame", color = TextPrimary, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // Gallery Custom Upload Button
                Button(
                    onClick = onPickFromGallery,
                    colors = ButtonDefaults.buttonColors(containerColor = ViperCyan, contentColor = Color.Black),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Import PNG / Frame from Gallery", fontWeight = FontWeight.Bold)
                }

                Divider(color = Color(0xFF1E293B))

                Text("Or Choose Built-in Stream Frame:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextSecondary)

                listOf(
                    Triple("GAMING_BORDER", "Neon Gaming Border Frame", "#00E5FF"),
                    Triple("WEBCAM_FRAME", "Webcam Frame with CAM Tag", "#00E676"),
                    Triple("TOP_BAR", "Top Stream Info Banner Bar", "#00E5FF"),
                    Triple("BOTTOM_TICKER", "Bottom Stream Ticker Plate", "#00E676")
                ).forEach { (type, title, colorHex) ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF131A28))
                            .border(1.dp, Color(android.graphics.Color.parseColor(colorHex)), RoundedCornerShape(8.dp))
                            .clickable { onSelectPreset(type, title, colorHex) }
                            .padding(10.dp)
                    ) {
                        Text(title, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = TextSecondary) }
        },
        containerColor = ViperSurfaceCard
    )
}
