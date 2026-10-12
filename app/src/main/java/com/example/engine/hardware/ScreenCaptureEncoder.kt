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
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.data.model.ResolutionOption
import java.io.ByteArrayOutputStream
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
}

interface AudioFrameListener {
    fun onAudioFormatAvailable(format: MediaFormat, audioSpecificConfig: ByteArray)
    fun onAudioSampleAvailable(byteBuffer: ByteBuffer, bufferInfo: MediaCodec.BufferInfo)
}

/**
 * Production-grade hardware screen capture and low-latency encoding engine.
 * Specifically tuned for Qualcomm Snapdragon / Motorola Moto G96 5G running Android 14-16.
 */
class ScreenCaptureEncoder(private val context: Context) {

    companion object {
        const val TAG_PROJECTION = "ViperMediaProjection"
        const val TAG_VIDEO = "ViperVideoEncoder"
        const val TAG_AUDIO = "ViperAudioEngine"
        const val TAG_RTMP = "ViperRtmpEngine"
        const val TAG_MUXER = "ViperMuxer"

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

    // Audio Capture & Encoder
    private var audioRecordMic: AudioRecord? = null
    private var audioRecordGame: AudioRecord? = null
    private var audioCodec: MediaCodec? = null
    private var audioCaptureThread: Thread? = null
    private var audioDrainThread: Thread? = null
    private var currentAudioFormat: MediaFormat? = null
    private var currentAudioSpecificConfig: ByteArray = byteArrayOf(0x12, 0x10) // AAC-LC 44.1kHz Stereo

    // Volumes and Configuration
    @Volatile var micVolume: Float = 1.0f
    @Volatile var gameVolume: Float = 1.0f
    @Volatile var isMicMuted: Boolean = false

    private val presentationTimeBaseUs = AtomicLong(0)

    fun registerVideoListener(listener: VideoFrameListener) {
        if (!videoListeners.contains(listener)) {
            videoListeners.add(listener)
            // Immediately dispatch cached format if already running
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
        bitrateKbps: Int
    ): Boolean {
        if (isRunning.get()) {
            Log.i(TAG_VIDEO, "ScreenCaptureEncoder is already active. Attaching new listener without re-creating VirtualDisplay.")
            return true
        }

        activeProjection = projection
        isRunning.set(true)
        presentationTimeBaseUs.set(System.nanoTime() / 1000L)

        val successVideo = initVideoEncoder(projection, resolution, targetFps, bitrateKbps)
        val successAudio = initAudioEncoder(projection)

        Log.i(
            TAG_VIDEO,
            "ScreenCaptureEncoder pipeline initialized. Video: $successVideo, Audio: $successAudio (${resolution.label}, $targetFps fps, $bitrateKbps kbps)"
        )
        return successVideo
    }

    @SuppressLint("WrongConstant")
    private fun initVideoEncoder(
        projection: MediaProjection,
        resolution: ResolutionOption,
        targetFps: Int,
        bitrateKbps: Int
    ): Boolean {
        return try {
            val mimeType = MediaFormat.MIMETYPE_VIDEO_AVC

            // 16-pixel alignment for Motorola Snapdragon hardware encoder compatibility
            val alignedWidth = ((resolution.width + 15) / 16) * 16
            val alignedHeight = ((resolution.height + 15) / 16) * 16

            val format = MediaFormat.createVideoFormat(mimeType, alignedWidth, alignedHeight).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
                setInteger(MediaFormat.KEY_BIT_RATE, (bitrateKbps * 1000).coerceIn(1000000, 15000000))
                setInteger(MediaFormat.KEY_FRAME_RATE, targetFps.coerceIn(24, 60))
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 2) // 2-second keyframe interval required by YouTube
                setInteger(MediaFormat.KEY_BITRATE_MODE, MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR)

                // High priority on Android: repeat previous frame after 33ms so static menus never stall YouTube live ingest
                val repeatUs = (1_000_000L / targetFps).coerceAtLeast(16_666L)
                setLong(MediaFormat.KEY_REPEAT_PREVIOUS_FRAME_AFTER, repeatUs)

                // Profile and Level for H.264
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    setInteger(MediaFormat.KEY_PROFILE, MediaCodecInfo.CodecProfileLevel.AVCProfileBaseline)
                    setInteger(MediaFormat.KEY_LEVEL, MediaCodecInfo.CodecProfileLevel.AVCLevel4)
                }
            }

            // Codec selection: prefer Qualcomm / hardware encoder if present on Moto G96 5G
            val codec = selectHardwareEncoder(mimeType) ?: MediaCodec.createEncoderByType(mimeType)
            Log.i(TAG_VIDEO, "MediaCodec instantiated: ${codec.name} for ${alignedWidth}x${alignedHeight} @ $targetFps fps")

            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            val inputSurface = codec.createInputSurface()
            codec.start()
            videoCodec = codec

            val densityDpi = context.resources.displayMetrics.densityDpi
            Log.i(TAG_PROJECTION, "Creating VirtualDisplay for MediaProjection (size: ${alignedWidth}x${alignedHeight}, dpi: $densityDpi)")

            virtualDisplay = projection.createVirtualDisplay(
                "ViperScreenCaptureDisplay",
                alignedWidth,
                alignedHeight,
                densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                inputSurface,
                null,
                null
            )

            isVideoRunning.set(true)
            startVideoDrainLoop(codec)
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
                    val name = info.name.lowercase()
                    // Target Moto G96 5G Qualcomm encoder names
                    if (name.contains("qcom") || name.contains("c2.qcom") || name.contains("omx.qcom")) {
                        Log.i(TAG_VIDEO, "Selected Qualcomm hardware encoder: ${info.name}")
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
                                for (listener in videoListeners) {
                                    listener.onVideoFormatAvailable(newFormat, currentSps, currentPps)
                                }
                            }
                        }
                        outputIndex >= 0 -> {
                            val outputBuffer = codec.getOutputBuffer(outputIndex)
                            if (outputBuffer != null && bufferInfo.size > 0) {
                                outputBuffer.position(bufferInfo.offset)
                                outputBuffer.limit(bufferInfo.offset + bufferInfo.size)

                                val isConfig = (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0
                                val isKey = (bufferInfo.flags and MediaCodec.BUFFER_FLAG_KEY_FRAME) != 0

                                if (isConfig) {
                                    // Parse SPS/PPS from config buffer if not yet discovered
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
        // Strip 00 00 00 01 Annex-B start code if present
        if (bytes.size >= 4 && bytes[0] == 0.toByte() && bytes[1] == 0.toByte() && bytes[2] == 0.toByte() && bytes[3] == 1.toByte()) {
            return bytes.copyOfRange(4, bytes.size)
        } else if (bytes.size >= 3 && bytes[0] == 0.toByte() && bytes[1] == 0.toByte() && bytes[2] == 1.toByte()) {
            return bytes.copyOfRange(3, bytes.size)
        }
        return bytes
    }

    private fun parseSpsPpsFromConfigBuffer(buffer: ByteBuffer, info: MediaCodec.BufferInfo) {
        try {
            val dup = buffer.duplicate()
            dup.position(info.offset)
            dup.limit(info.offset + info.size)
            val bytes = ByteArray(dup.remaining())
            dup.get(bytes)

            // Locate 00 00 00 01 markers
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
            }
        } catch (e: Exception) {
            Log.w(TAG_VIDEO, "Config buffer parse warning: ${e.message}")
        }
    }

    /**
     * Initializes AudioRecord (Microphone + AudioPlaybackCapture for internal game audio)
     * and hardware AAC MediaCodec encoder.
     */
    private fun initAudioEncoder(projection: MediaProjection): Boolean {
        return try {
            val sampleRate = 44100
            val channelConfig = AudioFormat.CHANNEL_IN_STEREO
            val audioFormat = AudioFormat.ENCODING_PCM_16BIT
            val minBuf = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
            val bufferSize = max(minBuf * 2, 8192)

            val hasMicPerm = ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED

            if (hasMicPerm) {
                try {
                    val micRecord = AudioRecord(
                        MediaRecorder.AudioSource.MIC,
                        sampleRate,
                        channelConfig,
                        audioFormat,
                        bufferSize
                    )
                    if (micRecord.state == AudioRecord.STATE_INITIALIZED) {
                        micRecord.startRecording()
                        audioRecordMic = micRecord
                        Log.i(TAG_AUDIO, "Microphone AudioRecord initialized and recording.")
                    }
                } catch (e: Exception) {
                    Log.w(TAG_AUDIO, "Microphone AudioRecord initialization failed: ${e.message}")
                }
            }

            // AudioPlaybackCapture for Android 10+ (Q+) internal game sound
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && hasMicPerm) {
                try {
                    val captureConfig = AudioPlaybackCaptureConfiguration.Builder(projection)
                        .addMatchingUsage(AudioAttributes.USAGE_GAME)
                        .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                        .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
                        .build()

                    val gameRecord = AudioRecord.Builder()
                        .setAudioPlaybackCaptureConfig(captureConfig)
                        .setAudioFormat(
                            AudioFormat.Builder()
                                .setEncoding(audioFormat)
                                .setSampleRate(sampleRate)
                                .setChannelMask(channelConfig)
                                .build()
                        )
                        .setBufferSizeInBytes(bufferSize)
                        .build()

                    if (gameRecord.state == AudioRecord.STATE_INITIALIZED) {
                        gameRecord.startRecording()
                        audioRecordGame = gameRecord
                        Log.i(TAG_AUDIO, "AudioPlaybackCapture internal game audio successfully started.")
                    } else {
                        Log.w(TAG_AUDIO, "AudioPlaybackCapture state != INITIALIZED")
                        gameRecord.release()
                    }
                } catch (e: Exception) {
                    Log.i(TAG_AUDIO, "AudioPlaybackCapture not supported or restricted by system: ${e.message}. Using microphone audio.")
                }
            }

            // Initialize AAC MediaCodec encoder
            val aacMime = MediaFormat.MIMETYPE_AUDIO_AAC
            val aacFormat = MediaFormat.createAudioFormat(aacMime, sampleRate, 2).apply {
                setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                setInteger(MediaFormat.KEY_BIT_RATE, 128000)
                setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384)
            }

            val aacCodec = MediaCodec.createEncoderByType(aacMime)
            aacCodec.configure(aacFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            aacCodec.start()
            audioCodec = aacCodec

            isAudioRunning.set(true)
            startAudioPump(sampleRate, bufferSize)
            true
        } catch (e: Exception) {
            Log.e(TAG_AUDIO, "Failed to initialize AAC audio encoder", e)
            isAudioRunning.set(false)
            false
        }
    }

    private fun startAudioPump(sampleRate: Int, bufferSize: Int) {
        val codec = audioCodec ?: return

        // 1. Audio Capture Thread (Reads PCM, mixes mic + internal game audio, feeds into MediaCodec)
        audioCaptureThread = Thread({
            val micBuffer = ShortArray(1024)
            val gameBuffer = ShortArray(1024)
            val mixedBuffer = ShortArray(1024)
            var audioPtsUs = 0L

            while (isAudioRunning.get()) {
                try {
                    val micRead = if (audioRecordMic != null && !isMicMuted) {
                        audioRecordMic?.read(micBuffer, 0, micBuffer.size) ?: 0
                    } else 0

                    val gameRead = if (audioRecordGame != null) {
                        audioRecordGame?.read(gameBuffer, 0, gameBuffer.size) ?: 0
                    } else 0

                    val framesRead = max(micRead, gameRead)
                    if (framesRead > 0) {
                        // Software mix with independent volume scaling
                        for (i in 0 until framesRead) {
                            val micSample = if (i < micRead && !isMicMuted) (micBuffer[i] * micVolume) else 0f
                            val gameSample = if (i < gameRead) (gameBuffer[i] * gameVolume) else 0f
                            val mixed = (micSample + gameSample).coerceIn(-32768f, 32767f).toInt().toShort()
                            mixedBuffer[i] = mixed
                        }

                        // Feed PCM into audio MediaCodec
                        val inputIndex = codec.dequeueInputBuffer(5000L)
                        if (inputIndex >= 0) {
                            val inputBuf = codec.getInputBuffer(inputIndex)
                            if (inputBuf != null) {
                                inputBuf.clear()
                                val byteSize = framesRead * 2
                                val byteBuf = ByteBuffer.allocate(byteSize)
                                for (i in 0 until framesRead) {
                                    byteBuf.putShort(mixedBuffer[i])
                                }
                                byteBuf.flip()
                                inputBuf.put(byteBuf)

                                codec.queueInputBuffer(
                                    inputIndex,
                                    0,
                                    byteSize,
                                    audioPtsUs,
                                    0
                                )
                                // Advances PTS based on sample rate (44100 Hz stereo: 2 samples per frame)
                                audioPtsUs += ((framesRead / 2L) * 1_000_000L) / sampleRate
                            }
                        }
                    } else {
                        Thread.sleep(10)
                    }
                } catch (e: Exception) {
                    if (isAudioRunning.get()) {
                        Log.w(TAG_AUDIO, "Audio capture loop warning: ${e.message}")
                    }
                    break
                }
            }
        }, "ViperAudioCaptureThread").apply { start() }

        // 2. Audio Drain Thread (Retrieves encoded AAC buffers and dispatches to listeners)
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
                                Log.i(TAG_AUDIO, "Extracted AAC AudioSpecificConfig (${asc.size} bytes)")
                            }

                            for (listener in audioListeners) {
                                listener.onAudioFormatAvailable(newFormat, currentAudioSpecificConfig)
                            }
                        }
                        outIndex >= 0 -> {
                            val outBuf = codec.getOutputBuffer(outIndex)
                            if (outBuf != null && bufferInfo.size > 0) {
                                outBuf.position(bufferInfo.offset)
                                outBuf.limit(bufferInfo.offset + bufferInfo.size)

                                for (listener in audioListeners) {
                                    outBuf.position(bufferInfo.offset)
                                    outBuf.limit(bufferInfo.offset + bufferInfo.size)
                                    listener.onAudioSampleAvailable(outBuf, bufferInfo)
                                }
                            }
                            codec.releaseOutputBuffer(outIndex, false)
                        }
                    }
                } catch (e: Exception) {
                    if (isAudioRunning.get()) {
                        Log.w(TAG_AUDIO, "Audio drain warning: ${e.message}")
                    }
                    break
                }
            }
        }, "ViperAudioDrainThread").apply { start() }
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

        // 3. Stop and release audio capture & AAC encoder
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

        activeProjection = null
        currentVideoFormat = null
        currentAudioFormat = null
        Log.i(TAG_VIDEO, "ScreenCaptureEncoder fully stopped and clean.")
    }
}
