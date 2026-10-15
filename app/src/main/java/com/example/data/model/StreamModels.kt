package com.example.data.model

enum class PlatformType(val displayName: String, val defaultRtmpUrl: String, val instructions: String) {
    YOUTUBE("YouTube Live", "rtmp://a.rtmp.youtube.com/live2", "Get Stream Key from YouTube Studio -> Go Live"),
    TWITCH("Twitch", "rtmp://live.twitch.tv/app", "Get Stream Key from Twitch Creator Dashboard -> Stream"),
    FACEBOOK("Facebook Live", "rtmps://live-api-s.facebook.com:443/rtmp", "Get Stream Key from Facebook Live Producer"),
    CUSTOM_RTMP("Custom RTMP / RTMPS", "rtmp://", "Enter custom server RTMP/RTMPS ingest URL and stream key")
}

enum class ResolutionOption(val width: Int, val height: Int, val label: String, val recommendedBitrate: Int) {
    RES_360P(640, 360, "360p (Fast)", 1200),
    RES_480P(854, 480, "480p (Standard)", 2000),
    RES_720P(1280, 720, "720p HD (Balanced)", 3500),
    RES_1080P(1920, 1080, "1080p FHD (Pro Gaming)", 6000)
}

enum class FpsOption(val value: Int, val label: String) {
    FPS_24(24, "24 FPS (Cinematic)"),
    FPS_30(30, "30 FPS (Standard)"),
    FPS_60(60, "60 FPS (Ultra Smooth)")
}

enum class VideoCodecType(val displayName: String, val mimeType: String) {
    H264_AVC("H.264 / AVC (Broad Compatibility)", "video/avc"),
    H265_HEVC("H.265 / HEVC (High Efficiency)", "video/hevc")
}

enum class RateControlMode(val displayName: String) {
    CBR("CBR (Constant Bitrate - Best for Live)"),
    VBR("VBR (Variable Bitrate)")
}

data class StreamConfig(
    val platform: PlatformType = PlatformType.YOUTUBE,
    val streamKey: String = "",
    val rtmpUrl: String = PlatformType.YOUTUBE.defaultRtmpUrl,
    val streamTitle: String = "BGMI Championship Rush | VIPER STUDIO Live",
    val streamDescription: String = "Streaming mobile gameplay in 1080p 60FPS using VIPER STUDIO on Android.",
    val gameCategory: String = "BGMI / PUBG Mobile",
    val resolution: ResolutionOption = ResolutionOption.RES_720P,
    val fps: FpsOption = FpsOption.FPS_30,
    val bitrateKbps: Int = 3500,
    val codec: VideoCodecType = VideoCodecType.H264_AVC,
    val rateControl: RateControlMode = RateControlMode.CBR,
    val keyframeIntervalSec: Int = 2,
    val isAdaptiveBitrateEnabled: Boolean = true,
    val isLowLatencyEnabled: Boolean = true,
    val isLandscape: Boolean = true
)

enum class AudioCaptureMode(val displayName: String, val shortLabel: String) {
    INTERNAL_AND_MIC("Internal + Microphone (Game & Voice)", "Game + Mic"),
    INTERNAL_ONLY("Internal Only (Game Audio Only)", "Game Only"),
    MIC_ONLY("Microphone Only (Voice Only)", "Mic Only")
}

data class AudioMixConfig(
    val gameAudioVolume: Float = 1.0f, // 0.0 to 2.0 (200% boost)
    val micAudioVolume: Float = 0.85f, // 0.0 to 2.0 (calibrated for clear vocal headroom)
    val micGain: Float = 0.85f,        // 0.0 to 2.0 (calibrated preamp gain, prevents hardware clipping)
    val isInternalAudioEnabled: Boolean = true,
    val isMicAudioEnabled: Boolean = true,
    val isMicMuted: Boolean = false,
    val isNoiseSuppressionEnabled: Boolean = true,
    val isEchoCancellationEnabled: Boolean = true,
    val isHighPassFilter100HzEnabled: Boolean = true, // 100 Hz High-Pass Filter (eliminates sub-bass rumble and buzzing)
    val audioDelayMs: Int = 0,         // -500ms to +500ms sync delay
    val sampleRateHz: Int = 48000,
    val audioCaptureMode: AudioCaptureMode = AudioCaptureMode.INTERNAL_AND_MIC
)

enum class NetworkHealth(val label: String, val hexColor: Long) {
    EXCELLENT("Excellent (Stable)", 0xFF00E676),
    GOOD("Good (Normal)", 0xFF69F0AE),
    FAIR("Fair (Slight Jitter)", 0xFFFFD600),
    POOR("Poor (Frame Drops)", 0xFFFF5252),
    RECONNECTING("Reconnecting...", 0xFFFF1744)
}

data class StreamStats(
    val isStreaming: Boolean = false,
    val isConnecting: Boolean = false,
    val isRecording: Boolean = false,
    val isPaused: Boolean = false,
    val uptimeSeconds: Long = 0,
    val currentBitrateKbps: Int = 0,
    val targetBitrateKbps: Int = 3500,
    val currentFps: Int = 0,
    val targetFps: Int = 30,
    val droppedFrames: Long = 0,
    val totalFrames: Long = 0,
    val networkHealth: NetworkHealth = NetworkHealth.EXCELLENT,
    val bufferHealthPct: Int = 98,
    val rttLatencyMs: Int = 42,
    val deviceTempC: Float = 36.5f,
    val mirrorServerUrl: String? = null,
    val mirrorConnectedClients: Int = 0,
    val errorMessage: String? = null
)

enum class LayerType(val displayName: String) {
    GAME_SCREEN("Game Screen Capture"),
    CAMERA_PIP("Facecam / Camera PiP"),
    TEXT_BANNER("Text Banner / Ticker"),
    IMAGE_OVERLAY("Logo / Watermark"),
    SOCIAL_BADGE("Social Media Tag"),
    ALERT_BOX("Live Alert Box")
}

data class SceneLayer(
    val id: String,
    val type: LayerType,
    val label: String,
    val xPct: Float,       // 0..100% position on canvas
    val yPct: Float,
    val widthPct: Float,   // 5..100%
    val heightPct: Float,
    val rotationDeg: Float = 0f,
    val opacity: Float = 1.0f, // 0.0 to 1.0
    val isVisible: Boolean = true,
    val isLocked: Boolean = false,
    val zIndex: Int = 0,
    val textContent: String = "",
    val textColorHex: String = "#00E676",
    val bgColorHex: String = "#B30F1722",
    val pipShape: String = "ROUNDED", // ROUNDED, CIRCLE, SQUARE
    val isMarqueeScroll: Boolean = false,
    val imageUrl: String = ""
)

data class ScenePreset(
    val id: String,
    val name: String,
    val isDefault: Boolean = false,
    val layers: List<SceneLayer>
)

data class YouTubeChannelInfo(
    val isConnected: Boolean = false,
    val accountEmail: String = "",
    val channelId: String = "",
    val channelTitle: String = "",
    val channelHandle: String = "",
    val subscriberCountText: String = "",
    val avatarUrl: String = "",
    val bannerUrl: String = "",
    val activeBroadcastTitle: String = "",
    val activeBroadcastId: String = "",
    val activeLiveChatId: String = "",
    val streamKey: String = "",
    val rtmpIngestUrl: String = "rtmp://a.rtmp.youtube.com/live2",
    val lastSyncTimeMs: Long = 0L,
    val isYouTubeAuthorized: Boolean = false,
    val liveUrl: String = "",
    val streamStatus: String = "",
    val lifeCycleStatus: String = "",
    val isLiveVerified: Boolean = false,
    val oauthAccessToken: String = ""
)

data class LiveChatSettings(
    val isOverlayEnabled: Boolean = true,
    val isDraggable: Boolean = true,
    val backgroundOpacity: Float = 0.65f, // 0.0f (transparent) to 1.0f (solid)
    val fontSizeSp: Int = 12,            // 10, 12, 14
    val showBadges: Boolean = true,
    val maxMessages: Int = 8,
    val xPct: Float = 5f,                // Draggable canvas X position (0-100%)
    val yPct: Float = 45f,               // Draggable canvas Y position (0-100%)
    val widthPct: Float = 55f,
    val heightPct: Float = 35f,
    val isLocked: Boolean = false        // Locked prevents accidental dragging
)

data class ChatMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val sender: String,
    val text: String,
    val avatarUrl: String = "",
    val badgeColorHex: String = "#00E676",
    val badgeText: String = "",
    val isSuperChat: Boolean = false,
    val amount: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

enum class AlertType(val title: String, val soundTone: String) {
    SUBSCRIBER("New Subscriber!", "FANFARE"),
    SUPER_CHAT("Super Chat!", "CHIME_HIGH"),
    DONATION("Donation Received!", "CASH_REGISTER"),
    FOLLOWER("New Follower!", "BELL_SYNTH")
}

data class AlertEvent(
    val id: String,
    val type: AlertType,
    val username: String,
    val amount: String = "",
    val message: String = "",
    val durationSec: Int = 4,
    val timestamp: Long = System.currentTimeMillis(),
    val colorHex: String = "#00E676"
)

data class RecordingItem(
    val id: Long = 0,
    val title: String,
    val filePath: String,
    val durationSeconds: Long,
    val fileSizeBytes: Long,
    val resolution: String,
    val timestamp: Long
)
