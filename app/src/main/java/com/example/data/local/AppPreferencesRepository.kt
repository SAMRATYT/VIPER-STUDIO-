package com.example.data.local

import android.content.Context
import android.content.SharedPreferences
import com.example.data.model.AudioCaptureMode
import com.example.data.model.AudioMixConfig
import com.example.data.model.FpsOption
import com.example.data.model.PlatformType
import com.example.data.model.ResolutionOption
import com.example.data.model.StreamConfig
import com.example.data.model.VideoCodecType

/**
 * Repository for persisting and restoring VIPER STUDIO stream and audio settings.
 * Ensures user configurations (Noise Suppression, Orientation, Bitrate, Resolution, etc.)
 * survive application restarts.
 */
class AppPreferencesRepository(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "viper_studio_user_preferences"

        // Audio Keys
        private const val KEY_NOISE_SUPPRESSION = "key_audio_noise_suppression"
        private const val KEY_ECHO_CANCELLATION = "key_audio_echo_cancellation"
        private const val KEY_AUDIO_MODE = "key_audio_capture_mode"
        private const val KEY_GAME_VOLUME = "key_audio_game_volume"
        private const val KEY_MIC_VOLUME = "key_audio_mic_volume"
        private const val KEY_MIC_GAIN = "key_audio_mic_gain"
        private const val KEY_HIGH_PASS_FILTER_100HZ = "key_audio_high_pass_filter_100hz"
        private const val KEY_MIC_MUTED = "key_audio_mic_muted"
        private const val KEY_AUDIO_DELAY_MS = "key_audio_delay_ms"

        // Video & Stream Keys
        private const val KEY_IS_LANDSCAPE = "key_video_is_landscape"
        private const val KEY_RESOLUTION = "key_video_resolution"
        private const val KEY_FPS = "key_video_fps"
        private const val KEY_BITRATE_KBPS = "key_video_bitrate_kbps"
        private const val KEY_CODEC = "key_video_codec"
        private const val KEY_ADAPTIVE_BITRATE = "key_stream_adaptive_bitrate"
        private const val KEY_LOW_LATENCY = "key_stream_low_latency"
        private const val KEY_PLATFORM = "key_stream_platform"
        private const val KEY_RTMP_URL = "key_stream_rtmp_url"
        private const val KEY_STREAM_KEY = "key_stream_key"
        private const val KEY_STREAM_TITLE = "key_stream_title"
        private const val KEY_GAME_CATEGORY = "key_game_category"
    }

    fun loadAudioConfig(): AudioMixConfig {
        val modeStr = prefs.getString(KEY_AUDIO_MODE, AudioCaptureMode.INTERNAL_AND_MIC.name)
        val mode = try {
            AudioCaptureMode.valueOf(modeStr ?: AudioCaptureMode.INTERNAL_AND_MIC.name)
        } catch (_: Exception) {
            AudioCaptureMode.INTERNAL_AND_MIC
        }

        return AudioMixConfig(
            gameAudioVolume = prefs.getFloat(KEY_GAME_VOLUME, 1.0f),
            micAudioVolume = prefs.getFloat(KEY_MIC_VOLUME, 0.85f),
            micGain = prefs.getFloat(KEY_MIC_GAIN, 0.85f),
            isInternalAudioEnabled = prefs.getBoolean("key_internal_audio_enabled", true),
            isMicAudioEnabled = prefs.getBoolean("key_mic_audio_enabled", true),
            isMicMuted = prefs.getBoolean(KEY_MIC_MUTED, false),
            isNoiseSuppressionEnabled = prefs.getBoolean(KEY_NOISE_SUPPRESSION, true),
            isEchoCancellationEnabled = prefs.getBoolean(KEY_ECHO_CANCELLATION, true),
            isHighPassFilter100HzEnabled = prefs.getBoolean(KEY_HIGH_PASS_FILTER_100HZ, true),
            audioDelayMs = prefs.getInt(KEY_AUDIO_DELAY_MS, 0),
            sampleRateHz = 48000,
            audioCaptureMode = mode
        )
    }

    fun saveAudioConfig(config: AudioMixConfig) {
        prefs.edit().apply {
            putBoolean(KEY_NOISE_SUPPRESSION, config.isNoiseSuppressionEnabled)
            putBoolean(KEY_ECHO_CANCELLATION, config.isEchoCancellationEnabled)
            putBoolean(KEY_HIGH_PASS_FILTER_100HZ, config.isHighPassFilter100HzEnabled)
            putString(KEY_AUDIO_MODE, config.audioCaptureMode.name)
            putFloat(KEY_GAME_VOLUME, config.gameAudioVolume)
            putFloat(KEY_MIC_VOLUME, config.micAudioVolume)
            putFloat(KEY_MIC_GAIN, config.micGain)
            putBoolean("key_internal_audio_enabled", config.isInternalAudioEnabled)
            putBoolean("key_mic_audio_enabled", config.isMicAudioEnabled)
            putBoolean(KEY_MIC_MUTED, config.isMicMuted)
            putInt(KEY_AUDIO_DELAY_MS, config.audioDelayMs)
            apply()
        }
    }

    fun loadStreamConfig(): StreamConfig {
        val platformStr = prefs.getString(KEY_PLATFORM, PlatformType.YOUTUBE.name)
        val platform = try {
            PlatformType.valueOf(platformStr ?: PlatformType.YOUTUBE.name)
        } catch (_: Exception) {
            PlatformType.YOUTUBE
        }

        val resStr = prefs.getString(KEY_RESOLUTION, ResolutionOption.RES_720P.name)
        val resolution = try {
            ResolutionOption.valueOf(resStr ?: ResolutionOption.RES_720P.name)
        } catch (_: Exception) {
            ResolutionOption.RES_720P
        }

        val fpsStr = prefs.getString(KEY_FPS, FpsOption.FPS_30.name)
        val fps = try {
            FpsOption.valueOf(fpsStr ?: FpsOption.FPS_30.name)
        } catch (_: Exception) {
            FpsOption.FPS_30
        }

        val codecStr = prefs.getString(KEY_CODEC, VideoCodecType.H264_AVC.name)
        val codec = try {
            VideoCodecType.valueOf(codecStr ?: VideoCodecType.H264_AVC.name)
        } catch (_: Exception) {
            VideoCodecType.H264_AVC
        }

        return StreamConfig(
            platform = platform,
            streamKey = prefs.getString(KEY_STREAM_KEY, "") ?: "",
            rtmpUrl = prefs.getString(KEY_RTMP_URL, platform.defaultRtmpUrl) ?: platform.defaultRtmpUrl,
            streamTitle = prefs.getString(KEY_STREAM_TITLE, "BGMI Championship Rush | VIPER STUDIO Live") ?: "BGMI Championship Rush | VIPER STUDIO Live",
            gameCategory = prefs.getString(KEY_GAME_CATEGORY, "BGMI / PUBG Mobile") ?: "BGMI / PUBG Mobile",
            resolution = resolution,
            fps = fps,
            bitrateKbps = prefs.getInt(KEY_BITRATE_KBPS, resolution.recommendedBitrate),
            codec = codec,
            isAdaptiveBitrateEnabled = prefs.getBoolean(KEY_ADAPTIVE_BITRATE, true),
            isLowLatencyEnabled = prefs.getBoolean(KEY_LOW_LATENCY, true),
            isLandscape = prefs.getBoolean(KEY_IS_LANDSCAPE, true)
        )
    }

    fun saveStreamConfig(config: StreamConfig) {
        prefs.edit().apply {
            putBoolean(KEY_IS_LANDSCAPE, config.isLandscape)
            putString(KEY_PLATFORM, config.platform.name)
            putString(KEY_STREAM_KEY, config.streamKey)
            putString(KEY_RTMP_URL, config.rtmpUrl)
            putString(KEY_STREAM_TITLE, config.streamTitle)
            putString(KEY_GAME_CATEGORY, config.gameCategory)
            putString(KEY_RESOLUTION, config.resolution.name)
            putString(KEY_FPS, config.fps.name)
            putInt(KEY_BITRATE_KBPS, config.bitrateKbps)
            putString(KEY_CODEC, config.codec.name)
            putBoolean(KEY_ADAPTIVE_BITRATE, config.isAdaptiveBitrateEnabled)
            putBoolean(KEY_LOW_LATENCY, config.isLowLatencyEnabled)
            apply()
        }
    }

    // YouTube Channel Info Persistence
    fun loadYouTubeChannelInfo(): com.example.data.model.YouTubeChannelInfo {
        return com.example.data.model.YouTubeChannelInfo(
            isConnected = prefs.getBoolean("yt_is_connected", false),
            accountEmail = prefs.getString("yt_account_email", "") ?: "",
            channelId = prefs.getString("yt_channel_id", "") ?: "",
            channelTitle = prefs.getString("yt_channel_title", "") ?: "",
            channelHandle = prefs.getString("yt_channel_handle", "") ?: "",
            subscriberCountText = prefs.getString("yt_sub_count", "") ?: "",
            avatarUrl = prefs.getString("yt_avatar_url", "") ?: "",
            bannerUrl = prefs.getString("yt_banner_url", "") ?: "",
            activeBroadcastTitle = prefs.getString("yt_broadcast_title", "") ?: "",
            activeBroadcastId = prefs.getString("yt_broadcast_id", "") ?: "",
            activeLiveChatId = prefs.getString("yt_chat_id", "") ?: "",
            streamKey = prefs.getString("yt_stream_key", "") ?: "",
            rtmpIngestUrl = prefs.getString("yt_ingest_url", "rtmp://a.rtmp.youtube.com/live2") ?: "rtmp://a.rtmp.youtube.com/live2",
            lastSyncTimeMs = prefs.getLong("yt_last_sync", 0L),
            isYouTubeAuthorized = prefs.getBoolean("yt_is_authorized", false)
        )
    }

    fun saveYouTubeChannelInfo(info: com.example.data.model.YouTubeChannelInfo) {
        prefs.edit().apply {
            putBoolean("yt_is_connected", info.isConnected)
            putString("yt_account_email", info.accountEmail)
            putString("yt_channel_id", info.channelId)
            putString("yt_channel_title", info.channelTitle)
            putString("yt_channel_handle", info.channelHandle)
            putString("yt_sub_count", info.subscriberCountText)
            putString("yt_avatar_url", info.avatarUrl)
            putString("yt_banner_url", info.bannerUrl)
            putString("yt_broadcast_title", info.activeBroadcastTitle)
            putString("yt_broadcast_id", info.activeBroadcastId)
            putString("yt_chat_id", info.activeLiveChatId)
            putString("yt_stream_key", info.streamKey)
            putString("yt_ingest_url", info.rtmpIngestUrl)
            putLong("yt_last_sync", info.lastSyncTimeMs)
            putBoolean("yt_is_authorized", info.isYouTubeAuthorized)
            apply()
        }
    }

    // Live Chat Settings Persistence
    fun loadLiveChatSettings(): com.example.data.model.LiveChatSettings {
        return com.example.data.model.LiveChatSettings(
            isOverlayEnabled = prefs.getBoolean("chat_overlay_enabled", true),
            isDraggable = prefs.getBoolean("chat_draggable", true),
            backgroundOpacity = prefs.getFloat("chat_bg_opacity", 0.65f),
            fontSizeSp = prefs.getInt("chat_font_size", 12),
            showBadges = prefs.getBoolean("chat_show_badges", true),
            maxMessages = prefs.getInt("chat_max_msgs", 8),
            xPct = prefs.getFloat("chat_x_pct", 5f),
            yPct = prefs.getFloat("chat_y_pct", 45f),
            widthPct = prefs.getFloat("chat_width_pct", 55f),
            heightPct = prefs.getFloat("chat_height_pct", 35f),
            isLocked = prefs.getBoolean("chat_is_locked", false)
        )
    }

    fun saveLiveChatSettings(settings: com.example.data.model.LiveChatSettings) {
        prefs.edit().apply {
            putBoolean("chat_overlay_enabled", settings.isOverlayEnabled)
            putBoolean("chat_draggable", settings.isDraggable)
            putFloat("chat_bg_opacity", settings.backgroundOpacity)
            putInt("chat_font_size", settings.fontSizeSp)
            putBoolean("chat_show_badges", settings.showBadges)
            putInt("chat_max_msgs", settings.maxMessages)
            putFloat("chat_x_pct", settings.xPct)
            putFloat("chat_y_pct", settings.yPct)
            putFloat("chat_width_pct", settings.widthPct)
            putFloat("chat_height_pct", settings.heightPct)
            putBoolean("chat_is_locked", settings.isLocked)
            apply()
        }
    }
}
