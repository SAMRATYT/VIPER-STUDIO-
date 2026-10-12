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
            micAudioVolume = prefs.getFloat(KEY_MIC_VOLUME, 1.2f),
            isMicMuted = prefs.getBoolean(KEY_MIC_MUTED, false),
            isNoiseSuppressionEnabled = prefs.getBoolean(KEY_NOISE_SUPPRESSION, true),
            isEchoCancellationEnabled = prefs.getBoolean(KEY_ECHO_CANCELLATION, true),
            audioDelayMs = prefs.getInt(KEY_AUDIO_DELAY_MS, 0),
            sampleRateHz = 48000,
            audioCaptureMode = mode
        )
    }

    fun saveAudioConfig(config: AudioMixConfig) {
        prefs.edit().apply {
            putBoolean(KEY_NOISE_SUPPRESSION, config.isNoiseSuppressionEnabled)
            putBoolean(KEY_ECHO_CANCELLATION, config.isEchoCancellationEnabled)
            putString(KEY_AUDIO_MODE, config.audioCaptureMode.name)
            putFloat(KEY_GAME_VOLUME, config.gameAudioVolume)
            putFloat(KEY_MIC_VOLUME, config.micAudioVolume)
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
}
