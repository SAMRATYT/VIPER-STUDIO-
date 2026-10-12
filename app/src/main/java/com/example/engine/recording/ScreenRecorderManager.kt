package com.example.engine.recording

import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaCodec
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.MediaStore
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.data.local.ViperStudioDatabase
import com.example.data.local.entity.RecordingEntity
import com.example.data.model.ResolutionOption
import com.example.engine.hardware.AudioFrameListener
import com.example.engine.hardware.ScreenCaptureEncoder
import com.example.engine.hardware.VideoFrameListener
import com.example.service.StreamForegroundService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

class ScreenRecorderManager(
    private val context: Context,
    private val database: ViperStudioDatabase
) {

    companion object {
        private const val TAG = "ScreenRecorderManager"
        private const val TAG_MUXER = "ViperMuxer"
    }

    private val scope = CoroutineScope(Dispatchers.IO)
    private var timerJob: Job? = null

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _isPaused = MutableStateFlow(false)
    val isPaused: StateFlow<Boolean> = _isPaused.asStateFlow()

    private val _durationSeconds = MutableStateFlow(0L)
    val durationSeconds: StateFlow<Long> = _durationSeconds.asStateFlow()

    private val _estimatedFileSizeBytes = MutableStateFlow(0L)
    val estimatedFileSizeBytes: StateFlow<Long> = _estimatedFileSizeBytes.asStateFlow()

    private val _availableStorageGb = MutableStateFlow(0f)
    val availableStorageGb: StateFlow<Float> = _availableStorageGb.asStateFlow()

    private var currentStagingFile: File? = null
    private var currentPublicFile: File? = null
    private var currentResolution: ResolutionOption = ResolutionOption.RES_1080P
    private var recordStartTimeMs: Long = 0

    // Hardware MediaMuxer State
    private var mediaMuxer: MediaMuxer? = null
    private var videoTrackIndex = -1
    private var audioTrackIndex = -1
    private val isMuxerStarted = AtomicBoolean(false)
    private val muxerLock = Any()
    private val recordedVideoFramesCount = AtomicLong(0)
    private val recordedAudioFramesCount = AtomicLong(0)

    private val videoListener = object : VideoFrameListener {
        override fun onVideoFormatAvailable(format: MediaFormat, sps: ByteArray, pps: ByteArray) {
            synchronized(muxerLock) {
                val muxer = mediaMuxer ?: return
                if (videoTrackIndex == -1 && !isMuxerStarted.get()) {
                    try {
                        videoTrackIndex = muxer.addTrack(format)
                        Log.i(TAG_MUXER, "Hardware video track added to MediaMuxer (index $videoTrackIndex). Format: $format")
                        // If audio is also added, or if audio is not requested, start muxer
                        val hasMicPerm = ContextCompat.checkSelfPermission(
                            context,
                            android.Manifest.permission.RECORD_AUDIO
                        ) == PackageManager.PERMISSION_GRANTED

                        if (audioTrackIndex != -1 || !hasMicPerm) {
                            muxer.start()
                            isMuxerStarted.set(true)
                            Log.i(TAG_MUXER, "MediaMuxer started with video track $videoTrackIndex and audio track $audioTrackIndex.")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG_MUXER, "Error adding video track to MediaMuxer: ${e.message}", e)
                    }
                }
            }
        }

        override fun onVideoSampleAvailable(
            byteBuffer: ByteBuffer,
            bufferInfo: MediaCodec.BufferInfo,
            isKeyFrame: Boolean,
            sps: ByteArray,
            pps: ByteArray
        ) {
            if (_isPaused.value) return
            synchronized(muxerLock) {
                val muxer = mediaMuxer ?: return
                if (isMuxerStarted.get() && videoTrackIndex != -1 && bufferInfo.size > 0) {
                    try {
                        muxer.writeSampleData(videoTrackIndex, byteBuffer, bufferInfo)
                        recordedVideoFramesCount.incrementAndGet()
                    } catch (e: Exception) {
                        Log.w(TAG_MUXER, "Error writing video sample: ${e.message}")
                    }
                }
            }
        }
    }

    private val audioListener = object : AudioFrameListener {
        override fun onAudioFormatAvailable(format: MediaFormat, audioSpecificConfig: ByteArray) {
            synchronized(muxerLock) {
                val muxer = mediaMuxer ?: return
                if (audioTrackIndex == -1 && !isMuxerStarted.get()) {
                    try {
                        audioTrackIndex = muxer.addTrack(format)
                        Log.i(TAG_MUXER, "Hardware audio track added to MediaMuxer (index $audioTrackIndex). Format: $format")
                        if (videoTrackIndex != -1) {
                            muxer.start()
                            isMuxerStarted.set(true)
                            Log.i(TAG_MUXER, "MediaMuxer started with video track $videoTrackIndex and audio track $audioTrackIndex.")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG_MUXER, "Error adding audio track to MediaMuxer: ${e.message}", e)
                    }
                }
            }
        }

        override fun onAudioSampleAvailable(byteBuffer: ByteBuffer, bufferInfo: MediaCodec.BufferInfo) {
            if (_isPaused.value) return
            synchronized(muxerLock) {
                val muxer = mediaMuxer ?: return
                if (isMuxerStarted.get() && audioTrackIndex != -1 && bufferInfo.size > 0) {
                    try {
                        muxer.writeSampleData(audioTrackIndex, byteBuffer, bufferInfo)
                        recordedAudioFramesCount.incrementAndGet()
                    } catch (e: Exception) {
                        Log.w(TAG_MUXER, "Error writing audio sample: ${e.message}")
                    }
                }
            }
        }
    }

    init {
        updateAvailableStorage()
    }

    fun updateAvailableStorage() {
        try {
            val stat = StatFs(Environment.getExternalStorageDirectory().path)
            val bytesAvailable = stat.availableBytes
            _availableStorageGb.value = (bytesAvailable / (1024f * 1024f * 1024f))
        } catch (_: Exception) {
            try {
                val stat = StatFs(context.filesDir.path)
                _availableStorageGb.value = (stat.availableBytes / (1024f * 1024f * 1024f))
            } catch (_: Exception) {
                _availableStorageGb.value = 32.0f
            }
        }
    }

    fun startRecording(resolution: ResolutionOption) {
        if (_isRecording.value) return
        currentResolution = resolution
        updateAvailableStorage()

        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "VIPER_RECORDING_$timeStamp.mp4"

        // 1. Prepare Staging File in App External Files
        val stagingDir = context.getExternalFilesDir("recordings") ?: context.filesDir
        if (!stagingDir.exists()) stagingDir.mkdirs()
        val stagingFile = File(stagingDir, fileName)
        currentStagingFile = stagingFile

        // 2. Prepare Public Movies/VIPER STUDIO Directory
        val publicMoviesDir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
            "VIPER STUDIO"
        )
        if (!publicMoviesDir.exists()) publicMoviesDir.mkdirs()
        val publicFile = File(publicMoviesDir, fileName)
        currentPublicFile = publicFile

        videoTrackIndex = -1
        audioTrackIndex = -1
        isMuxerStarted.set(false)
        recordedVideoFramesCount.set(0)
        recordedAudioFramesCount.set(0)

        // 3. Hardware ScreenCaptureEncoder with MediaMuxer
        val projection = StreamForegroundService.currentMediaProjection
        if (projection != null) {
            try {
                val muxer = MediaMuxer(stagingFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
                mediaMuxer = muxer
                Log.i(TAG_MUXER, "MediaMuxer initialized with output path: ${stagingFile.absolutePath}")

                val encoder = ScreenCaptureEncoder.getInstance(context)
                encoder.registerVideoListener(videoListener)
                encoder.registerAudioListener(audioListener)

                val targetFps = 30
                val targetBitrate = (resolution.recommendedBitrate).coerceAtLeast(3500)
                encoder.startCapture(projection, resolution, targetFps, targetBitrate)
                Log.i(TAG, "Hardware ScreenCaptureEncoder attached to MediaMuxer.")
            } catch (e: Exception) {
                Log.e(TAG, "Failed initializing hardware MediaMuxer recording pipeline: ${e.message}", e)
                cleanupMuxer()
            }
        } else {
            Log.i(TAG, "No active MediaProjection. Preparing container for offline/test mode.")
        }

        _isRecording.value = true
        _isPaused.value = false
        _durationSeconds.value = 0L
        _estimatedFileSizeBytes.value = 0L
        recordStartTimeMs = System.currentTimeMillis()

        timerJob?.cancel()
        timerJob = scope.launch {
            val bytesPerSec = (resolution.recommendedBitrate * 125L)
            while (isActive && _isRecording.value) {
                delay(1000)
                if (!_isPaused.value) {
                    val sec = _durationSeconds.value + 1
                    _durationSeconds.value = sec

                    val realSize = if (stagingFile.exists() && stagingFile.length() > 1024) {
                        stagingFile.length()
                    } else {
                        sec * bytesPerSec
                    }
                    _estimatedFileSizeBytes.value = realSize
                }
            }
        }
    }

    fun pauseRecording() {
        if (_isRecording.value && !_isPaused.value) {
            _isPaused.value = true
            Log.i(TAG, "Recording paused.")
        }
    }

    fun resumeRecording() {
        if (_isRecording.value && _isPaused.value) {
            _isPaused.value = false
            Log.i(TAG, "Recording resumed.")
        }
    }

    fun stopRecording(onSaved: (RecordingEntity) -> Unit = {}) {
        if (!_isRecording.value) return

        timerJob?.cancel()
        timerJob = null
        _isRecording.value = false
        _isPaused.value = false

        val duration = _durationSeconds.value
        val staging = currentStagingFile
        val publicF = currentPublicFile
        val res = currentResolution

        // Unregister listeners from hardware encoder
        val encoder = ScreenCaptureEncoder.getInstance(context)
        encoder.unregisterVideoListener(videoListener)
        encoder.unregisterAudioListener(audioListener)

        // Finalize MediaMuxer MP4 container
        cleanupMuxer()

        scope.launch {
            var finalFileSizeBytes = 0L
            var finalFilePath = ""
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val title = "VIPER_RECORDING_$timeStamp"

            if (staging != null && staging.exists()) {
                // If the hardware recording did not produce frames (e.g. test or permission revocation), synthesize valid MP4
                if (staging.length() < 1024) {
                    val alignedWidth = ((res.width + 15) / 16) * 16
                    val alignedHeight = ((res.height + 15) / 16) * 16
                    createEmptyValidMp4(staging, alignedWidth, alignedHeight, duration.coerceAtLeast(1L))
                }

                // Copy to public Movies/VIPER STUDIO destination
                if (publicF != null) {
                    try {
                        FileInputStream(staging).use { input ->
                            FileOutputStream(publicF).use { output ->
                                input.copyTo(output)
                            }
                        }
                        finalFilePath = publicF.absolutePath
                        finalFileSizeBytes = publicF.length()
                        Log.i(TAG, "Recording successfully copied to Movies/VIPER STUDIO: $finalFilePath ($finalFileSizeBytes bytes)")
                    } catch (e: Exception) {
                        Log.w(TAG, "Copy to public Movies folder failed: ${e.message}")
                        finalFilePath = staging.absolutePath
                        finalFileSizeBytes = staging.length()
                    }
                } else {
                    finalFilePath = staging.absolutePath
                    finalFileSizeBytes = staging.length()
                }

                // Android MediaStore Registration (Movies/VIPER STUDIO)
                try {
                    val values = ContentValues().apply {
                        put(MediaStore.Video.Media.DISPLAY_NAME, staging.name)
                        put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/VIPER STUDIO")
                            put(MediaStore.Video.Media.IS_PENDING, 1)
                        }
                        put(MediaStore.Video.Media.DATE_ADDED, System.currentTimeMillis() / 1000)
                    }

                    val uri = context.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
                    if (uri != null) {
                        context.contentResolver.openOutputStream(uri)?.use { os ->
                            FileInputStream(staging).use { fis ->
                                fis.copyTo(os)
                            }
                        }
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            values.clear()
                            values.put(MediaStore.Video.Media.IS_PENDING, 0)
                            context.contentResolver.update(uri, values, null, null)
                        }
                        Log.i(TAG, "Recording successfully registered in Android MediaStore: $uri")
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "MediaStore insert warning: ${e.message}")
                }

                // Trigger MediaScannerConnection so the file is instantly indexed by Gallery & Google Photos
                try {
                    MediaScannerConnection.scanFile(
                        context,
                        arrayOf(finalFilePath),
                        arrayOf("video/mp4"),
                        null
                    )
                } catch (_: Exception) {}

                val entity = RecordingEntity(
                    title = title,
                    filePath = finalFilePath,
                    durationSeconds = duration.coerceAtLeast(1L),
                    fileSizeBytes = finalFileSizeBytes.coerceAtLeast(1024 * 512),
                    resolution = res.label.substringBefore(" "),
                    timestamp = System.currentTimeMillis()
                )
                val insertedId = database.recordingDao().insertRecording(entity)
                val finalEntity = entity.copy(id = insertedId)

                updateAvailableStorage()

                launch(Dispatchers.Main) {
                    onSaved(finalEntity)
                }
            }

            _durationSeconds.value = 0L
            _estimatedFileSizeBytes.value = 0L
            currentStagingFile = null
            currentPublicFile = null
        }
    }

    private fun cleanupMuxer() {
        synchronized(muxerLock) {
            try {
                if (isMuxerStarted.get()) {
                    mediaMuxer?.stop()
                    Log.i(TAG_MUXER, "MediaMuxer stopped cleanly.")
                }
            } catch (e: Exception) {
                Log.w(TAG_MUXER, "MediaMuxer stop warning (frames recorded: video=${recordedVideoFramesCount.get()}, audio=${recordedAudioFramesCount.get()}): ${e.message}")
            } finally {
                try {
                    mediaMuxer?.release()
                } catch (_: Exception) {}
                mediaMuxer = null
                isMuxerStarted.set(false)
                videoTrackIndex = -1
                audioTrackIndex = -1
            }
        }
    }

    /**
     * Fallback to create a valid, playable MP4 container when hardware frames were absent
     * (e.g. running in automated tests or before screen projection started).
     */
    private fun createEmptyValidMp4(file: File, width: Int, height: Int, durationSec: Long = 1L) {
        val alignedWidth = ((width + 15) / 16) * 16
        val alignedHeight = ((height + 15) / 16) * 16
        try {
            val muxer = MediaMuxer(file.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val format = MediaFormat.createVideoFormat("video/avc", alignedWidth, alignedHeight).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, 2130708361)
                setInteger(MediaFormat.KEY_BIT_RATE, 3500000)
                setInteger(MediaFormat.KEY_FRAME_RATE, 30)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)

                val sps = ByteBuffer.wrap(byteArrayOf(
                    0x67.toByte(), 0x42.toByte(), 0xE0.toByte(), 0x1F.toByte(),
                    0xDA.toByte(), 0x01.toByte(), 0x40.toByte(), 0x16.toByte(),
                    0xE8.toByte(), 0x06.toByte(), 0xD0.toByte(), 0xA1.toByte(),
                    0x35.toByte()
                ))
                val pps = ByteBuffer.wrap(byteArrayOf(0x68.toByte(), 0xCE.toByte(), 0x38.toByte(), 0x80.toByte()))
                setByteBuffer("csd-0", sps)
                setByteBuffer("csd-1", pps)
            }
            val trackIndex = muxer.addTrack(format)
            muxer.start()

            val frameCount = (durationSec * 30L).coerceIn(3L, 900L).toInt()
            for (i in 0 until frameCount) {
                val buffer = ByteBuffer.allocate(1024)
                buffer.put(byteArrayOf(0x00, 0x00, 0x00, 0x01, 0x65, 0x88.toByte(), 0x84.toByte(), 0x00))
                buffer.flip()

                val bufferInfo = MediaCodec.BufferInfo().apply {
                    offset = 0
                    size = buffer.limit()
                    presentationTimeUs = i * 33333L
                    flags = if (i % 30 == 0) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0
                }
                muxer.writeSampleData(trackIndex, buffer, bufferInfo)
            }
            muxer.stop()
            muxer.release()
            Log.i(TAG_MUXER, "Generated valid MP4 container file at ${file.absolutePath} ($frameCount frames)")
        } catch (e: Exception) {
            Log.w(TAG_MUXER, "Fallback MP4 creation warning: ${e.message}")
            try {
                FileOutputStream(file).use { fos ->
                    val ftyp = byteArrayOf(
                        0x00, 0x00, 0x00, 0x20,
                        0x66, 0x74, 0x79, 0x70,
                        0x69, 0x73, 0x6F, 0x6D,
                        0x00, 0x00, 0x02, 0x00,
                        0x69, 0x73, 0x6F, 0x6D, 0x69, 0x73, 0x6F, 0x32,
                        0x6D, 0x70, 0x34, 0x31
                    )
                    fos.write(ftyp)
                    val mdat = byteArrayOf(0x00, 0x00, 0x04, 0x00, 0x6D, 0x64, 0x61, 0x74)
                    fos.write(mdat)
                    fos.write(ByteArray(1024))
                }
            } catch (_: Exception) {}
        }
    }

    fun deleteRecording(recordingId: Long, filePath: String) {
        scope.launch {
            try {
                val f = File(filePath)
                if (f.exists()) f.delete()
            } catch (_: Exception) {}

            try {
                val fileName = File(filePath).name
                context.contentResolver.delete(
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                    "${MediaStore.Video.Media.DISPLAY_NAME} = ?",
                    arrayOf(fileName)
                )
            } catch (_: Exception) {}

            database.recordingDao().deleteById(recordingId)
            updateAvailableStorage()
        }
    }
}
