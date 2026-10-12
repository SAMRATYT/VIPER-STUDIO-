package com.example.ui

import android.app.Application
import androidx.camera.core.CameraSelector
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ViperStudioApplication
import com.example.data.local.entity.RecordingEntity
import com.example.data.local.entity.SceneEntity
import com.example.data.model.AlertEvent
import com.example.data.model.AlertType
import com.example.data.model.AudioCaptureMode
import com.example.data.model.AudioMixConfig
import com.example.data.model.FpsOption
import com.example.data.model.LayerType
import com.example.data.model.PlatformType
import com.example.data.model.RecordingItem
import com.example.data.model.ResolutionOption
import com.example.data.model.SceneLayer
import com.example.data.model.ScenePreset
import com.example.data.model.StreamConfig
import com.example.data.model.StreamStats
import com.example.data.model.VideoCodecType
import com.example.engine.audio.AlertSoundSynthesizer
import com.example.engine.hardware.DeviceHardwareProfile
import com.example.engine.hardware.HardwareEncoderDetector
import com.example.service.StreamForegroundService
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val sender: String,
    val text: String,
    val badgeColorHex: String = "#00E676",
    val isSuperChat: Boolean = false,
    val amount: String = ""
)

class ViperStudioViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as ViperStudioApplication
    private val rtmpEngine = app.rtmpStreamEngine
    private val audioEngine = app.audioMixerEngine
    private val mirrorServer = app.localMirrorServer
    private val recorderManager = app.screenRecorderManager
    private val db = app.database

    // Hardware Profile
    val hardwareProfile: DeviceHardwareProfile = HardwareEncoderDetector.detectHardwareProfile()

    // Stream Configuration
    private val _streamConfig = MutableStateFlow(
        StreamConfig(
            bitrateKbps = hardwareProfile.recommendedBitrateKbps,
            resolution = if (hardwareProfile.hasHardwareAvc) ResolutionOption.RES_720P else ResolutionOption.RES_480P
        )
    )
    val streamConfig: StateFlow<StreamConfig> = _streamConfig.asStateFlow()

    // Telemetry & Stats
    val streamStats: StateFlow<StreamStats> = rtmpEngine.stats

    // Audio Mixer State
    val audioConfig: StateFlow<AudioMixConfig> = audioEngine.config
    val micLevel: StateFlow<Float> = audioEngine.micLevel
    val gameLevel: StateFlow<Float> = audioEngine.gameLevel

    // Recording State
    val isRecording: StateFlow<Boolean> = recorderManager.isRecording
    val isRecordingPaused: StateFlow<Boolean> = recorderManager.isPaused
    val recordingDurationSec: StateFlow<Long> = recorderManager.durationSeconds
    val estimatedRecordingSizeBytes: StateFlow<Long> = recorderManager.estimatedFileSizeBytes
    val availableStorageGb: StateFlow<Float> = recorderManager.availableStorageGb

    private val _lastSavedRecording = MutableStateFlow<RecordingItem?>(null)
    val lastSavedRecording: StateFlow<RecordingItem?> = _lastSavedRecording.asStateFlow()

    fun dismissSavedRecordingBanner() {
        _lastSavedRecording.value = null
    }

    val recordingsList: StateFlow<List<RecordingItem>> = db.recordingDao().getAllRecordings()
        .map { entities ->
            entities.map {
                RecordingItem(
                    id = it.id,
                    title = it.title,
                    filePath = it.filePath,
                    durationSeconds = it.durationSeconds,
                    fileSizeBytes = it.fileSizeBytes,
                    resolution = it.resolution,
                    timestamp = it.timestamp
                )
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Scenes & Layers
    private val _scenes = MutableStateFlow<List<ScenePreset>>(emptyList())
    val scenes: StateFlow<List<ScenePreset>> = _scenes.asStateFlow()

    private val _activeScene = MutableStateFlow<ScenePreset?>(null)
    val activeScene: StateFlow<ScenePreset?> = _activeScene.asStateFlow()

    private val _selectedLayerId = MutableStateFlow<String?>(null)
    val selectedLayerId: StateFlow<String?> = _selectedLayerId.asStateFlow()

    // Camera Settings
    private val _isCameraActive = MutableStateFlow(true)
    val isCameraActive: StateFlow<Boolean> = _isCameraActive.asStateFlow()

    private val _cameraLensFacing = MutableStateFlow(CameraSelector.LENS_FACING_FRONT)
    val cameraLensFacing: StateFlow<Int> = _cameraLensFacing.asStateFlow()

    // Live Alert System
    private val _activeAlert = MutableStateFlow<AlertEvent?>(null)
    val activeAlert: StateFlow<AlertEvent?> = _activeAlert.asStateFlow()

    private val _alertHistory = MutableStateFlow<List<AlertEvent>>(emptyList())
    val alertHistory: StateFlow<List<AlertEvent>> = _alertHistory.asStateFlow()

    private var alertDismissJob: Job? = null

    // Live Chat Overlay
    private val _chatMessages = MutableStateFlow<List<ChatMessage>>(
        listOf(
            ChatMessage(sender = "SniperKing99", text = "Let's go VIPER STUDIO! 🔥", badgeColorHex = "#00E5FF"),
            ChatMessage(sender = "ApexPro_Gaming", text = "Clean 1080p 60fps stream! What device?", badgeColorHex = "#00E676"),
            ChatMessage(sender = "CyberQueen", text = "Drop Pochinki next match!!", badgeColorHex = "#B388FF"),
            ChatMessage(sender = "Rohan_BGMI", text = "Donated ₹100 for custom room!", badgeColorHex = "#FFD600", isSuperChat = true, amount = "₹100")
        )
    )
    val chatMessages: StateFlow<List<ChatMessage>> = _chatMessages.asStateFlow()

    private val _isChatOverlayVisible = MutableStateFlow(true)
    val isChatOverlayVisible: StateFlow<Boolean> = _isChatOverlayVisible.asStateFlow()

    // PC Mirror Server
    val isMirrorServerRunning: StateFlow<Boolean> = mirrorServer.isRunning
    val mirrorServerUrl: StateFlow<String?> = mirrorServer.serverUrl
    val mirrorConnectedClients: StateFlow<Int> = mirrorServer.connectedClients

    init {
        audioEngine.startAudioMonitoring()
        loadScenesFromDb()
    }

    private fun loadScenesFromDb() {
        viewModelScope.launch {
            db.sceneDao().getAllScenes().collect { entities ->
                val list = entities.map {
                    ScenePreset(
                        id = it.id,
                        name = it.name,
                        isDefault = it.isDefault,
                        layers = ViperStudioApplication.deserializeLayers(it.layersJson)
                    )
                }
                _scenes.value = list
                if (_activeScene.value == null && list.isNotEmpty()) {
                    _activeScene.value = list.firstOrNull { it.isDefault } ?: list.first()
                }
            }
        }
    }

    fun selectScene(scene: ScenePreset) {
        _activeScene.value = scene
        _selectedLayerId.value = null
    }

    fun selectLayer(layerId: String?) {
        _selectedLayerId.value = layerId
    }

    fun updateLayerPosition(layerId: String, newXPct: Float, newYPct: Float) {
        val current = _activeScene.value ?: return
        val updatedLayers = current.layers.map { layer ->
            if (layer.id == layerId && !layer.isLocked) {
                layer.copy(
                    xPct = newXPct.coerceIn(0f, 95f),
                    yPct = newYPct.coerceIn(0f, 95f)
                )
            } else layer
        }
        val updatedScene = current.copy(layers = updatedLayers)
        _activeScene.value = updatedScene
        saveSceneToDb(updatedScene)
    }

    fun updateLayerSize(layerId: String, newWidthPct: Float, newHeightPct: Float) {
        val current = _activeScene.value ?: return
        val updatedLayers = current.layers.map { layer ->
            if (layer.id == layerId && !layer.isLocked) {
                layer.copy(
                    widthPct = newWidthPct.coerceIn(5f, 100f),
                    heightPct = newHeightPct.coerceIn(5f, 100f)
                )
            } else layer
        }
        val updatedScene = current.copy(layers = updatedLayers)
        _activeScene.value = updatedScene
        saveSceneToDb(updatedScene)
    }

    fun toggleLayerVisibility(layerId: String) {
        val current = _activeScene.value ?: return
        val updatedLayers = current.layers.map {
            if (it.id == layerId) it.copy(isVisible = !it.isVisible) else it
        }
        val updatedScene = current.copy(layers = updatedLayers)
        _activeScene.value = updatedScene
        saveSceneToDb(updatedScene)
    }

    fun toggleLayerLock(layerId: String) {
        val current = _activeScene.value ?: return
        val updatedLayers = current.layers.map {
            if (it.id == layerId) it.copy(isLocked = !it.isLocked) else it
        }
        val updatedScene = current.copy(layers = updatedLayers)
        _activeScene.value = updatedScene
        saveSceneToDb(updatedScene)
    }

    fun addLayer(type: LayerType) {
        val current = _activeScene.value ?: return
        val newLayer = SceneLayer(
            id = "layer_${UUID.randomUUID().toString().take(6)}",
            type = type,
            label = type.displayName,
            xPct = 20f,
            yPct = 30f,
            widthPct = if (type == LayerType.TEXT_BANNER) 60f else 30f,
            heightPct = if (type == LayerType.TEXT_BANNER) 12f else 25f,
            zIndex = current.layers.size + 1,
            textContent = if (type == LayerType.TEXT_BANNER) "NEW CUSTOM OVERLAY" else "@VIPER_STREAM",
            textColorHex = "#00E676"
        )
        val updatedScene = current.copy(layers = current.layers + newLayer)
        _activeScene.value = updatedScene
        _selectedLayerId.value = newLayer.id
        saveSceneToDb(updatedScene)
    }

    fun removeLayer(layerId: String) {
        val current = _activeScene.value ?: return
        val updatedLayers = current.layers.filter { it.id != layerId }
        val updatedScene = current.copy(layers = updatedLayers)
        _activeScene.value = updatedScene
        if (_selectedLayerId.value == layerId) {
            _selectedLayerId.value = null
        }
        saveSceneToDb(updatedScene)
    }

    fun createNewScene(name: String) {
        val newScene = ScenePreset(
            id = "scene_${System.currentTimeMillis()}",
            name = name,
            isDefault = false,
            layers = listOf(
                SceneLayer("game_${UUID.randomUUID().toString().take(4)}", LayerType.GAME_SCREEN, "Game Screen", 0f, 0f, 100f, 100f, zIndex = 0),
                SceneLayer("alert_${UUID.randomUUID().toString().take(4)}", LayerType.ALERT_BOX, "Alert Box", 20f, 10f, 60f, 20f, zIndex = 10)
            )
        )
        _activeScene.value = newScene
        saveSceneToDb(newScene)
    }

    private fun saveSceneToDb(scene: ScenePreset) {
        viewModelScope.launch {
            db.sceneDao().insertScene(
                SceneEntity(
                    id = scene.id,
                    name = scene.name,
                    isDefault = scene.isDefault,
                    layersJson = ViperStudioApplication.serializeLayers(scene.layers)
                )
            )
        }
    }

    // Camera Controls
    fun toggleCamera() {
        _isCameraActive.value = !_isCameraActive.value
    }

    fun switchCameraLens() {
        _cameraLensFacing.value = if (_cameraLensFacing.value == CameraSelector.LENS_FACING_FRONT) {
            CameraSelector.LENS_FACING_BACK
        } else {
            CameraSelector.LENS_FACING_FRONT
        }
    }

    // Stream Config setters
    fun setPlatform(platform: PlatformType) {
        _streamConfig.value = _streamConfig.value.copy(
            platform = platform,
            rtmpUrl = platform.defaultRtmpUrl
        )
    }

    fun setStreamKey(key: String) {
        _streamConfig.value = _streamConfig.value.copy(streamKey = key)
    }

    fun setRtmpUrl(url: String) {
        _streamConfig.value = _streamConfig.value.copy(rtmpUrl = url)
    }

    fun setStreamTitle(title: String) {
        _streamConfig.value = _streamConfig.value.copy(streamTitle = title)
    }

    fun setStreamDescription(desc: String) {
        _streamConfig.value = _streamConfig.value.copy(streamDescription = desc)
    }

    fun setGameCategory(cat: String) {
        _streamConfig.value = _streamConfig.value.copy(gameCategory = cat)
    }

    fun setResolution(res: ResolutionOption) {
        _streamConfig.value = _streamConfig.value.copy(
            resolution = res,
            bitrateKbps = res.recommendedBitrate
        )
    }

    fun setFps(fps: FpsOption) {
        _streamConfig.value = _streamConfig.value.copy(fps = fps)
    }

    fun setBitrate(kbps: Int) {
        _streamConfig.value = _streamConfig.value.copy(bitrateKbps = kbps)
    }

    fun setCodec(codec: VideoCodecType) {
        _streamConfig.value = _streamConfig.value.copy(codec = codec)
    }

    fun toggleAdaptiveBitrate() {
        val current = _streamConfig.value.isAdaptiveBitrateEnabled
        _streamConfig.value = _streamConfig.value.copy(isAdaptiveBitrateEnabled = !current)
    }

    fun toggleLowLatency() {
        val current = _streamConfig.value.isLowLatencyEnabled
        _streamConfig.value = _streamConfig.value.copy(isLowLatencyEnabled = !current)
    }

    fun toggleOrientation() {
        val current = _streamConfig.value.isLandscape
        _streamConfig.value = _streamConfig.value.copy(isLandscape = !current)
    }

    // Audio Mixer setters
    fun setGameVolume(volume: Float) = audioEngine.setGameVolume(volume)
    fun setMicVolume(volume: Float) = audioEngine.setMicVolume(volume)
    fun toggleMicMute() = audioEngine.toggleMicMute()
    fun toggleNoiseSuppression() = audioEngine.toggleNoiseSuppression()
    fun toggleEchoCancellation() = audioEngine.toggleEchoCancellation()
    fun setAudioDelay(delayMs: Int) = audioEngine.setAudioDelay(delayMs)
    fun setAudioCaptureMode(mode: AudioCaptureMode) = audioEngine.setAudioCaptureMode(mode)

    // Alert Actions
    fun triggerTestAlert(type: AlertType) {
        val names = listOf("Ghost_Rider", "ToxicViper_99", "Phoenix_Playz", "Krypton_Warrior", "Ninja_Strike")
        val randomName = names.random()
        val event = when (type) {
            AlertType.SUBSCRIBER -> {
                AlertSoundSynthesizer.playFanfare()
                AlertEvent(
                    id = UUID.randomUUID().toString(),
                    type = type,
                    username = randomName,
                    message = "Subscribed with Notifications on!",
                    colorHex = "#00E676"
                )
            }
            AlertType.SUPER_CHAT -> {
                AlertSoundSynthesizer.playSuperChatChime()
                AlertEvent(
                    id = UUID.randomUUID().toString(),
                    type = type,
                    username = randomName,
                    amount = "$20.00",
                    message = "Insane clutches in BGMI today bro! Keep crushing!",
                    colorHex = "#FFD600"
                )
            }
            AlertType.DONATION -> {
                AlertSoundSynthesizer.playDonationPing()
                AlertEvent(
                    id = UUID.randomUUID().toString(),
                    type = type,
                    username = randomName,
                    amount = "₹500",
                    message = "GGs from Delhi fan! Win the tournament!",
                    colorHex = "#00E5FF"
                )
            }
            AlertType.FOLLOWER -> {
                AlertSoundSynthesizer.playFollowerBell()
                AlertEvent(
                    id = UUID.randomUUID().toString(),
                    type = type,
                    username = randomName,
                    message = "Just followed the channel!",
                    colorHex = "#B388FF"
                )
            }
        }

        _activeAlert.value = event
        _alertHistory.value = listOf(event) + _alertHistory.value.take(20)

        alertDismissJob?.cancel()
        alertDismissJob = viewModelScope.launch {
            delay(event.durationSec * 1000L)
            if (_activeAlert.value?.id == event.id) {
                _activeAlert.value = null
            }
        }
    }

    // Stream Error State
    val streamErrorMessage: StateFlow<String?> = rtmpEngine.stats
        .map { it.errorMessage }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun clearErrorMessage() {
        rtmpEngine.clearErrorMessage()
    }

    // Stream Control with MediaProjection Token Support
    fun startBroadcast(resultCode: Int? = null, resultData: android.content.Intent? = null): Boolean {
        // Prevent duplicate broadcast sessions if already streaming or connecting
        if (rtmpEngine.stats.value.isStreaming || rtmpEngine.stats.value.isConnecting) {
            return false
        }

        // Validation check
        val cfg = _streamConfig.value
        if (cfg.platform != PlatformType.CUSTOM_RTMP && cfg.streamKey.trim().isBlank()) {
            rtmpEngine.startStream(cfg) // Will set error message
            return false
        }

        if (resultCode != null && resultData != null) {
            StreamForegroundService.startServiceWithProjection(app, resultCode, resultData, cfg.streamTitle)
        } else {
            StreamForegroundService.startServiceCameraOnly(app, cfg.streamTitle)
        }

        return rtmpEngine.startStream(cfg)
    }

    fun stopBroadcast() {
        rtmpEngine.stopStream()
        if (!recorderManager.isRecording.value) {
            StreamForegroundService.stopService(app)
        }
    }

    // Screen Recording with MediaProjection Token Support
    fun startScreenRecording(resultCode: Int? = null, resultData: android.content.Intent? = null) {
        if (recorderManager.isRecording.value) return

        if (resultCode != null && resultData != null) {
            StreamForegroundService.startServiceWithProjection(app, resultCode, resultData, "Screen Recording (MP4)")
        } else if (!StreamForegroundService.isServiceRunning) {
            StreamForegroundService.startServiceCameraOnly(app, "Screen Recording (MP4)")
        }
        recorderManager.startRecording(_streamConfig.value.resolution)
    }

    fun pauseScreenRecording() {
        recorderManager.pauseRecording()
    }

    fun resumeScreenRecording() {
        recorderManager.resumeRecording()
    }

    fun stopScreenRecording() {
        recorderManager.stopRecording { entity ->
            _lastSavedRecording.value = RecordingItem(
                id = entity.id,
                title = entity.title,
                filePath = entity.filePath,
                durationSeconds = entity.durationSeconds,
                fileSizeBytes = entity.fileSizeBytes,
                resolution = entity.resolution,
                timestamp = entity.timestamp
            )
        }
        if (!rtmpEngine.stats.value.isStreaming) {
            StreamForegroundService.stopService(app)
        }
    }

    fun deleteRecording(item: RecordingItem) {
        recorderManager.deleteRecording(item.id, item.filePath)
    }

    // PC Mirror Server
    fun toggleMirrorServer() {
        if (mirrorServer.isRunning.value) {
            mirrorServer.stopServer()
        } else {
            mirrorServer.startServer()
        }
    }

    // Live Chat Overlay
    fun toggleChatOverlay() {
        _isChatOverlayVisible.value = !_isChatOverlayVisible.value
    }

    fun sendChatMessage(text: String) {
        if (text.isBlank()) return
        val msg = ChatMessage(
            sender = "Streamer (You)",
            text = text,
            badgeColorHex = "#FF1744"
        )
        _chatMessages.value = _chatMessages.value + msg
    }

    override fun onCleared() {
        super.onCleared()
        audioEngine.stopAudioMonitoring()
        rtmpEngine.stopStream()
        mirrorServer.stopServer()
    }
}
