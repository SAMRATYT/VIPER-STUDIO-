package com.example

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CastConnected
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.ui.ViperStudioViewModel
import com.example.ui.screens.AlertBoxScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.LiveScreen
import com.example.ui.screens.PcMirrorScreen
import com.example.ui.screens.RecorderScreen
import com.example.ui.screens.SceneEditorScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.StudioScreen
import com.example.ui.theme.LiveRed
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.RecordingRed
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.ViperCyan
import com.example.ui.theme.ViperGreen
import kotlinx.coroutines.launch

enum class StudioDestination(val title: String, val icon: ImageVector) {
    HOME("Home", Icons.Default.Home),
    LIVE("Live", Icons.Default.LiveTv),
    STUDIO("Live Studio", Icons.Default.Layers),
    RECORDER("Recorder", Icons.Default.RadioButtonChecked),
    SCENE_EDITOR("Scene Editor", Icons.Default.Videocam),
    ALERT_BOX("Alert Box", Icons.Default.NotificationsActive),
    PC_MIRROR("PC Mirror", Icons.Default.CastConnected),
    SETTINGS("Settings", Icons.Default.Settings)
}

enum class PendingCaptureAction {
    START_STREAM,
    START_RECORDING
}

class MainActivity : ComponentActivity() {

    private val viewModel: ViperStudioViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MyApplicationTheme {
                ViperStudioApp(viewModel = viewModel)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViperStudioApp(viewModel: ViperStudioViewModel) {
    var currentDestination by remember { mutableStateOf(StudioDestination.HOME) }
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val coroutineScope = rememberCoroutineScope()
    val streamStats by viewModel.streamStats.collectAsState()
    val isRecording by viewModel.isRecording.collectAsState()
    val context = LocalContext.current

    // Request Runtime Permissions on start
    val permissionsToRequest = remember {
        mutableListOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CAMERA
        ).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }.toTypedArray()
    }

    var pendingCaptureAction by remember { mutableStateOf<PendingCaptureAction?>(null) }

    val mediaProjectionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val action = pendingCaptureAction
        pendingCaptureAction = null
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            when (action) {
                PendingCaptureAction.START_STREAM -> {
                    viewModel.startBroadcast(result.resultCode, result.data)
                }
                PendingCaptureAction.START_RECORDING -> {
                    viewModel.startScreenRecording(result.resultCode, result.data)
                }
                null -> {}
            }
        } else {
            Toast.makeText(
                context,
                "Screen capture permission was not granted. Broadcast not started.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    fun launchScreenCapture(action: PendingCaptureAction) {
        val projectionManager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as? MediaProjectionManager
        if (projectionManager != null) {
            pendingCaptureAction = action
            try {
                val captureIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    val config = android.media.projection.MediaProjectionConfig.createConfigForDefaultDisplay()
                    projectionManager.createScreenCaptureIntent(config)
                } else {
                    projectionManager.createScreenCaptureIntent()
                }
                mediaProjectionLauncher.launch(captureIntent)
            } catch (e: Exception) {
                android.util.Log.e("MainActivity", "Failed to launch screen capture intent", e)
                pendingCaptureAction = null
                if (action == PendingCaptureAction.START_STREAM) {
                    viewModel.startBroadcast(null, null)
                } else {
                    viewModel.startScreenRecording(null, null)
                }
            }
        } else {
            if (action == PendingCaptureAction.START_STREAM) {
                viewModel.startBroadcast(null, null)
            } else {
                viewModel.startScreenRecording(null, null)
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        val action = pendingCaptureAction
        if (action != null) {
            launchScreenCapture(action)
        }
    }

    LaunchedEffect(Unit) {
        permissionLauncher.launch(permissionsToRequest)
    }

    fun requestStartLive() {
        if (streamStats.isStreaming || streamStats.isConnecting || pendingCaptureAction != null) {
            return
        }

        val streamConfig = viewModel.streamConfig.value
        if (streamConfig.platform != com.example.data.model.PlatformType.CUSTOM_RTMP && streamConfig.streamKey.trim().isBlank()) {
            Toast.makeText(context, "Please enter your Stream Key in the Live tab first.", Toast.LENGTH_LONG).show()
            currentDestination = StudioDestination.LIVE
            return
        }

        val hasMic = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        val hasCamera = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasMic || !hasCamera) {
            pendingCaptureAction = PendingCaptureAction.START_STREAM
            permissionLauncher.launch(permissionsToRequest)
            return
        }

        launchScreenCapture(PendingCaptureAction.START_STREAM)
    }

    fun requestStartRecord() {
        if (isRecording || pendingCaptureAction != null) {
            return
        }

        val hasMic = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasMic) {
            pendingCaptureAction = PendingCaptureAction.START_RECORDING
            permissionLauncher.launch(permissionsToRequest)
            return
        }

        launchScreenCapture(PendingCaptureAction.START_RECORDING)
    }

    // Predictive Back Handler
    BackHandler(enabled = currentDestination != StudioDestination.HOME) {
        currentDestination = StudioDestination.HOME
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = Color(0xFF0F1522),
                modifier = Modifier
                    .width(300.dp)
                    .fillMaxHeight()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Drawer Header
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 24.dp, bottom = 12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(modifier = Modifier.size(12.dp).clip(CircleShape).background(ViperGreen))
                            Text(
                                text = "VIPER STUDIO",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Black,
                                color = TextPrimary,
                                letterSpacing = 1.sp
                            )
                        }
                        Text(
                            text = "Professional Android Live Broadcast & Recording",
                            fontSize = 11.sp,
                            color = TextMuted,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }

                    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color(0xFF1E283A)))

                    // Drawer Navigation Items
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        StudioDestination.values().forEach { destination ->
                            val isSelected = currentDestination == destination
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) Color(0x2200E676) else Color.Transparent)
                                    .clickable {
                                        currentDestination = destination
                                        coroutineScope.launch { drawerState.close() }
                                    }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    imageVector = destination.icon,
                                    contentDescription = destination.title,
                                    tint = if (isSelected) ViperGreen else TextSecondary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = destination.title,
                                    fontSize = 13.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) ViperGreen else TextPrimary
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    // Version & Device Info in Drawer Footer
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF141C2B))
                            .padding(10.dp)
                    ) {
                        Text("Moto G96 5G & Qualcomm Optimized", fontSize = 10.sp, color = ViperGreen, fontWeight = FontWeight.Bold)
                        Text("v1.0.0 Pro Live Release", fontSize = 10.sp, color = TextMuted)
                    }
                }
            }
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = currentDestination.title,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Black,
                                color = TextPrimary
                            )
                            if (streamStats.isStreaming) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(LiveRed)
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text("LIVE", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Black)
                                }
                            }
                            if (isRecording) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(RecordingRed)
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text("REC", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Black)
                                }
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = { coroutineScope.launch { drawerState.open() } },
                            modifier = Modifier.testTag("open_navigation_drawer_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Menu,
                                contentDescription = "Open Studio Menu",
                                tint = ViperGreen
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color(0xFF0A0E17),
                        titleContentColor = TextPrimary
                    )
                )
            },
            bottomBar = {
                NavigationBar(
                    containerColor = Color(0xFF0D121D),
                    tonalElevation = 8.dp,
                    modifier = Modifier.navigationBarsPadding()
                ) {
                    val bottomDestinations = listOf(
                        StudioDestination.HOME,
                        StudioDestination.LIVE,
                        StudioDestination.STUDIO,
                        StudioDestination.RECORDER,
                        StudioDestination.SETTINGS
                    )

                    bottomDestinations.forEach { dest ->
                        val isSelected = currentDestination == dest
                        NavigationBarItem(
                            selected = isSelected,
                            onClick = { currentDestination = dest },
                            icon = {
                                Icon(
                                    imageVector = dest.icon,
                                    contentDescription = dest.title,
                                    modifier = Modifier.size(20.dp)
                                )
                            },
                            label = {
                                Text(
                                    text = dest.title.substringBefore(" "),
                                    fontSize = 10.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Color.Black,
                                selectedTextColor = ViperGreen,
                                indicatorColor = ViperGreen,
                                unselectedIconColor = TextMuted,
                                unselectedTextColor = TextMuted
                            )
                        )
                    }
                }
            },
            containerColor = Color(0xFF0A0E17)
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                when (currentDestination) {
                    StudioDestination.HOME -> HomeScreen(
                        viewModel = viewModel,
                        onNavigateToLive = { currentDestination = StudioDestination.LIVE },
                        onNavigateToStudio = { currentDestination = StudioDestination.STUDIO },
                        onNavigateToRecorder = { currentDestination = StudioDestination.RECORDER },
                        onNavigateToSceneEditor = { currentDestination = StudioDestination.SCENE_EDITOR },
                        onNavigateToAlerts = { currentDestination = StudioDestination.ALERT_BOX },
                        onNavigateToMirror = { currentDestination = StudioDestination.PC_MIRROR },
                        onStartLiveRequest = { requestStartLive() },
                        onStartRecordRequest = { requestStartRecord() }
                    )

                    StudioDestination.LIVE -> LiveScreen(
                        viewModel = viewModel,
                        onStartLiveRequest = { requestStartLive() }
                    )

                    StudioDestination.STUDIO -> StudioScreen(
                        viewModel = viewModel
                    )

                    StudioDestination.RECORDER -> RecorderScreen(
                        viewModel = viewModel,
                        onStartRecordRequest = { requestStartRecord() }
                    )

                    StudioDestination.SCENE_EDITOR -> SceneEditorScreen(
                        viewModel = viewModel
                    )

                    StudioDestination.ALERT_BOX -> AlertBoxScreen(
                        viewModel = viewModel
                    )

                    StudioDestination.PC_MIRROR -> PcMirrorScreen(
                        viewModel = viewModel
                    )

                    StudioDestination.SETTINGS -> SettingsScreen(
                        viewModel = viewModel
                    )
                }
            }
        }
    }
}
