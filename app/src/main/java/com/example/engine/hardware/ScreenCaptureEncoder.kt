package com.example.engine.hardware

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.Process
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.data.model.AudioCaptureMode
import com.example.data.model.AudioMixConfig
import com.example.data.model.ResolutionOption
import com.example.engine.audio.AudioDeviceRoutingManager
import com.example.engine.audio.AudioJitterBuffer
import com.example.engine.audio.AudioProcessingUtils
import com.example.engine.audio.AudioRouteChangeListener
import java.nio.ByteBuffer
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.max

/**
 * Listener interface for consumers of encoded H.264 video and AAC audio frames.
 * Allows RTMP streaming and Screen Recording to share a single VirtualDisplay and MediaCodec pipeline.
 */
interface VideoFrameListener {
    fun onVideoFormatAvailable(format: MediaFormat, sps: ByteArray, pps: ByteArray)
    fun onVideoSampleAvailable(
        byteBuffer: ByteBuffer,
        bufferInfo: MediaCodec.BufferInfo,
        isKeyFrame: Boolean,
        sps: ByteArray,
        pps: ByteArray
    )
    fun onVideoEndOfStream() {}
}

interface AudioFrameListener {
    fun onAudioFormatAvailable(format: MediaFormat, audioSpecificConfig: ByteArray)
    fun onAudioSampleAvailable(byteBuffer: ByteBuffer, bufferInfo: MediaCodec.BufferInfo)
    fun onAudioEndOfStream() {}
}

/**
 * Production-grade hardware screen capture and low-latency encoding engine.
 * Specifically tuned for Qualcomm Snapdragon / Motorola Moto G96 5G running Android 14-16.
 *
 * Implements decoupled multi-threaded audio capture, jitter buffering, soft-clip limiting,
 * and monotonic presentation timestamp synchronization for live broadcast and recording.
 */
class ScreenCaptureEncoder(private val context: Context) : AudioRouteChangeListener {

    companion object {
        const val TAG_PROJECTION = "ViperMediaProjection"
        const val TAG_VIDEO = "ViperVideoEncoder"
        const val TAG_AUDIO = "ViperAudioEngine"
        const val TAG_DIAGNOSTICS = "ViperAudioDiagnostics"

        private const val SAMPLE_RATE = AudioProcessingUtils.SAMPLE_RATE_HZ // 48000 Hz
        private const val CHANNELS = AudioProcessingUtils.CHANNEL_COUNT_STEREO // 2 Channels (Stereo)
        private const val FRAME_SHORTS = AudioProcessingUtils.FRAME_SIZE_SHORTS // 2048 shorts (1024 stereo frames)

        @Volatile
        private var instance: ScreenCaptureEncoder? = null

        fun getInstance(context: Context): ScreenCaptureEncoder {
            return instance ?: synchronized(this) {
                instance ?: ScreenCaptureEncoder(context.applicationContext).also { instance = it }
            }
        }
    }

    private val isRunning = AtomicBoolean(false)
    private val isVideoRunning = AtomicBoolean(false)
    private val isAudioRunning = AtomicBoolean(false)

    // Sinks / Listeners
    private val videoListeners = CopyOnWriteArrayList<VideoFrameListener>()
    private val audioListeners = CopyOnWriteArrayList<AudioFrameListener>()

    // MediaProjection & VirtualDisplay
    private var activeProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null

    // Video Encoder
    private var videoCodec: MediaCodec? = null
    private var videoThread: HandlerThread? = null
    private var videoHandler: Handler? = null
    private var currentSps: ByteArray = byteArrayOf()
    private var currentPps: ByteArray = byteArrayOf()
    private var currentVideoFormat: MediaFormat? = null

    // Audio Capture & MediaCodec
    private var audioRecordMic: AudioRecord? = null
    private var audioRecordGame: AudioRecord? = null
    private var audioCodec: MediaCodec? = null

    // Dedicated Independent Audio Threads
    private var micCaptureThread: Thread? = null
    private var gameCaptureThread: Thread? = null
    private var audioMixPacerThread: Thread? = null
    private var audioDrainThread: Thread? = null

    // Decoupled Jitter Buffers
    private val micJitterBuffer = AudioJitterBuffer("MicBuffer", maxCapacity = 16)
    private val gameJitterBuffer = AudioJitterBuffer("GameBuffer", maxCapacity = 16)

    // Hardware Audio Route Manager
    private val routingManager = AudioDeviceRoutingManager(context)

    // Audio Formats and Configuration
    private var currentAudioFormat: MediaFormat? = null
    // AAC-LC 48kHz Stereo default AudioSpecificConfig: 0x11, 0x90
    private var currentAudioSpecificConfig: ByteArray = byteArrayOf(0x11, 0x90.toByte())

    // Real-Time Audio Settings (Thread-safe)
    @Volatile var micVolume: Float = 1.2f
    @Volatile var gameVolume: Float = 1.0f
    @Volatile var isInternalAudioEnabled: Boolean = true
    @Volatile var isMicAudioEnabled: Boolean = true
    @Volatile var isMicMuted: Boolean = false
    @Volatile var audioDelayMs: Int = 0
    @Volatile var captureMode: AudioCaptureMode = AudioCaptureMode.INTERNAL_AND_MIC
    @Volatile var isNoiseSuppressionEnabled: Boolean = true
    @Volatile var isEchoCancellationEnabled: Boolean = true

    // Audio Capture Error and Restriction State
    @Volatile var audioCaptureError: String? = null
    @Volatile var isPlaybackCaptureBlocked: Boolean = false

    // Hardware Audio Effects (Applied to microphone only, keeping internal game audio untouched)
    private var micNoiseSuppressor: NoiseSuppressor? = null
    private var micEchoCanceler: AcousticEchoCanceler? = null

    // Monotonic Time Base (Synced between Video and Audio)
    private val presentationTimeBaseUs = AtomicLong(0)
    private val totalAudioFramesWritten = AtomicLong(0)

    // Live Audio VU Levels
    @Volatile var liveMicLevel: Float = 0f
    @Volatile var liveGameLevel: Float = 0f

    // Diagnostic Counters
    private val audioEncoderQueueErrors = AtomicLong(0)
    private val audioAutoRecoveries = AtomicLong(0)
    private val audioDroppedBuffers = AtomicLong(0)

    fun updateAudioConfig(config: AudioMixConfig) {
        micVolume = config.micAudioVolume
        gameVolume = config.gameAudioVolume
        isInternalAudioEnabled = config.isInternalAudioEnabled
        isMicAudioEnabled = config.isMicAudioEnabled
        isMicMuted = config.isMicMuted
        audioDelayMs = config.audioDelayMs
        captureMode = config.audioCaptureMode
        isNoiseSuppressionEnabled = config.isNoiseSuppressionEnabled
        isEchoCancellationEnabled = config.isEchoCancellationEnabled

        applyMicHardwareEffects(config.isNoiseSuppressionEnabled, config.isEchoCancellationEnabled)
        Log.d(TAG_AUDIO, "Audio config updated: mode=${captureMode.name}, internalEnabled=$isInternalAudioEnabled, micEnabled=$isMicAudioEnabled, micVol=$micVolume, gameVol=$gameVolume, muted=$isMicMuted, delay=${audioDelayMs}ms, noiseReduction=${config.isNoiseSuppressionEnabled}")
    }

    private fun applyMicHardwareEffects(enableNoiseReduction: Boolean, enableEchoCancellation: Boolean) {
        val record = audioRecordMic ?: return
        val session = record.audioSessionId
        if (session <= 0) return

        try {
            if (NoiseSuppressor.isAvailable()) {
                if (enableNoiseReduction) {
                    if (micNoiseSuppressor == null) {
                        micNoiseSuppressor = NoiseSuppressor.create(session)
                    }
                    micNoiseSuppressor?.enabled = true
                    Log.i(TAG_AUDIO, "Hardware NoiseSuppressor enabled on mic session $session (game audio unaffected)")
                } else {
                    micNoiseSuppressor?.enabled = false
                    micNoiseSuppressor?.release()
                    micNoiseSuppressor = null
                    Log.i(TAG_AUDIO, "Hardware NoiseSuppressor disabled - natural raw microphone audio preserved")
                }
            } else {
                Log.d(TAG_AUDIO, "Hardware NoiseSuppressor not available on this device chipset")
            }

            if (AcousticEchoCanceler.isAvailable()) {
                if (enableEchoCancellation) {
                    if (micEchoCanceler == null) {
                        micEchoCanceler = AcousticEchoCanceler.create(session)
                    }
                    micEchoCanceler?.enabled = true
                } else {
                    micEchoCanceler?.enabled = false
                    micEchoCanceler?.release()
                    micEchoCanceler = null
                }
            }
        } catch (e: Exception) {
            Log.w(TAG_AUDIO, "Exception managing mic hardware audio effects", e)
        }
    }

    private fun releaseMicHardwareEffects() {
        try {
            micNoiseSuppressor?.enabled = false
            micNoiseSuppressor?.release()
            micNoiseSuppressor = null
            micEchoCanceler?.enabled = false
            micEchoCanceler?.release()
            micEchoCanceler = null
        } catch (e: Exception) {
            Log.w(TAG_AUDIO, "Error releasing mic audio effects", e)
        }
    }

    override fun onAudioRouteChanged(description: String) {
        Log.i(TAG_AUDIO, "Audio route changed: $description. Checking microphone state.")
        // If mic capture is running, signal safe re-initialization if device connection changed
        if (isAudioRunning.get()) {
            audioAutoRecoveries.incrementAndGet()
        }
    }

    fun registerVideoListener(listener: VideoFrameListener) {
        if (!videoListeners.contains(listener)) {
            videoListeners.add(listener)
            currentVideoFormat?.let { format ->
                if (currentSps.isNotEmpty() && currentPps.isNotEmpty()) {
                    listener.onVideoFormatAvailable(format, currentSps, currentPps)
                }
            }
        }
    }

    fun unregisterVideoListener(listener: VideoFrameListener) {
        videoListeners.remove(listener)
        checkAutoStop()
    }

    fun registerAudioListener(listener: AudioFrameListener) {
        if (!audioListeners.contains(listener)) {
            audioListeners.add(listener)
            currentAudioFormat?.let { format ->
                listener.onAudioFormatAvailable(format, currentAudioSpecificConfig)
            }
        }
    }

    fun unregisterAudioListener(listener: AudioFrameListener) {
        audioListeners.remove(listener)
        checkAutoStop()
    }

    fun isAudioActive(): Boolean = isAudioRunning.get()
    fun isVideoActive(): Boolean = isVideoRunning.get()
    fun isCaptureRunning(): Boolean = isRunning.get()

    private fun checkAutoStop() {
        if (videoListeners.isEmpty() && audioListeners.isEmpty() && isRunning.get()) {
            Log.i(TAG_VIDEO, "No remaining active listeners for screen capture. Shutting down encoder pipeline.")
            stopCapture()
        }
    }

    /**
     * Starts the hardware screen capture and encoder pipeline.
     */
    @Synchronized
    fun startCapture(
        projection: MediaProjection,
        resolution: ResolutionOption,
        targetFps: Int,
        bitrateKbps: Int,
        isLandscape: Boolean = true
    ): Boolean {
        if (isRunning.get()) {
            Log.i(TAG_VIDEO, "ScreenCaptureEncoder is already active. Attaching new listener without re-creating VirtualDisplay.")
            return true
        }

        activeProjection = projection
        isRunning.set(true)
        presentationTimeBaseUs.set(System.nanoTime() / 1000L)
        totalAudioFramesWritten.set(0)

        micJitterBuffer.resetStats()
        gameJitterBuffer.resetStats()
        audioEncoderQueueErrors.set(0)
        audioAutoRecoveries.set(0)

        routingManager.startListening(this)

        val successVideo = initVideoEncoder(projection, resolution, targetFps, bitrateKbps, isLandscape)
        val successAudio = initAudioEncoder(projection)

        Log.i(
            TAG_VIDEO,
            "ScreenCaptureEncoder pipeline started. Video: $successVideo, Audio: $successAudio (Orientation: ${if (isLandscape) "Landscape 16:9" else "Portrait 9:16"}, ${resolution.label}, $targetFps fps, $bitrateKbps kbps)"
        )
        return successVideo
    }

    @SuppressLint("WrongConstant")
    private fun initVideoEncoder(
        projection: MediaProjection,
        resolution: ResolutionOption,
        targetFps: Int,
        bitrateKbps: Int,
        isLandscape: Boolean
    ): Boolean {
        return try {
            val mimeType = MediaFormat.MIMETYPE_VIDEO_AVC

            // Support vertical (portrait: 720x1280) and horizontal (landscape: 1280x720)
            val rawWidth = if (isLandscape) resolution.width else resolution.height
            val rawHeight = if (isLandscape) resolution.height else resolution.width

            // Native 1080p and 720p dimensions must NOT be padded. For arbitrary resolutions, align to 16.
            val targetWidth = if (rawWidth == 1920 || rawWidth == 1280 || rawWidth == 720 || rawWidth == 1080) {
                rawWidth
            } else {
                ((rawWidth + 15) / 16) * 16
            }
            val targetHeight = if (rawHeight == 1080 || rawHeight == 720 || rawHeight == 1280 || rawHeight == 1920) {
                rawHeight
            } else {
                ((rawHeight + 15) / 16) * 16
            }

            val fpsVal = targetFps.coerceIn(24, 60)
            val bitrateVal = (bitrateKbps * 1000).coerceIn(1000000, 15000000)

            val format = MediaFormat.createVideoFormat(mimeType, targetWidth, targetHeight).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
                setInteger(MediaFormat.KEY_BIT_RATE, bitrateVal)
                setInteger(MediaFormat.KEY_FRAME_RATE, fpsVal)
                setInteger(MediaFormat.KEY_CAPTURE_RATE, fpsVal)
                setFloat(MediaFormat.KEY_OPERATING_RATE, fpsVal.toFloat())
                setInteger(MediaFormat.KEY_PRIORITY, 0)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1) // 1-second interval guarantees prompt keyframe delivery
                try {
                    setInteger(MediaFormat.KEY_BITRATE_MODE, MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR)
                } catch (_: Exception) {}

                val repeatUs = (1_000_000L / fpsVal).coerceAtLeast(16_666L)
                try {
                    setLong(MediaFormat.KEY_REPEAT_PREVIOUS_FRAME_AFTER, repeatUs)
                } catch (_: Exception) {}

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    try {
                        setInteger(MediaFormat.KEY_PROFILE, MediaCodecInfo.CodecProfileLevel.AVCProfileBaseline)
                    } catch (_: Exception) {}
                }
            }

            var codec: MediaCodec? = null
            try {
                codec = selectHardwareEncoder(mimeType)
                if (codec != null) {
                    codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                }
            } catch (e: Exception) {
                Log.w(TAG_VIDEO, "Hardware encoder configure warning (${e.message}), trying createEncoderByType", e)
                try { codec?.release() } catch (_: Exception) {}
                codec = null
            }

            if (codec == null) {
                codec = MediaCodec.createEncoderByType(mimeType)
                codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            }
            Log.i(TAG_VIDEO, "MediaCodec initialized: ${codec.name} for ${targetWidth}x${targetHeight} (${if (isLandscape) "Landscape" else "Portrait"}) @ $targetFps fps")

            val inputSurface = codec.createInputSurface()
            codec.start()
            videoCodec = codec

            val densityDpi = context.resources.displayMetrics.densityDpi.coerceIn(160, 480)
            Log.i(TAG_PROJECTION, "Creating VirtualDisplay for MediaProjection (size: ${targetWidth}x${targetHeight}, dpi: $densityDpi)")

            // CRITICAL: Must use Main Looper Handler so createVirtualDisplay never fails on background worker threads
            val mainHandler = Handler(Looper.getMainLooper())
            virtualDisplay = projection.createVirtualDisplay(
                "ViperScreenCaptureDisplay",
                targetWidth,
                targetHeight,
                densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                inputSurface,
                object : VirtualDisplay.Callback() {
                    override fun onPaused() {
                        Log.d(TAG_PROJECTION, "VirtualDisplay paused")
                    }
                    override fun onResumed() {
                        Log.d(TAG_PROJECTION, "VirtualDisplay resumed")
                    }
                    override fun onStopped() {
                        Log.d(TAG_PROJECTION, "VirtualDisplay stopped")
                    }
                },
                mainHandler
            )

            isVideoRunning.set(true)
            startVideoDrainLoop(codec)
            requestKeyFrame()
            true
        } catch (e: Exception) {
            Log.e(TAG_VIDEO, "Failed to initialize hardware video encoder", e)
            isVideoRunning.set(false)
            false
        }
    }

    private fun selectHardwareEncoder(mimeType: String): MediaCodec? {
        return try {
            val list = MediaCodecList(MediaCodecList.REGULAR_CODECS)
            for (info in list.codecInfos) {
                if (!info.isEncoder) continue
                if (info.supportedTypes.any { it.equals(mimeType, ignoreCase = true) }) {
                    val caps = try {
                        info.getCapabilitiesForType(mimeType)
                    } catch (_: Exception) {
                        null
                    } ?: continue

                    val supportsSurface = caps.colorFormats.contains(MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
                    if (!supportsSurface) continue

                    val name = info.name.lowercase()
                    val isHw = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        info.isHardwareAccelerated
                    } else {
                        !name.startsWith("omx.google.") && !name.startsWith("c2.android.") && !name.startsWith("omx.ffmpeg.")
                    }
                    val isVendorHw = name.contains("qcom") || name.contains("mtk") || name.contains("exynos") ||
                            name.contains("samsung") || name.contains("hisi") || name.contains("mali") || name.contains("powervr")
                    if (isHw || isVendorHw) {
                        Log.i(TAG_VIDEO, "Selected hardware encoder: ${info.name} (supports Surface)")
                        return MediaCodec.createByCodecName(info.name)
                    }
                }
            }
            null
        } catch (e: Exception) {
            Log.w(TAG_VIDEO, "Hardware codec lookup error: ${e.message}")
            null
        }
    }

    private fun isH264Keyframe(buffer: ByteBuffer): Boolean {
        try {
            val dup = buffer.duplicate()
            val limit = dup.limit()
            var pos = dup.position()
            while (pos < limit - 4) {
                if (dup.get(pos) == 0.toByte() && dup.get(pos + 1) == 0.toByte()) {
                    if (dup.get(pos + 2) == 1.toByte()) {
                        val nalType = dup.get(pos + 3).toInt() and 0x1F
                        if (nalType == 5) return true // IDR Picture
                    } else if (dup.get(pos + 2) == 0.toByte() && dup.get(pos + 3) == 1.toByte() && pos + 4 < limit) {
                        val nalType = dup.get(pos + 4).toInt() and 0x1F
                        if (nalType == 5) return true // IDR Picture
                    }
                }
                pos++
            }
        } catch (_: Exception) {}
        return false
    }

    private fun startVideoDrainLoop(codec: MediaCodec) {
        videoThread = HandlerThread("ViperVideoDrainThread", Thread.MAX_PRIORITY).apply { start() }
        videoHandler = Handler(videoThread!!.looper)
        videoHandler?.post {
            val bufferInfo = MediaCodec.BufferInfo()
            Log.i(TAG_VIDEO, "Video encoder drain loop running.")

            while (isVideoRunning.get()) {
                try {
                    val outputIndex = codec.dequeueOutputBuffer(bufferInfo, 10000L)
                    when {
                        outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            val newFormat = codec.outputFormat
                            currentVideoFormat = newFormat
                            Log.i(TAG_VIDEO, "MediaCodec INFO_OUTPUT_FORMAT_CHANGED: $newFormat")

                            val csd0 = newFormat.getByteBuffer("csd-0")
                            val csd1 = newFormat.getByteBuffer("csd-1")
                            if (csd0 != null && csd1 != null) {
                                currentSps = extractNaluBytes(csd0)
                                currentPps = extractNaluBytes(csd1)
                                Log.i(TAG_VIDEO, "Extracted hardware SPS (${currentSps.size} bytes) and PPS (${currentPps.size} bytes)")
                            }
                            for (listener in videoListeners) {
                                listener.onVideoFormatAvailable(newFormat, currentSps, currentPps)
                            }
                        }
                        outputIndex >= 0 -> {
                            val isEos = (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0
                            val outputBuffer = codec.getOutputBuffer(outputIndex)
                            if (outputBuffer != null && bufferInfo.size > 0) {
                                outputBuffer.position(bufferInfo.offset)
                                outputBuffer.limit(bufferInfo.offset + bufferInfo.size)

                                val isConfig = (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0
                                val isKey = (bufferInfo.flags and MediaCodec.BUFFER_FLAG_KEY_FRAME) != 0 || isH264Keyframe(outputBuffer)

                                if (isConfig) {
                                    parseSpsPpsFromConfigBuffer(outputBuffer, bufferInfo)
                                } else {
                                    for (listener in videoListeners) {
                                        outputBuffer.position(bufferInfo.offset)
                                        outputBuffer.limit(bufferInfo.offset + bufferInfo.size)
                                        listener.onVideoSampleAvailable(
                                            outputBuffer,
                                            bufferInfo,
                                            isKey,
                                            currentSps,
                                            currentPps
                                        )
                                    }
                                }
                            }
                            if (isEos) {
                                Log.i(TAG_VIDEO, "[RECORDER_EOS] Video encoder reached BUFFER_FLAG_END_OF_STREAM.")
                                for (listener in videoListeners) {
                                    listener.onVideoEndOfStream()
                                }
                            }
                            codec.releaseOutputBuffer(outputIndex, false)
                        }
                    }
                } catch (e: Exception) {
                    if (isVideoRunning.get()) {
                        Log.w(TAG_VIDEO, "Exception in video drain loop: ${e.message}")
                    }
                    break
                }
            }
            Log.i(TAG_VIDEO, "Video encoder drain loop ended.")
        }
    }

    private fun extractNaluBytes(buffer: ByteBuffer): ByteArray {
        val dup = buffer.duplicate()
        val bytes = ByteArray(dup.remaining())
        dup.get(bytes)
        if (bytes.size >= 4 && bytes[0] == 0.toByte() && bytes[1] == 0.toByte() && bytes[2] == 0.toByte() && bytes[3] == 1.toByte()) {
            return bytes.copyOfRange(4, bytes.size)
        } else if (bytes.size >= 3 && bytes[0] == 0.toByte() && bytes[1] == 0.toByte() && bytes[2] == 1.toByte()) {
            return bytes.copyOfRange(3, bytes.size)
        }
        return bytes
    }

    private fun ensureAnnexBStartCode(data: ByteArray): ByteBuffer {
        if (data.size >= 4 && data[0] == 0.toByte() && data[1] == 0.toByte() && data[2] == 0.toByte() && data[3] == 1.toByte()) {
            return ByteBuffer.wrap(data)
        }
        val withPrefix = ByteArray(data.size + 4)
        withPrefix[0] = 0.toByte()
        withPrefix[1] = 0.toByte()
        withPrefix[2] = 0.toByte()
        withPrefix[3] = 1.toByte()
        System.arraycopy(data, 0, withPrefix, 4, data.size)
        return ByteBuffer.wrap(withPrefix)
    }

    private fun parseSpsPpsFromConfigBuffer(buffer: ByteBuffer, info: MediaCodec.BufferInfo) {
        try {
            val dup = buffer.duplicate()
            dup.position(info.offset)
            dup.limit(info.offset + info.size)
            val bytes = ByteArray(dup.remaining())
            dup.get(bytes)

            val startCodes = mutableListOf<Int>()
            for (i in 0 until bytes.size - 4) {
                if (bytes[i] == 0.toByte() && bytes[i + 1] == 0.toByte() && bytes[i + 2] == 0.toByte() && bytes[i + 3] == 1.toByte()) {
                    startCodes.add(i)
                }
            }

            if (startCodes.size >= 2) {
                currentSps = bytes.copyOfRange(startCodes[0] + 4, startCodes[1])
                val ppsEnd = if (startCodes.size >= 3) startCodes[2] else bytes.size
                currentPps = bytes.copyOfRange(startCodes[1] + 4, ppsEnd)
                Log.i(TAG_VIDEO, "Parsed SPS (${currentSps.size}b) and PPS (${currentPps.size}b) from CODEC_CONFIG")
                currentVideoFormat?.let { fmt ->
                    fmt.setByteBuffer("csd-0", ensureAnnexBStartCode(currentSps))
                    fmt.setByteBuffer("csd-1", ensureAnnexBStartCode(currentPps))
                    for (listener in videoListeners) {
                        listener.onVideoFormatAvailable(fmt, currentSps, currentPps)
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG_VIDEO, "Config buffer parse warning: ${e.message}")
        }
    }

    /**
     * Initializes AudioRecord (Microphone + AudioPlaybackCapture) and AAC MediaCodec encoder.
     * Uses standard 48,000 Hz stereo, 128 kbps AAC-LC.
     */
    private fun initAudioEncoder(projection: MediaProjection): Boolean {
        return try {
            val minBufMic = AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_STEREO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            val micBufferSize = max(minBufMic * 2, 8192)

            val hasMicPerm = ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED

            if (hasMicPerm) {
                audioRecordMic = createMicAudioRecord(micBufferSize)
                audioRecordMic?.startRecording()
                Log.i(TAG_AUDIO, "Microphone AudioRecord started at $SAMPLE_RATE Hz Stereo.")
            } else {
                Log.w(TAG_AUDIO, "Microphone permission not granted. Running in Internal Only mode.")
            }

            // Internal Game Audio Capture via MediaProjection AudioPlaybackCapture (API 29+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                try {
                    val captureConfig = AudioPlaybackCaptureConfiguration.Builder(projection)
                        .addMatchingUsage(AudioAttributes.USAGE_GAME)
                        .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                        .build()

                    val minBufGame = AudioRecord.getMinBufferSize(
                        SAMPLE_RATE,
                        AudioFormat.CHANNEL_IN_STEREO,
                        AudioFormat.ENCODING_PCM_16BIT
                    )
                    val gameBufferSize = max(minBufGame * 2, 8192)

                    val gameRecord = AudioRecord.Builder()
                        .setAudioPlaybackCaptureConfig(captureConfig)
                        .setAudioFormat(
                            AudioFormat.Builder()
                                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                                .setSampleRate(SAMPLE_RATE)
                                .setChannelMask(AudioFormat.CHANNEL_IN_STEREO)
                                .build()
                        )
                        .setBufferSizeInBytes(gameBufferSize)
                        .build()

                    if (gameRecord.state == AudioRecord.STATE_INITIALIZED) {
                        gameRecord.startRecording()
                        audioRecordGame = gameRecord
                        isPlaybackCaptureBlocked = false
                        audioCaptureError = null
                        Log.i(TAG_AUDIO, "AudioPlaybackCapture internal game audio successfully started at $SAMPLE_RATE Hz Stereo.")
                    } else {
                        isPlaybackCaptureBlocked = true
                        val err = "AudioPlaybackCapture failed to initialize (State: ${gameRecord.state}). Android or game policy may restrict capture."
                        audioCaptureError = err
                        Log.w(TAG_AUDIO, err)
                        gameRecord.release()
                    }
                } catch (e: Exception) {
                    isPlaybackCaptureBlocked = true
                    val err = "Android or BGMI audio playback capture restricted: ${e.message}"
                    audioCaptureError = err
                    Log.e(TAG_AUDIO, err, e)
                }
            } else {
                isPlaybackCaptureBlocked = true
                val err = "Internal audio capture requires Android 10+ (API 29+)."
                audioCaptureError = err
                Log.w(TAG_AUDIO, err)
            }

            // Initialize AAC-LC 48kHz Stereo Encoder (128 kbps)
            val aacMime = MediaFormat.MIMETYPE_AUDIO_AAC
            val aacFormat = MediaFormat.createAudioFormat(aacMime, SAMPLE_RATE, CHANNELS).apply {
                setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                setInteger(MediaFormat.KEY_BIT_RATE, 128000)
                setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384)
            }

            val aacCodec = MediaCodec.createEncoderByType(aacMime)
            aacCodec.configure(aacFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            aacCodec.start()
            audioCodec = aacCodec

            isAudioRunning.set(true)
            startMultiThreadedAudioPipeline(aacCodec)
            true
        } catch (e: Exception) {
            Log.e(TAG_AUDIO, "Failed to initialize AAC audio encoder pipeline", e)
            isAudioRunning.set(false)
            false
        }
    }

    private fun createMicAudioRecord(bufferSize: Int): AudioRecord? {
        val record = try {
            // Try Stereo first
            val stereoRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_STEREO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            )
            if (stereoRecord.state == AudioRecord.STATE_INITIALIZED) {
                stereoRecord
            } else {
                stereoRecord.release()
                // Fallback to Mono if device hardware mic requires mono
                val monoRecord = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufferSize
                )
                if (monoRecord.state == AudioRecord.STATE_INITIALIZED) monoRecord else {
                    monoRecord.release()
                    null
                }
            }
        } catch (e: Exception) {
            Log.w(TAG_AUDIO, "Error creating AudioRecord: ${e.message}")
            null
        }

        record?.let {
            applyMicHardwareEffects(isNoiseSuppressionEnabled, isEchoCancellationEnabled)
        }
        return record
    }

    /**
     * Starts decoupled, multi-threaded audio architecture:
     * 1. Mic Capture Thread (reads independently, pushes to micJitterBuffer)
     * 2. Game Capture Thread (reads independently, pushes to gameJitterBuffer)
     * 3. Audio Mix Pacer Thread (mixes with limiter, generates monotonic timestamps, feeds MediaCodec)
     * 4. Audio Drain Thread (drains AAC from MediaCodec, dispatches to RTMP and MediaMuxer)
     */
    private fun startMultiThreadedAudioPipeline(codec: MediaCodec) {
        micJitterBuffer.clear()
        gameJitterBuffer.clear()

        // 1. Independent Microphone Reader Thread
        micCaptureThread = Thread({
            Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
            val rawBuffer = ShortArray(FRAME_SHORTS)
            val stereoBuffer = ShortArray(FRAME_SHORTS)

            while (isAudioRunning.get()) {
                val record = audioRecordMic
                if (record == null || record.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                    try {
                        Thread.sleep(20)
                    } catch (_: InterruptedException) {
                        break
                    }
                    continue
                }

                try {
                    val read = record.read(rawBuffer, 0, rawBuffer.size)
                    when {
                        read > 0 -> {
                            val isMono = record.channelCount == 1
                            if (isMono) {
                                AudioProcessingUtils.upmixMonoToStereo(rawBuffer, read, stereoBuffer)
                                micJitterBuffer.push(stereoBuffer)
                                liveMicLevel = if (isMicMuted || !isMicAudioEnabled) 0f else AudioProcessingUtils.calculateNormalizedLevel(stereoBuffer, read * 2)
                            } else {
                                if (read < rawBuffer.size) {
                                    val partial = rawBuffer.copyOf(read)
                                    micJitterBuffer.push(partial)
                                } else {
                                    micJitterBuffer.push(rawBuffer)
                                }
                                liveMicLevel = if (isMicMuted || !isMicAudioEnabled) 0f else AudioProcessingUtils.calculateNormalizedLevel(rawBuffer, read)
                            }
                        }
                        read == AudioRecord.ERROR_INVALID_OPERATION || read == AudioRecord.ERROR_DEAD_OBJECT -> {
                            Log.w(TAG_AUDIO, "AudioRecord mic error ($read). Attempting smooth recovery...")
                            audioAutoRecoveries.incrementAndGet()
                            try {
                                record.stop()
                                record.release()
                            } catch (_: Exception) {}
                            Thread.sleep(100)
                            audioRecordMic = createMicAudioRecord(max(AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_STEREO, AudioFormat.ENCODING_PCM_16BIT) * 2, 8192))
                            audioRecordMic?.startRecording()
                        }
                        else -> {
                            Thread.sleep(5)
                        }
                    }
                } catch (e: Exception) {
                    if (isAudioRunning.get()) {
                        Log.w(TAG_AUDIO, "Mic capture thread exception: ${e.message}")
                    }
                    break
                }
            }
            Log.i(TAG_AUDIO, "MicCaptureThread terminated.")
        }, "ViperMicCaptureThread").apply { start() }

        // 2. Independent Game Audio PlaybackCapture Reader Thread
        gameCaptureThread = Thread({
            Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
            val rawBuffer = ShortArray(FRAME_SHORTS)

            while (isAudioRunning.get()) {
                val record = audioRecordGame
                if (record == null || record.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                    try {
                        Thread.sleep(20)
                    } catch (_: InterruptedException) {
                        break
                    }
                    continue
                }

                try {
                    val read = record.read(rawBuffer, 0, rawBuffer.size)
                    when {
                        read > 0 -> {
                            if (read < rawBuffer.size) {
                                val partial = rawBuffer.copyOf(read)
                                gameJitterBuffer.push(partial)
                            } else {
                                gameJitterBuffer.push(rawBuffer)
                            }
                            liveGameLevel = if (!isInternalAudioEnabled) 0f else AudioProcessingUtils.calculateNormalizedLevel(rawBuffer, read)
                        }
                        read == AudioRecord.ERROR_INVALID_OPERATION || read == AudioRecord.ERROR_DEAD_OBJECT -> {
                            Log.w(TAG_AUDIO, "Game AudioPlaybackCapture error: $read. Attempting recovery...")
                            audioAutoRecoveries.incrementAndGet()
                            Thread.sleep(50)
                        }
                        read < 0 -> {
                            Log.w(TAG_AUDIO, "Game AudioPlaybackCapture read error: $read")
                            Thread.sleep(20)
                        }
                        else -> {
                            Thread.sleep(5)
                        }
                    }
                } catch (e: Exception) {
                    if (isAudioRunning.get()) {
                        Log.w(TAG_AUDIO, "Game audio capture thread exception: ${e.message}")
                    }
                    break
                }
            }
            Log.i(TAG_AUDIO, "GameCaptureThread terminated.")
        }, "ViperGameCaptureThread").apply { start() }

        // 3. Audio Mixing & Frame Pacing Thread
        audioMixPacerThread = Thread({
            Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
            val micPcm = ShortArray(FRAME_SHORTS)
            val gamePcm = ShortArray(FRAME_SHORTS)
            val mixedPcm = ShortArray(FRAME_SHORTS)
            val frameBytes = AudioProcessingUtils.FRAME_SIZE_BYTES

            val frameDurationNs = (AudioProcessingUtils.FRAME_SIZE_STEREO_SAMPLES.toLong() * 1_000_000_000L) / SAMPLE_RATE
            var nextFrameTimeNs = System.nanoTime()
            var diagLogTimer = System.currentTimeMillis()

            Log.i(TAG_AUDIO, "AudioMixPacerThread started. Frame duration: ${frameDurationNs / 1_000_000.0} ms")

            while (isAudioRunning.get()) {
                try {
                    // Frame pacing: align with steady 21.33ms hardware interval
                    val nowNs = System.nanoTime()
                    val sleepNs = nextFrameTimeNs - nowNs
                    if (sleepNs > 1_000_000L) {
                        Thread.sleep(sleepNs / 1_000_000L)
                    }
                    nextFrameTimeNs += frameDurationNs

                    // Pull frames from decoupled jitter buffers
                    micJitterBuffer.poll(micPcm)
                    gameJitterBuffer.poll(gamePcm)

                    // Apply microphone software noise suppression if enabled (internal game audio unaffected)
                    if (isNoiseSuppressionEnabled && isMicAudioEnabled && !isMicMuted) {
                        AudioProcessingUtils.suppressMicNoise(micPcm, micPcm.size)
                    }

                    // Software mixing with soft-clipping limiter and independent toggles
                    AudioProcessingUtils.mixStereoPcm(
                        micPcm = micPcm,
                        micVolume = micVolume,
                        isMicMuted = isMicMuted,
                        isMicEnabled = isMicAudioEnabled,
                        gamePcm = gamePcm,
                        gameVolume = gameVolume,
                        isInternalAudioEnabled = isInternalAudioEnabled,
                        outputPcm = mixedPcm,
                        mode = captureMode
                    )

                    // Strictly Monotonic Presentation Timestamp (PTS) synchronized with Video time base
                    val baseUs = presentationTimeBaseUs.get()
                    val framesWritten = totalAudioFramesWritten.get()
                    val ptsUs = baseUs + ((framesWritten * 1_000_000L) / SAMPLE_RATE) + (audioDelayMs * 1_000L)

                    // Feed PCM into audio MediaCodec
                    val inIndex = codec.dequeueInputBuffer(8000L)
                    if (inIndex >= 0) {
                        val inBuffer = codec.getInputBuffer(inIndex)
                        if (inBuffer != null) {
                            inBuffer.clear()
                            val shortBuffer = inBuffer.asShortBuffer()
                            shortBuffer.put(mixedPcm)
                            inBuffer.position(0)
                            inBuffer.limit(frameBytes)

                            codec.queueInputBuffer(inIndex, 0, frameBytes, ptsUs, 0)
                            totalAudioFramesWritten.addAndGet(AudioProcessingUtils.FRAME_SIZE_STEREO_SAMPLES.toLong())
                        }
                    } else {
                        audioEncoderQueueErrors.incrementAndGet()
                    }

                    // Periodic Diagnostic Logging (every 5 seconds)
                    if (System.currentTimeMillis() - diagLogTimer >= 5000L) {
                        diagLogTimer = System.currentTimeMillis()
                        val driftMs = ((System.nanoTime() / 1000L - ptsUs) / 1000L)
                        Log.i(
                            TAG_DIAGNOSTICS,
                            "Audio Status: Mode=${captureMode.name}, InternalOn=$isInternalAudioEnabled, MicOn=$isMicAudioEnabled, Pts=$ptsUs us, Drift=${driftMs}ms, " +
                                "MicUnderruns=${micJitterBuffer.underrunsCount.get()}, MicOverruns=${micJitterBuffer.overrunsCount.get()}, " +
                                "GameUnderruns=${gameJitterBuffer.underrunsCount.get()}, GameOverruns=${gameJitterBuffer.overrunsCount.get()}, " +
                                "QueueErrors=${audioEncoderQueueErrors.get()}, Recoveries=${audioAutoRecoveries.get()}, Dropped=${audioDroppedBuffers.get()}"
                        )
                    }
                } catch (e: Exception) {
                    if (isAudioRunning.get()) {
                        Log.w(TAG_AUDIO, "Audio mixer pacer thread exception: ${e.message}")
                    }
                    break
                }
            }
            Log.i(TAG_AUDIO, "AudioMixPacerThread terminated.")
        }, "ViperAudioMixPacerThread").apply { start() }

        // 4. AAC MediaCodec Drain Thread
        audioDrainThread = Thread({
            val bufferInfo = MediaCodec.BufferInfo()
            while (isAudioRunning.get()) {
                try {
                    val outIndex = codec.dequeueOutputBuffer(bufferInfo, 10000L)
                    when {
                        outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            val newFormat = codec.outputFormat
                            currentAudioFormat = newFormat
                            Log.i(TAG_AUDIO, "Audio MediaCodec output format changed: $newFormat")

                            val csd = newFormat.getByteBuffer("csd-0")
                            if (csd != null) {
                                val asc = ByteArray(csd.remaining())
                                csd.duplicate().get(asc)
                                currentAudioSpecificConfig = asc
                                Log.i(TAG_AUDIO, "Extracted AAC AudioSpecificConfig (${asc.size} bytes: ${asc.joinToString { "0x%02X".format(it) }})")
                            }

                            for (listener in audioListeners) {
                                listener.onAudioFormatAvailable(newFormat, currentAudioSpecificConfig)
                            }
                        }
                        outIndex >= 0 -> {
                            val isEos = (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0
                            val isConfig = (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0
                            val outBuf = codec.getOutputBuffer(outIndex)
                            if (outBuf != null && !isConfig && bufferInfo.size > 0) {
                                outBuf.position(bufferInfo.offset)
                                outBuf.limit(bufferInfo.offset + bufferInfo.size)

                                for (listener in audioListeners) {
                                    outBuf.position(bufferInfo.offset)
                                    outBuf.limit(bufferInfo.offset + bufferInfo.size)
                                    listener.onAudioSampleAvailable(outBuf, bufferInfo)
                                }
                            }
                            if (isEos) {
                                Log.i(TAG_AUDIO, "[RECORDER_EOS] Audio encoder reached BUFFER_FLAG_END_OF_STREAM.")
                                for (listener in audioListeners) {
                                    listener.onAudioEndOfStream()
                                }
                            }
                            codec.releaseOutputBuffer(outIndex, false)
                        }
                    }
                } catch (e: Exception) {
                    if (isAudioRunning.get()) {
                        Log.w(TAG_AUDIO, "Audio drain exception: ${e.message}")
                    }
                    break
                }
            }
            Log.i(TAG_AUDIO, "AudioDrainThread terminated.")
        }, "ViperAudioDrainThread").apply { start() }
    }

    /**
     * Requests an immediate IDR keyframe (sync frame) from the hardware video encoder.
     * Essential for screen recording to ensure the MP4 video track begins with a valid keyframe.
     */
    fun requestKeyFrame() {
        try {
            val params = Bundle().apply {
                putInt(MediaCodec.PARAMETER_KEY_REQUEST_SYNC_FRAME, 0)
            }
            videoCodec?.setParameters(params)
            Log.i(TAG_VIDEO, "Successfully requested immediate IDR keyframe from hardware video encoder.")
        } catch (e: Exception) {
            Log.w(TAG_VIDEO, "requestKeyFrame warning: ${e.message}")
        }
    }

    /**
     * Signals End-of-Stream to the video encoder without immediately tearing down
     * the pipeline, allowing remaining frames to be drained by active listeners.
     */
    fun signalVideoEndOfInputStream() {
        try {
            videoCodec?.signalEndOfInputStream()
            Log.i(TAG_VIDEO, "Signaled EndOfInputStream to video MediaCodec.")
        } catch (e: Exception) {
            Log.w(TAG_VIDEO, "signalEndOfInputStream warning: ${e.message}")
        }
    }

    /**
     * Signals End-of-Stream to the audio encoder by queueing an empty buffer with
     * BUFFER_FLAG_END_OF_STREAM, ensuring remaining audio buffers are drained.
     */
    fun signalAudioEndOfInputStream() {
        try {
            val codec = audioCodec ?: return
            val inIndex = codec.dequeueInputBuffer(10000L)
            if (inIndex >= 0) {
                val baseUs = presentationTimeBaseUs.get()
                val framesWritten = totalAudioFramesWritten.get()
                val ptsUs = baseUs + ((framesWritten * 1_000_000L) / SAMPLE_RATE)
                codec.queueInputBuffer(
                    inIndex,
                    0,
                    0,
                    ptsUs,
                    MediaCodec.BUFFER_FLAG_END_OF_STREAM
                )
                Log.i(TAG_AUDIO, "Queued BUFFER_FLAG_END_OF_STREAM to audio MediaCodec.")
            }
        } catch (e: Exception) {
            Log.w(TAG_AUDIO, "signalAudioEndOfInputStream warning: ${e.message}")
        }
    }

    /**
     * Stops the encoder pipeline, virtual display, and releases all resources safely.
     */
    @Synchronized
    fun stopCapture() {
        if (!isRunning.get()) return
        Log.i(TAG_VIDEO, "Stopping ScreenCaptureEncoder pipeline.")

        isRunning.set(false)
        isVideoRunning.set(false)
        isAudioRunning.set(false)

        routingManager.stopListening()

        // 1. Release VirtualDisplay
        try {
            virtualDisplay?.release()
            Log.i(TAG_PROJECTION, "VirtualDisplay successfully released.")
        } catch (e: Exception) {
            Log.w(TAG_PROJECTION, "VirtualDisplay release warning: ${e.message}")
        }
        virtualDisplay = null

        // 2. Stop and release video MediaCodec
        try {
            videoCodec?.signalEndOfInputStream()
        } catch (_: Exception) {}

        videoThread?.quitSafely()
        videoThread = null
        videoHandler = null

        try {
            videoCodec?.stop()
            videoCodec?.release()
            Log.i(TAG_VIDEO, "Video MediaCodec stopped and released.")
        } catch (e: Exception) {
            Log.w(TAG_VIDEO, "Video MediaCodec release warning: ${e.message}")
        }
        videoCodec = null

        // 3. Stop and release audio AudioRecords and MediaCodec
        releaseMicHardwareEffects()
        try {
            audioRecordMic?.stop()
            audioRecordMic?.release()
        } catch (_: Exception) {}
        audioRecordMic = null

        try {
            audioRecordGame?.stop()
            audioRecordGame?.release()
        } catch (_: Exception) {}
        audioRecordGame = null

        try {
            audioCodec?.stop()
            audioCodec?.release()
            Log.i(TAG_AUDIO, "Audio AAC MediaCodec stopped and released.")
        } catch (e: Exception) {
            Log.w(TAG_AUDIO, "Audio codec release warning: ${e.message}")
        }
        audioCodec = null

        micJitterBuffer.clear()
        gameJitterBuffer.clear()

        activeProjection = null
        currentVideoFormat = null
        currentAudioFormat = null
        currentSps = ByteArray(0)
        currentPps = ByteArray(0)
        currentAudioSpecificConfig = ByteArray(0)
        liveMicLevel = 0f
        liveGameLevel = 0f
        Log.i(TAG_VIDEO, "ScreenCaptureEncoder fully stopped and clean.")
    }
}
