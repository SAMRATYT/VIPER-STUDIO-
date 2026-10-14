package com.example.ui

import android.app.Application
import android.content.Context
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
import com.example.data.model.RateControlMode
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
import com.example.engine.recording.ScreenRecorderManager
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

import com.example.data.model.ChatMessage
import com.example.data.model.LiveChatSettings
import com.example.data.model.YouTubeChannelInfo

class ViperStudioViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as ViperStudioApplication
    private val rtmpEngine = app.rtmpStreamEngine
    private val audioEngine = app.audioMixerEngine
    private val ytEngine = app.youTubeSyncEngine
    private val mirrorServer = app.localMirrorServer
    private val recorderManager = app.screenRecorderManager
    private val db = app.database

    private val prefsRepo = app.preferencesRepository

    // Hardware Profile
    val hardwareProfile: DeviceHardwareProfile = HardwareEncoderDetector.detectHardwareProfile()

    // Stream Configuration (Persisted across restarts)
    private val _streamConfig = MutableStateFlow(prefsRepo.loadStreamConfig())
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
    val recordingState: StateFlow<ScreenRecorderManager.RecordingState> = recorderManager.recordingState
    val recordingErrorMessage: StateFlow<String?> = recorderManager.recordingErrorMessage

    private val _lastSavedRecording = MutableStateFlow<RecordingItem?>(null)
    val lastSavedRecording: StateFlow<RecordingItem?> = _lastSavedRecording.asStateFlow()

    fun dismissSavedRecordingBanner() {
        _lastSavedRecording.value = null
        recorderManager.clearRecordingState()
    }

    fun dismissRecordingError() {
        recorderManager.clearRecordingState()
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

    // YouTube OAuth & Channel Synchronization
    val youTubeChannelInfo: StateFlow<YouTubeChannelInfo> = ytEngine.channelInfo
    val isYouTubeSyncing: StateFlow<Boolean> = ytEngine.isSyncing
    val authError: StateFlow<String?> = ytEngine.authError

    // Live Chat Feed (Synchronized with YouTube Live Chat & Offline viewer simulation)
    val chatMessages: StateFlow<List<ChatMessage>> = ytEngine.chatMessages

    private val _isChatOverlayVisible = MutableStateFlow(true)
    val isChatOverlayVisible: StateFlow<Boolean> = _isChatOverlayVisible.asStateFlow()

    // Customizable Live Chat Overlay Settings (Draggable, Opacity, Font Size, Lock)
    private val _liveChatSettings = MutableStateFlow(prefsRepo.loadLiveChatSettings())
    val liveChatSettings: StateFlow<LiveChatSettings> = _liveChatSettings.asStateFlow()

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

    fun updateLayerRotation(layerId: String, rotationDeg: Float) {
        val current = _activeScene.value ?: return
        val updatedLayers = current.layers.map {
            if (it.id == layerId) it.copy(rotationDeg = rotationDeg.coerceIn(-180f, 180f)) else it
        }
        val updatedScene = current.copy(layers = updatedLayers)
        _activeScene.value = updatedScene
        saveSceneToDb(updatedScene)
    }

    fun updateLayerOpacity(layerId: String, opacity: Float) {
        val current = _activeScene.value ?: return
        val updatedLayers = current.layers.map {
            if (it.id == layerId) it.copy(opacity = opacity.coerceIn(0.05f, 1.0f)) else it
        }
        val updatedScene = current.copy(layers = updatedLayers)
        _activeScene.value = updatedScene
        saveSceneToDb(updatedScene)
    }

    fun updateLayerText(layerId: String, text: String, colorHex: String) {
        val current = _activeScene.value ?: return
        val updatedLayers = current.layers.map {
            if (it.id == layerId) it.copy(textContent = text, textColorHex = colorHex) else it
        }
        val updatedScene = current.copy(layers = updatedLayers)
        _activeScene.value = updatedScene
        saveSceneToDb(updatedScene)
    }

    fun updateLayerImage(layerId: String, imageUrl: String) {
        val current = _activeScene.value ?: return
        val updatedLayers = current.layers.map {
            if (it.id == layerId) it.copy(imageUrl = imageUrl) else it
        }
        val updatedScene = current.copy(layers = updatedLayers)
        _activeScene.value = updatedScene
        saveSceneToDb(updatedScene)
    }

    fun duplicateLayer(layerId: String) {
        val current = _activeScene.value ?: return
        val source = current.layers.firstOrNull { it.id == layerId } ?: return
        val newLayer = source.copy(
            id = "layer_${UUID.randomUUID().toString().take(6)}",
            label = "${source.label} (Copy)",
            xPct = (source.xPct + 4f).coerceAtMost(90f),
            yPct = (source.yPct + 4f).coerceAtMost(90f),
            zIndex = (current.layers.maxOfOrNull { it.zIndex } ?: 0) + 1
        )
        val updatedScene = current.copy(layers = current.layers + newLayer)
        _activeScene.value = updatedScene
        _selectedLayerId.value = newLayer.id
        saveSceneToDb(updatedScene)
    }

    fun bringLayerToFront(layerId: String) {
        val current = _activeScene.value ?: return
        val maxZ = (current.layers.maxOfOrNull { it.zIndex } ?: 0) + 1
        val updatedLayers = current.layers.map {
            if (it.id == layerId) it.copy(zIndex = maxZ) else it
        }
        val updatedScene = current.copy(layers = updatedLayers)
        _activeScene.value = updatedScene
        saveSceneToDb(updatedScene)
    }

    fun sendLayerToBack(layerId: String) {
        val current = _activeScene.value ?: return
        val minZ = (current.layers.minOfOrNull { it.zIndex } ?: 0) - 1
        val updatedLayers = current.layers.map {
            if (it.id == layerId) it.copy(zIndex = minZ) else it
        }
        val updatedScene = current.copy(layers = updatedLayers)
        _activeScene.value = updatedScene
        saveSceneToDb(updatedScene)
    }

    fun moveLayerUp(layerId: String) {
        val current = _activeScene.value ?: return
        val updatedLayers = current.layers.map {
            if (it.id == layerId) it.copy(zIndex = it.zIndex + 1) else it
        }
        val updatedScene = current.copy(layers = updatedLayers)
        _activeScene.value = updatedScene
        saveSceneToDb(updatedScene)
    }

    fun moveLayerDown(layerId: String) {
        val current = _activeScene.value ?: return
        val updatedLayers = current.layers.map {
            if (it.id == layerId) it.copy(zIndex = (it.zIndex - 1).coerceAtLeast(0)) else it
        }
        val updatedScene = current.copy(layers = updatedLayers)
        _activeScene.value = updatedScene
        saveSceneToDb(updatedScene)
    }

    fun alignLayer(layerId: String, alignment: String) {
        val current = _activeScene.value ?: return
        val layer = current.layers.firstOrNull { it.id == layerId } ?: return
        val (newX, newY) = when (alignment) {
            "CENTER" -> Pair(((100f - layer.widthPct) / 2f).coerceAtLeast(0f), ((100f - layer.heightPct) / 2f).coerceAtLeast(0f))
            "TOP" -> Pair(layer.xPct, 2f)
            "BOTTOM" -> Pair(layer.xPct, (100f - layer.heightPct - 2f).coerceAtLeast(0f))
            "LEFT" -> Pair(2f, layer.yPct)
            "RIGHT" -> Pair((100f - layer.widthPct - 2f).coerceAtLeast(0f), layer.yPct)
            else -> Pair(layer.xPct, layer.yPct)
        }
        updateLayerPosition(layerId, newX, newY)
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
        val updated = _streamConfig.value.copy(
            platform = platform,
            rtmpUrl = platform.defaultRtmpUrl
        )
        _streamConfig.value = updated
        prefsRepo.saveStreamConfig(updated)
    }

    fun setStreamKey(key: String) {
        val updated = _streamConfig.value.copy(streamKey = key)
        _streamConfig.value = updated
        prefsRepo.saveStreamConfig(updated)
    }

    fun setRtmpUrl(url: String) {
        val updated = _streamConfig.value.copy(rtmpUrl = url)
        _streamConfig.value = updated
        prefsRepo.saveStreamConfig(updated)
    }

    fun setStreamTitle(title: String) {
        val updated = _streamConfig.value.copy(streamTitle = title)
        _streamConfig.value = updated
        prefsRepo.saveStreamConfig(updated)
    }

    fun setStreamDescription(desc: String) {
        val updated = _streamConfig.value.copy(streamDescription = desc)
        _streamConfig.value = updated
        prefsRepo.saveStreamConfig(updated)
    }

    fun setGameCategory(cat: String) {
        val updated = _streamConfig.value.copy(gameCategory = cat)
        _streamConfig.value = updated
        prefsRepo.saveStreamConfig(updated)
    }

    fun setResolution(res: ResolutionOption) {
        val updated = _streamConfig.value.copy(
            resolution = res,
            bitrateKbps = res.recommendedBitrate
        )
        _streamConfig.value = updated
        prefsRepo.saveStreamConfig(updated)
    }

    fun setFps(fps: FpsOption) {
        val updated = _streamConfig.value.copy(fps = fps)
        _streamConfig.value = updated
        prefsRepo.saveStreamConfig(updated)
    }

    fun setBitrate(kbps: Int) {
        val updated = _streamConfig.value.copy(bitrateKbps = kbps)
        _streamConfig.value = updated
        prefsRepo.saveStreamConfig(updated)
    }

    fun setCodec(codec: VideoCodecType) {
        val updated = _streamConfig.value.copy(codec = codec)
        _streamConfig.value = updated
        prefsRepo.saveStreamConfig(updated)
    }

    fun setRateControl(mode: RateControlMode) {
        val updated = _streamConfig.value.copy(rateControl = mode)
        _streamConfig.value = updated
        prefsRepo.saveStreamConfig(updated)
    }

    fun toggleAdaptiveBitrate() {
        val current = _streamConfig.value.isAdaptiveBitrateEnabled
        val updated = _streamConfig.value.copy(isAdaptiveBitrateEnabled = !current)
        _streamConfig.value = updated
        prefsRepo.saveStreamConfig(updated)
    }

    fun toggleLowLatency() {
        val current = _streamConfig.value.isLowLatencyEnabled
        val updated = _streamConfig.value.copy(isLowLatencyEnabled = !current)
        _streamConfig.value = updated
        prefsRepo.saveStreamConfig(updated)
    }

    fun setOrientation(isLandscape: Boolean) {
        val updated = _streamConfig.value.copy(isLandscape = isLandscape)
        _streamConfig.value = updated
        prefsRepo.saveStreamConfig(updated)
    }

    fun toggleOrientation() {
        val current = _streamConfig.value.isLandscape
        setOrientation(!current)
    }

    // Audio Mixer setters
    fun setGameVolume(volume: Float) = audioEngine.setGameVolume(volume)
    fun setMicVolume(volume: Float) = audioEngine.setMicVolume(volume)
    fun setMicGain(gain: Float) = audioEngine.setMicGain(gain)
    fun toggleHighPassFilter100Hz() = audioEngine.toggleHighPassFilter100Hz()
    fun toggleInternalAudio() = audioEngine.toggleInternalAudio()
    fun toggleMicAudio() = audioEngine.toggleMicAudio()
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
        val cfg = _streamConfig.value
        recorderManager.startRecording(
            resolution = cfg.resolution,
            isLandscape = cfg.isLandscape,
            targetFps = cfg.fps.value,
            bitrateKbps = cfg.bitrateKbps
        )
    }

    fun pauseScreenRecording() {
        recorderManager.pauseRecording()
    }

    fun resumeScreenRecording() {
        recorderManager.resumeRecording()
    }

    fun stopScreenRecording() {
        recorderManager.stopRecording(
            onSaved = { entity ->
                _lastSavedRecording.value = RecordingItem(
                    id = entity.id,
                    title = entity.title,
                    filePath = entity.filePath,
                    durationSeconds = entity.durationSeconds,
                    fileSizeBytes = entity.fileSizeBytes,
                    resolution = entity.resolution,
                    timestamp = entity.timestamp
                )
                if (!rtmpEngine.stats.value.isStreaming) {
                    StreamForegroundService.stopService(app)
                }
            },
            onError = { err ->
                android.util.Log.e("ViperStudioViewModel", "Screen recording finalization error: $err")
                if (!rtmpEngine.stats.value.isStreaming) {
                    StreamForegroundService.stopService(app)
                }
            }
        )
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
        ytEngine.sendChatMessage(text)
    }

    // YouTube Channel Connection & Google OAuth
    fun signInWithGoogle(
        context: Context,
        onSuccess: (YouTubeChannelInfo) -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        ytEngine.signInWithGoogle(context, forceSwitchAccount = false, onSuccess = { channel ->
            if (channel.streamKey.isNotBlank()) {
                _streamConfig.value = _streamConfig.value.copy(
                    streamKey = channel.streamKey,
                    rtmpUrl = channel.rtmpIngestUrl.ifBlank { "rtmp://a.rtmp.youtube.com/live2" }
                )
            }
            onSuccess(channel)
        }, onError = onError)
    }

    fun switchGoogleAccount(
        context: Context,
        onSuccess: (YouTubeChannelInfo) -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        ytEngine.switchGoogleAccount(context, onSuccess = { channel ->
            if (channel.streamKey.isNotBlank()) {
                _streamConfig.value = _streamConfig.value.copy(
                    streamKey = channel.streamKey,
                    rtmpUrl = channel.rtmpIngestUrl.ifBlank { "rtmp://a.rtmp.youtube.com/live2" }
                )
            }
            onSuccess(channel)
        }, onError = onError)
    }

    fun disconnectAccount(context: Context) {
        ytEngine.disconnectAccount(context)
    }

    fun connectYouTubePermissions(
        onSuccess: (YouTubeChannelInfo) -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        ytEngine.connectYouTubePermissions(onSuccess = { channel ->
            if (channel.streamKey.isNotBlank()) {
                _streamConfig.value = _streamConfig.value.copy(
                    streamKey = channel.streamKey,
                    rtmpUrl = channel.rtmpIngestUrl.ifBlank { "rtmp://a.rtmp.youtube.com/live2" }
                )
            }
            onSuccess(channel)
        }, onError = onError)
    }

    fun clearAuthError() {
        ytEngine.clearAuthError()
    }

    fun syncYouTubeBroadcast(onComplete: (Boolean) -> Unit = {}) {
        ytEngine.syncBroadcastDetails { success ->
            val channel = ytEngine.channelInfo.value
            if (success && channel.streamKey.isNotBlank()) {
                _streamConfig.value = _streamConfig.value.copy(
                    streamKey = channel.streamKey,
                    rtmpUrl = channel.rtmpIngestUrl.ifBlank { "rtmp://a.rtmp.youtube.com/live2" }
                )
            }
            onComplete(success)
        }
    }

    fun applyYouTubeStreamKeyToConfig() {
        val channel = ytEngine.channelInfo.value
        if (channel.streamKey.isNotBlank()) {
            _streamConfig.value = _streamConfig.value.copy(
                streamKey = channel.streamKey,
                rtmpUrl = channel.rtmpIngestUrl.ifBlank { "rtmp://a.rtmp.youtube.com/live2" }
            )
        }
    }

    // Live Chat Overlay Settings (Draggable, Opacity, Font Size, Lock)
    fun updateLiveChatPosition(xPct: Float, yPct: Float) {
        val current = _liveChatSettings.value
        val updated = current.copy(
            xPct = xPct.coerceIn(0f, 100f - current.widthPct),
            yPct = yPct.coerceIn(0f, 100f - current.heightPct)
        )
        _liveChatSettings.value = updated
        prefsRepo.saveLiveChatSettings(updated)
    }

    fun updateLiveChatSize(widthPct: Float, heightPct: Float) {
        val current = _liveChatSettings.value
        val updated = current.copy(
            widthPct = widthPct.coerceIn(20f, 95f),
            heightPct = heightPct.coerceIn(15f, 80f)
        )
        _liveChatSettings.value = updated
        prefsRepo.saveLiveChatSettings(updated)
    }

    fun updateLiveChatOpacity(opacity: Float) {
        val current = _liveChatSettings.value
        val updated = current.copy(backgroundOpacity = opacity.coerceIn(0.1f, 1.0f))
        _liveChatSettings.value = updated
        prefsRepo.saveLiveChatSettings(updated)
    }

    fun updateLiveChatFontSize(sizeSp: Int) {
        val current = _liveChatSettings.value
        val updated = current.copy(fontSizeSp = sizeSp.coerceIn(8, 20))
        _liveChatSettings.value = updated
        prefsRepo.saveLiveChatSettings(updated)
    }

    fun toggleLiveChatLock() {
        val current = _liveChatSettings.value
        val updated = current.copy(isLocked = !current.isLocked)
        _liveChatSettings.value = updated
        prefsRepo.saveLiveChatSettings(updated)
    }

    override fun onCleared() {
        super.onCleared()
        audioEngine.stopAudioMonitoring()
        rtmpEngine.stopStream()
        mirrorServer.stopServer()
    }
}
