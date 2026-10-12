package com.example.engine.hardware

import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import android.os.Build

data class CodecInfo(
    val name: String,
    val mimeType: String,
    val isHardwareAccelerated: Boolean,
    val maxBitrateKbps: Int,
    val maxWidth: Int,
    val maxHeight: Int,
    val maxSupportedFps: Int
)

data class DeviceHardwareProfile(
    val deviceModel: String,
    val androidVersion: String,
    val hasHardwareAvc: Boolean,
    val hasHardwareHevc: Boolean,
    val preferredVideoCodec: String,
    val avcCodecs: List<CodecInfo>,
    val hevcCodecs: List<CodecInfo>,
    val hasAudioPlaybackCapture: Boolean,
    val hasAcousticEchoCanceler: Boolean,
    val hasNoiseSuppressor: Boolean,
    val recommendedResolution: String,
    val recommendedFps: Int,
    val recommendedBitrateKbps: Int
)

object HardwareEncoderDetector {

    fun detectHardwareProfile(): DeviceHardwareProfile {
        val codecList = MediaCodecList(MediaCodecList.REGULAR_CODECS)
        val allCodecs = codecList.codecInfos.filter { it.isEncoder }

        val avcList = mutableListOf<CodecInfo>()
        val hevcList = mutableListOf<CodecInfo>()

        for (info in allCodecs) {
            for (type in info.supportedTypes) {
                if (type.equals("video/avc", ignoreCase = true)) {
                    avcList.add(inspectCodec(info, type))
                } else if (type.equals("video/hevc", ignoreCase = true)) {
                    hevcList.add(inspectCodec(info, type))
                }
            }
        }

        val hasHardwareAvc = avcList.any { it.isHardwareAccelerated }
        val hasHardwareHevc = hevcList.any { it.isHardwareAccelerated }

        val hasEchoCanceler = try {
            AcousticEchoCanceler.isAvailable()
        } catch (_: Exception) {
            false
        }

        val hasNoiseSuppressor = try {
            NoiseSuppressor.isAvailable()
        } catch (_: Exception) {
            false
        }

        val hasAudioPlaybackCapture = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

        // Recommendations tailored for Moto G96 5G and modern mid-to-flagship chips
        val recRes = if (hasHardwareAvc) "720p HD (1280x720)" else "480p (854x480)"
        val recFps = 30
        val recBitrate = if (hasHardwareAvc) 3500 else 2000

        return DeviceHardwareProfile(
            deviceModel = "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}",
            androidVersion = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
            hasHardwareAvc = hasHardwareAvc,
            hasHardwareHevc = hasHardwareHevc,
            preferredVideoCodec = if (hasHardwareAvc) "H.264/AVC (Hardware)" else "H.264 (Software)",
            avcCodecs = avcList,
            hevcCodecs = hevcList,
            hasAudioPlaybackCapture = hasAudioPlaybackCapture,
            hasAcousticEchoCanceler = hasEchoCanceler,
            hasNoiseSuppressor = hasNoiseSuppressor,
            recommendedResolution = recRes,
            recommendedFps = recFps,
            recommendedBitrateKbps = recBitrate
        )
    }

    private fun inspectCodec(info: MediaCodecInfo, mimeType: String): CodecInfo {
        var isHw = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            isHw = info.isHardwareAccelerated
        } else {
            val nameLower = info.name.lowercase()
            isHw = !nameLower.startsWith("omx.google.") && !nameLower.startsWith("c2.android.")
        }

        var maxWidth = 1920
        var maxHeight = 1080
        var maxFps = 60
        var maxBitrate = 12000

        try {
            val caps = info.getCapabilitiesForType(mimeType)
            val videoCaps = caps.videoCapabilities
            if (videoCaps != null) {
                maxWidth = videoCaps.supportedWidths?.upper ?: 1920
                maxHeight = videoCaps.supportedHeights?.upper ?: 1080
                maxFps = videoCaps.supportedFrameRates?.upper ?: 60
                maxBitrate = (videoCaps.bitrateRange?.upper ?: 12000000) / 1000
            }
        } catch (_: Exception) {
            // fallback defaults
        }

        return CodecInfo(
            name = info.name,
            mimeType = mimeType,
            isHardwareAccelerated = isHw,
            maxBitrateKbps = maxBitrate,
            maxWidth = maxWidth,
            maxHeight = maxHeight,
            maxSupportedFps = maxFps
        )
    }
}
